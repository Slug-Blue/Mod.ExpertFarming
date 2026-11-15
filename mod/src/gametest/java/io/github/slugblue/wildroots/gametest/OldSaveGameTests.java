package io.github.slugblue.wildroots.gametest;

import io.github.slugblue.wildroots.WildRoots;
import io.github.slugblue.wildroots.core.Ids;
import io.github.slugblue.wildroots.core.registry.ModItems;
import io.github.slugblue.wildroots.data.Definitions;
import io.github.slugblue.wildroots.genetics.codec.DecodeResult;
import io.github.slugblue.wildroots.genetics.model.GenomeLayout;
import io.github.slugblue.wildroots.item.LotNbt;
import io.github.slugblue.wildroots.item.LotStacks;
import io.github.slugblue.wildroots.item.MotherItem;
import io.github.slugblue.wildroots.item.MotherNbt;
import io.github.slugblue.wildroots.item.SeedPouchItem;
import io.github.slugblue.wildroots.plant.FieldPlantBlock;
import io.github.slugblue.wildroots.plant.GrowthLogic;
import io.github.slugblue.wildroots.plant.PlantBlockEntity;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * WR-196, docs/05-architecture.md §5.4 item 3: the frozen template
 * {@code old_save_v1.nbt} (block entities and items exactly as the v1 format
 * wrote them, built from {@code fixtures/v1/*.snbt} by the test-source
 * generator {@code OldSaveTemplate}; {@code OldSaveV1Test} checks that the
 * committed file is exactly what it builds) is placed; its block
 * entities load through {@code BlockEntity#load}, genomes decode, plants
 * grow, a lot plants, the pouch gives its entries last in first out and the
 * mother root decodes. The template is never regenerated or deleted; its
 * sha256 is pinned in {@code OldSaveV1Test}.
 *
 * <p>Placement: a crop placed by a structure pops on the shape updates that
 * follow the placement while the light engine has not lit it yet (vanilla
 * {@code CropBlock#canSurvive}; GameTests run underground). So the test runs
 * on the lit field and places the frozen template itself, through the same
 * {@code StructureTemplate#placeInWorld} and {@code BlockEntity#load}, with
 * {@code UPDATE_KNOWN_SHAPE} (no shape updates), at {@link #ORIGIN}.
 *
 * <p>Template ({@code 7×4×5}, lit by light blocks): stone floor at y 0,
 * farmland at y 1, plants at y 2 on row z 1 — x 1 young player plant
 * (stage 0), x 2 flowering mother with a pending lot (stage 4), x 3 lazy wild
 * plant with a passport only (stage 2), x 4 sleeping plant of a species that
 * no longer exists (stage 1); empty farmland at (1, 1, 3); a chest at
 * (4, 1, 3) with a lot of 5 seeds, a pouch of 6 entries and a mother root.
 */
@GameTestHolder(WildRoots.MOD_ID)
@PrefixGameTestTemplate(false)
public final class OldSaveGameTests {

    static final ResourceLocation TEMPLATE = Ids.mod("gametest/old_save_v1");
    /** Where the template's (0, 0, 0) goes in the lit field (its floor replaces the field's at y 0). */
    static final BlockPos ORIGIN = new BlockPos(5, 0, 5);
    static final BlockPos YOUNG = ORIGIN.offset(1, 2, 1);
    static final BlockPos FLOWERING = ORIGIN.offset(2, 2, 1);
    static final BlockPos WILD = ORIGIN.offset(3, 2, 1);
    static final BlockPos SLEEPING = ORIGIN.offset(4, 2, 1);
    static final BlockPos EMPTY_CELL = ORIGIN.offset(1, 2, 3);
    static final BlockPos CHEST = ORIGIN.offset(4, 1, 3);

    private OldSaveGameTests() {
    }

    static PlantBlockEntity plant(GameTestHelper helper, BlockPos rel) {
        if (!(helper.getBlockEntity(rel) instanceof PlantBlockEntity p)) {
            throw new IllegalStateException("no plant block entity at " + rel + ": " + helper.getBlockEntity(rel));
        }
        return p;
    }

    static GenomeLayout carrotLayout() {
        return Definitions.current().genetics().layout(PlantFixtures.carrot().def()).orElseThrow();
    }

    /** Block variant by the released M1a rule from the raw v1 alleles (domestic allele = 1). */
    static int v1Variant(GenomeLayout old, long[] bits) {
        java.util.function.BiPredicate<String, Boolean> dom = (trait, recessive) -> {
            var s = old.section(io.github.slugblue.wildroots.genetics.model.TraitId.of(trait));
            int sum = GenomeLayout.get(bits, s, 0, 0) + GenomeLayout.get(bits, s, 0, 1);
            return recessive ? sum == 2 : sum >= 1;
        };
        // color_p is dominant domestic (P_), color_y1 and color_y2 are dominant wild (domestic phenotype = homozygous)
        if (dom.test("color_p", false)) {
            return 3;
        }
        if (dom.test("color_y1", true)) {
            return dom.test("color_y2", true) ? 2 : 1;
        }
        return 0;
    }

    /** Random ticks with a fixed RNG until the stage passes {@code from}; whether it did. */
    static boolean grows(GameTestHelper helper, BlockPos rel, int from, long seed) {
        int target = from + 1;
        return PlantFixtures.stage(helper, rel) >= target
                || MotherRootGameTests.growTo(helper, rel, target, null, seed);
    }

    /** Places the frozen template at {@link #ORIGIN} without shape updates. */
    static void placeTemplate(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        StructureTemplate t = level.getStructureManager().get(TEMPLATE)
                .orElseThrow(() -> new AssertionError("old_save_v1.nbt is missing"));
        helper.assertTrue(t.getSize().equals(new Vec3i(7, 4, 5)), "template size " + t.getSize());
        BlockPos at = helper.absolutePos(ORIGIN);
        helper.assertTrue(t.placeInWorld(level, at, at, new StructurePlaceSettings(), RandomSource.create(196),
                Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE), "template not placed");
    }

    @GameTest(template = SeedSetGameTests.FIELD, setupTicks = SeedSetGameTests.SETUP,
            timeoutTicks = PlantFixtures.LIT_WAIT + 200)
    public static void old_save_v1(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        placeTemplate(helper);
        // first look, before any world random tick can move a stage
        int[] stages = {PlantFixtures.stage(helper, YOUNG), PlantFixtures.stage(helper, FLOWERING),
                PlantFixtures.stage(helper, WILD), PlantFixtures.stage(helper, SLEEPING)};
        helper.assertTrue(Arrays.equals(stages, new int[] {0, 4, 2, 1}), "stages " + Arrays.toString(stages));

        PlantBlockEntity young = plant(helper, YOUNG);
        PlantBlockEntity flowering = plant(helper, FLOWERING);
        PlantBlockEntity wild = plant(helper, WILD);
        PlantBlockEntity sleeping = plant(helper, SLEEPING);
        helper.assertTrue(young.status() == PlantBlockEntity.Status.OK, "young: " + young.inertReason());
        helper.assertTrue(wild.status() == PlantBlockEntity.Status.OK && !wild.genomeDecoded(),
                "wild plant is not lazy: " + wild.status() + " decoded " + wild.genomeDecoded());

        // genomes decode (bits as frozen in fixtures/v1/plant_v1.snbt)
        PlantBlockEntity.Live y = young.live(level).orElseThrow(() -> new AssertionError("young plant: " + young.inertReason()));
        // the v1 bits (frozen in fixtures/v1/plant_v1.snbt, 142 bits, layout cee0bc2c52916ae3) are migrated to the layout
        // with the colour locus color_r (WR-701): every allele kept, color_r = RR (legacy_default), so the colour is the old one
        long[] v1 = {4992702343843299669L, -4236302851338009576L, 24L};
        GenomeLayout old = Definitions.current().layouts().lookup(PlantFixtures.SP, -3539622408563365149L)
                .orElseThrow(() -> new AssertionError("the M1a layout is not in the history"));
        GenomeLayout now = carrotLayout();
        helper.assertTrue(y.genome().layout().hash() == now.hash() && now.totalBits() == 144 && old.totalBits() == 142,
                "layouts " + old + " -> " + y.genome().layout());
        for (io.github.slugblue.wildroots.genetics.model.Locus s : old.sections()) {
            io.github.slugblue.wildroots.genetics.model.Locus t = now.section(s.trait());
            for (int l = 0; l < s.loci(); l++) {
                for (int w = 0; w < 2; w++) {
                    helper.assertTrue(GenomeLayout.get(v1, s, l, w) == y.genome().allele(t, l, w),
                            "allele " + s.trait() + " " + l + "/" + w + " changed in the migration");
                }
            }
        }
        io.github.slugblue.wildroots.genetics.model.Locus rr = now.section(
                io.github.slugblue.wildroots.genetics.model.TraitId.of("color_r"));
        helper.assertTrue(y.genome().allele(rr, 0, 0) == 0 && y.genome().allele(rr, 0, 1) == 0, "color_r is not RR");
        // the colour (block variant) of every old plant is what the v1 alleles gave: P_ purple 3, y1y1 y2y2 orange 2, y1y1 yellow 1, else 0
        helper.assertTrue(GrowthLogic.variant(young.live(level).orElseThrow().def(), GrowthLogic.phenotype(y,
                GrowthLogic.environment(level, helper.absolutePos(YOUNG))).orElseThrow()) == v1Variant(old, v1),
                "the variant of the old plant changed");
        PlantBlockEntity.Live f = flowering.live(level).orElseThrow(() -> new AssertionError("flowering: " + flowering.inertReason()));
        helper.assertTrue(f.data().pending() != null && f.data().pending().fathers().size() == 2, "pending lot lost");
        helper.assertTrue(wild.live(level).isPresent() && wild.genomeDecoded(), "the lazy wild plant did not realise");
        // the sleeping plant stays asleep and keeps its data
        helper.assertTrue(sleeping.live(level).isEmpty() && sleeping.status() == PlantBlockEntity.Status.SLEEPING,
                "sleeping plant: " + sleeping.status());
        CompoundTag kept = sleeping.saveWithFullMetadata().getCompound("wr");
        helper.assertTrue("wildroots:old_turnip".equals(kept.getString("sp"))
                && kept.getCompound("g").getLongArray("a").length == 3, "sleeping plant data not kept: " + kept);

        // chest: lot, pouch, mother root
        ChestBlockEntity chest = (ChestBlockEntity) helper.getBlockEntity(CHEST);
        ItemStack lot = chest.getItem(0).copy();
        ItemStack pouch = chest.getItem(1).copy();
        ItemStack mother = chest.getItem(2).copy();
        helper.assertTrue(lot.is(ModItems.SEED_LOT.get()) && lot.getCount() == 5, "slot 0 " + lot);
        helper.assertTrue(pouch.is(ModItems.SEED_POUCH.get()), "slot 1 " + pouch);
        helper.assertTrue(mother.is(ModItems.MOTHER_ROOT.get()), "slot 2 " + mother);
        LotNbt.Lot l = (LotNbt.Lot) LotStacks.read(lot);
        helper.assertTrue(l.lotId() == 0x1234_5678_9ABCL, "lot id " + l.lotId());
        helper.assertTrue(LotStacks.record(l, Definitions.current()).isPresent(), "lot genomes do not decode");
        MotherNbt.Mother m = MotherItem.read(mother).orElseThrow(() -> new AssertionError("mother root unreadable"));
        // read as the mod reads it: the v1 genome migrates to the layout with color_r (WR-701)
        DecodeResult md = Definitions.current().genetics().decode(m.genome(), PlantFixtures.carrot().def());
        helper.assertTrue(md instanceof DecodeResult.Valid v && v.migrated(), "mother root genome: " + md);

        // the pouch gives its entries last in first out, through a real menu click
        Player player = helper.makeMockSurvivalPlayer();
        SimpleContainer c = new SimpleContainer(27);
        ChestMenu menu = SeedPouchGameTests.menu(player, c);
        c.setItem(0, pouch);
        List<Long> out = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            menu.setCarried(ItemStack.EMPTY);
            menu.clicked(0, 1, ClickType.PICKUP, player);
            helper.assertTrue(menu.getCarried().is(ModItems.SEED_LOT.get()), "take " + i + ": " + menu.getCarried());
            out.add(SeedPouchGameTests.lotId(menu.getCarried()));
        }
        helper.assertTrue(out.equals(List.of(1003L, 1003L, 1003L, 1002L, 1002L, 1001L)), "pouch order " + out);
        helper.assertTrue(SeedPouchItem.contents(c.getItem(0)).orElseThrow().used() == 0, "pouch not empty");

        PlantFixtures.whenLit(helper, List.of(YOUNG, FLOWERING, WILD, EMPTY_CELL), () -> {
            // the lot plants
            helper.assertTrue(PlantingGameTests.use(helper, player, lot, EMPTY_CELL).consumesAction(), "lot not planted");
            helper.assertTrue(lot.getCount() == 4, "count " + lot.getCount());
            helper.assertTrue(plant(helper, EMPTY_CELL).live(level).isPresent(), "planted lot is not live");
            // plants grow (random ticks with fixed RNGs)
            helper.assertTrue(grows(helper, YOUNG, 0, 19601), "young plant does not grow");
            helper.assertTrue(grows(helper, WILD, 2, 19602), "wild plant does not grow");
            helper.assertTrue(grows(helper, FLOWERING, FieldPlantBlock.FLOWER_STAGE, 19603), "flowering plant does not set seed");
            helper.succeed();
        }, 0);
    }
}
