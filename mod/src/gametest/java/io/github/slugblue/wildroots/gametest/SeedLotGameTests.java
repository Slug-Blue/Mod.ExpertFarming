package io.github.slugblue.wildroots.gametest;

import io.github.slugblue.wildroots.WildRoots;
import io.github.slugblue.wildroots.core.Ids;
import io.github.slugblue.wildroots.core.line.LineContent;
import io.github.slugblue.wildroots.core.line.LineManifest;
import io.github.slugblue.wildroots.core.line.LineRegistry;
import io.github.slugblue.wildroots.core.registry.ModItems;
import io.github.slugblue.wildroots.core.safety.NbtLimits;
import io.github.slugblue.wildroots.data.Definitions;
import io.github.slugblue.wildroots.data.DefinitionsSnapshot;
import io.github.slugblue.wildroots.genetics.api.Classification;
import io.github.slugblue.wildroots.genetics.api.LotRecord;
import io.github.slugblue.wildroots.genetics.api.QuantTrait;
import io.github.slugblue.wildroots.genetics.api.SpeciesDef;
import io.github.slugblue.wildroots.genetics.codec.GenomeRecord;
import io.github.slugblue.wildroots.genetics.codec.LayoutDescriptor;
import io.github.slugblue.wildroots.genetics.model.GenomeLayout;
import io.github.slugblue.wildroots.genetics.model.TraitId;
import io.github.slugblue.wildroots.item.LotFathersNbt;
import io.github.slugblue.wildroots.item.LotFlags;
import io.github.slugblue.wildroots.item.LotNbt;
import io.github.slugblue.wildroots.item.LotStacks;
import io.github.slugblue.wildroots.plant.Passport;
import io.github.slugblue.wildroots.plant.PlantBlockEntity;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.ForgeRegistries;

/** WR-135: seed lots, lazy lots, {@code LotStacks} (GDD §5.3; docs/05-architecture.md §6.1, §6.3; SEC-13, SEC-35). */
@GameTestHolder(WildRoots.MOD_ID)
@PrefixGameTestTemplate(false)
public final class SeedLotGameTests {

    private SeedLotGameTests() {
    }

    static LotNbt.Lot lot(ItemStack s) {
        LotNbt.Result r = LotStacks.read(s);
        if (r instanceof LotNbt.Lot l) {
            return l;
        }
        throw new IllegalStateException("not a realised lot: " + r);
    }

    /** SEC-13: the lotId exists from creation; halves of a stack split before realisation realise the same. */
    @GameTest(template = SmokeGameTests.FLOOR_3X3X3)
    public static void lazyLotHasItsIdAtCreationAndHalvesRealiseAlike(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ItemStack lazy = LotStacks.lazy(level, PlantFixtures.SP, "meadow", 8, LotNbt.RareSource.NONE);
        LotNbt.Lazy tpl = (LotNbt.Lazy) LotStacks.read(lazy);
        helper.assertTrue(tpl.lotId() != 0, "no lotId at creation");
        ItemStack half = lazy.split(4);
        helper.assertTrue(((LotNbt.Lazy) LotStacks.read(half)).lotId() == tpl.lotId(), "split changed the lotId");
        helper.assertTrue(LotStacks.realise(lazy, level) && LotStacks.realise(half, level), "not realised");
        LotNbt.Lot a = lot(lazy);
        LotNbt.Lot b = lot(half);
        helper.assertTrue(a.lotId() == tpl.lotId() && b.lotId() == tpl.lotId(), "realisation changed the lotId");
        helper.assertTrue(a.mother().equals(b.mother()), "halves realised different mothers");
        helper.assertTrue(a.fathers().size() == 2 && b.fathers().size() == 2, "fathers " + a.fathers().size());
        for (int i = 0; i < 2; i++) {
            helper.assertTrue(a.fathers().get(i).genome().equals(b.fathers().get(i).genome()), "fathers differ");
        }
        helper.assertTrue((a.flags() & LotFlags.FROM_WILD) != 0, "realised wild lot is not from the wild");
        helper.assertTrue(lazy.getCount() == 4 && half.getCount() == 4, "counts");
        helper.assertTrue(ItemStack.isSameItemSameTags(lazy, half), "halves no longer stack");
        helper.succeed();
    }

    /** SEC-35: 36 lazy lots in an inventory realise at most 8 per tick, so it takes at least 5 ticks. */
    @GameTest(template = SmokeGameTests.FLOOR_3X3X3, timeoutTicks = 100)
    public static void inventoryTickRealisesAtMostEightPerTick(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Player player = helper.makeMockSurvivalPlayer();
        Inventory inv = player.getInventory();
        for (int i = 0; i < 36; i++) {
            inv.items.set(i, LotStacks.lazy(level, PlantFixtures.SP, "meadow", 1, LotNbt.RareSource.NONE));
        }
        int[] done = {0};
        int[] ticks = {0};
        helper.onEachTick(() -> {
            if (done[0] >= 36) {
                return;
            }
            inv.tick();
            ticks[0]++;
            int now = 0;
            for (int i = 0; i < 36; i++) {
                if (LotStacks.read(inv.items.get(i)) instanceof LotNbt.Lot) {
                    now++;
                }
            }
            helper.assertTrue(now - done[0] <= 8, "realised " + (now - done[0]) + " in one tick");
            done[0] = now;
            if (now == 36) {
                helper.assertTrue(ticks[0] >= 5, "36 lots realised in " + ticks[0] + " ticks");
                helper.succeed();
            }
        });
    }

    /** Line name, generation (+1) and "from the wild" follow the mother; the lot fits 512 B with 4 fathers. */
    @GameTest(template = SmokeGameTests.FLOOR_3X3X3)
    public static void lotInheritsByTheMotherAndFits512Bytes(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        PlantBlockEntity be = PlantFixtures.place(helper, PlantFixtures.PLANT, 5);
        be.init(Definitions.current().genetics(), new PlantBlockEntity.Init(PlantFixtures.SP, PlantFixtures.genome(31),
                PlantFixtures.noise(32), Passport.PLAYER, new UUID(3, 4), Classification.WILD, "Golden Line", 3,
                LotFlags.FROM_WILD, 0));
        PlantBlockEntity.Live live = be.live(level).orElseThrow();
        List<LotFathersNbt.Father> fathers = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            fathers.add(new LotFathersNbt.Father(Definitions.current().genetics().encode(PlantFixtures.genome(40 + i))
                    .toFather(), 1 + i, false));
        }
        ItemStack s = LotStacks.seeds(level, live, fathers, 0.6f, LotFlags.BEES, be, 9);
        LotNbt.Lot l = lot(s);
        helper.assertTrue(l.lineName().equals(Optional.of("Golden Line")), "line name " + l.lineName());
        helper.assertTrue(l.generation() == 4, "generation " + l.generation());
        helper.assertTrue((l.flags() & LotFlags.FROM_WILD) != 0, "from the wild lost");
        helper.assertTrue(l.rare() == LotNbt.RareSource.NONE, "rare flag without a mutation");
        helper.assertTrue(s.getCount() == 9 && l.fathers().size() == 4, "count or fathers");
        var budget = NbtLimits.lot(s.getTag().get(LotNbt.KEY));
        helper.assertTrue(budget.ok() && budget.bytes() <= NbtLimits.LOT_TARGET_BYTES, "lot size " + budget);
        helper.assertTrue(LotNbt.disp(s.getTag().get(LotNbt.KEY)).size() <= 6, "disp lines");
        helper.succeed();
    }

    /**
     * WR-124 transfer: with {@code disabledLines = ["carrot"]} the carrot items
     * and blocks stay registered, are hidden from the creative tab, and carrot
     * lots and plants are inert because the species is not in the snapshot.
     */
    @GameTest(template = SmokeGameTests.FLOOR_3X3X3, batch = "wr135_lines", timeoutTicks = 200)
    public static void disabledCarrotLineIsRegisteredInertAndHidden(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        LineRegistry off = LineRegistry.build(LineManifest.fromClasspath(), List.of("carrot"), w -> { });
        helper.assertTrue(!off.isEnabled("carrot"), "carrot still enabled");
        helper.assertTrue(ForgeRegistries.BLOCKS.containsKey(Ids.mod("wild_carrot")), "block not registered");
        helper.assertTrue(ForgeRegistries.ITEMS.containsKey(Ids.mod("seed_lot")), "seed lot not registered");
        for (var ro : ModItems.REGISTER.getEntries()) {
            var line = LineContent.lineOf(ro.get());
            boolean visible = LineContent.visible(line, off);
            helper.assertTrue(visible == line.isEmpty(), ro.getId() + ": line " + line + " visible " + visible);
        }
        ItemStack lazy = LotStacks.lazy(level, PlantFixtures.SP, "meadow", 2, LotNbt.RareSource.NONE);
        PlantBlockEntity be = PlantFixtures.plant(PlantFixtures.place(helper, PlantFixtures.PLANT, 1), 51);
        DefinitionsSnapshot original = Definitions.current();
        try {
            Map<String, DefinitionsSnapshot.SpeciesEntry> species = new LinkedHashMap<>(original.species());
            species.remove(PlantFixtures.SP);
            Map<String, DefinitionsSnapshot.CultivarEntry> cultivars = new LinkedHashMap<>(original.cultivars());
            cultivars.values().removeIf(c -> c.def().species().equals(PlantFixtures.SP));
            Definitions.publish(new DefinitionsSnapshot(species, cultivars, original.stats()));
            helper.assertTrue(!LotStacks.realise(lazy, level), "a lot of a disabled line realised");
            helper.assertTrue(be.live(level).isEmpty(), "a plant of a disabled line is live");
            helper.assertTrue(be.status() == PlantBlockEntity.Status.SLEEPING, "status " + be.status());
        } finally {
            Definitions.publish(original);
        }
        helper.assertTrue(be.live(level).isPresent(), "plant did not wake up");
        helper.succeed();
    }

    /** WR-111 transfer: mother and fathers of a lot migrate when the layout changed (§5.3; S10). */
    @GameTest(template = SmokeGameTests.FLOOR_3X3X3, batch = "wr135_migration", timeoutTicks = 200)
    public static void lotGenomesMigrateToTheCurrentLayout(GameTestHelper helper) {
        String sp = "wildroots:gt_migrating_lot";
        SpeciesDef oldDef = PlantFixtures.carrotAs(sp, q -> q);
        SpeciesDef newDef = PlantFixtures.carrotAs(sp, q -> q.stream()
                .map(t -> t.id().equals(TraitId.of("yield")) ? new QuantTrait(t.id(), 8, t.sigmaEnv(), t.vigorAffected()) : t)
                .toList());
        GenomeLayout oldLayout = GenomeLayout.of(oldDef);
        GenomeLayout newLayout = GenomeLayout.of(newDef);
        GenomeRecord m = Definitions.current().genetics().encode(PlantFixtures.genome(61));
        GenomeRecord mother = new GenomeRecord(m.v(), sp, m.lh(), m.a());
        List<LotFathersNbt.Father> fathers = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            fathers.add(new LotFathersNbt.Father(Definitions.current().genetics().encode(PlantFixtures.genome(70 + i))
                    .toFather(), 1, i == 3));
        }
        CompoundTag root = LotStacks.seedsTag(new LotStacks.Parent(sp, Optional.empty(), mother, Optional.empty(), 0, 0),
                fathers, 0.5f, 0, 5L, LotStacks.RareClaim.NONE);
        ItemStack stack = LotStacks.stack(root, 3);
        ItemStack reloaded = ItemStack.of(stack.save(new CompoundTag()));
        DefinitionsSnapshot original = Definitions.current();
        try {
            Definitions.publish(PlantFixtures.withSpecies(original, newDef,
                    new LayoutDescriptor(sp, oldLayout.hash(), oldLayout.canonical())));
            LotRecord r = LotStacks.record(lot(reloaded), Definitions.current()).orElseThrow();
            helper.assertTrue(r.mother().layout().equals(newLayout), "mother not migrated");
            helper.assertTrue(r.fathers().size() == 4, "fathers lost");
            r.fathers().forEach(f -> helper.assertTrue(f.genome().layout().equals(newLayout), "father not migrated"));
            helper.assertTrue(r.fathers().get(3).donor(), "donor flag lost");
        } finally {
            Definitions.publish(original);
        }
        helper.succeed();
    }
}
