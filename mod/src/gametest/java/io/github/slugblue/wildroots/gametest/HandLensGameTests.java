package io.github.slugblue.wildroots.gametest;

import io.github.slugblue.wildroots.WildRoots;
import io.github.slugblue.wildroots.core.registry.ModItems;
import io.github.slugblue.wildroots.core.safety.DispCodec;
import io.github.slugblue.wildroots.genetics.api.Phenotype;
import io.github.slugblue.wildroots.genetics.api.QuantTrait;
import io.github.slugblue.wildroots.genetics.pollination.Brush;
import io.github.slugblue.wildroots.item.LotFlags;
import io.github.slugblue.wildroots.item.LotNbt;
import io.github.slugblue.wildroots.item.LotStacks;
import io.github.slugblue.wildroots.journal.Journals;
import io.github.slugblue.wildroots.lens.Lens;
import io.github.slugblue.wildroots.lens.LensReading;
import io.github.slugblue.wildroots.lens.LensScale;
import io.github.slugblue.wildroots.plant.GrowthLogic;
import io.github.slugblue.wildroots.plant.PendingLot;
import io.github.slugblue.wildroots.plant.PlantBlockEntity;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * WR-183: the hand lens on a plant (five-step scale of P, stage,
 * pollination, signs of inbreeding, "looks like") and on a lot
 * ({@code inspected}, {@code disp} rewritten for good); the recipe.
 */
@GameTestHolder(WildRoots.MOD_ID)
@PrefixGameTestTemplate(false)
public final class HandLensGameTests {

    private HandLensGameTests() {
    }

    static String argKey(DispCodec.Line l, int i) {
        return DispCodec.argKey(l.args().get(i));
    }

    @GameTest(template = SmokeGameTests.FLOOR_3X3X3, batch = "wr18x_journal")
    public static void lensOnAPlantReadsTheFiveStepScale(GameTestHelper helper) {
        JournalGameTests.Recorder rec = new JournalGameTests.Recorder();
        ServerPlayer p = JournalGameTests.survival(helper, "wr183_plant");
        try {
            ServerLevel level = helper.getLevel();
            PlantBlockEntity be = PlantFixtures.plant(PlantFixtures.place(helper, PlantFixtures.PLANT, 4), 501);
            be.setPendingLot(new PendingLot(List.of(), 0, Brush.NONE, true));
            BlockPos abs = helper.absolutePos(PlantFixtures.PLANT);
            List<DispCodec.Line> lines = Lens.inspectPlant(level, abs, p).orElseThrow();
            PlantBlockEntity.Live live = be.live(level).orElseThrow();
            Phenotype ph = GrowthLogic.phenotype(live, GrowthLogic.environment(level, abs)).orElseThrow();

            DispCodec.Line header = lines.get(0);
            helper.assertTrue(header.key().equals(Lens.HEADER)
                    && "wildroots.species.wild_carrot".equals(argKey(header, 0))
                    && "wildroots.tooltip.stage.flowering".equals(argKey(header, 1)), "header " + header);
            List<DispCodec.Line> traits = lines.stream().filter(l -> l.key().equals(LensReading.TRAIT)).toList();
            List<QuantTrait> quant = live.def().quantitative();
            helper.assertTrue(traits.size() == quant.size(), "trait lines " + traits.size() + " for " + quant.size());
            for (int i = 0; i < quant.size(); i++) {
                String t = quant.get(i).id().value();
                int step = LensScale.step(ph.p(quant.get(i).id()));
                String scale = LensScale.scale(t, live.species().info().displayTraits());
                helper.assertTrue(LensScale.wordKey(scale, step).equals(argKey(traits.get(i), 1)),
                        t + ": P " + ph.p(quant.get(i).id()) + " shown as " + traits.get(i));
            }
            helper.assertTrue("wildroots.tooltip.trait_name.fruit_size.wild_carrot".equals(argKey(traits.get(
                    quant.stream().map(q -> q.id().value()).toList().indexOf("fruit_size")), 0)), "carrot root size name");
            helper.assertTrue(lines.stream().anyMatch(l -> l.key().startsWith("wildroots.tooltip.qual_state.bitterness.")),
                    "no bitterness");
            helper.assertTrue(lines.stream().anyMatch(l -> l.key().equals("wildroots.tooltip.pollination.bees")),
                    "no pollination line");
            boolean ailing = lines.stream().anyMatch(l -> l.key().equals(LensReading.AILING));
            helper.assertTrue(ailing == (ph.vigor() < GrowthLogic.AILING_BELOW), "ailing line " + ailing + " V " + ph.vigor());
            boolean looks = lines.stream().anyMatch(l -> l.key().equals(LensReading.LOOKS_LIKE));
            helper.assertTrue(looks == LensReading.looksLike(live.snapshot(), live.def(), ph, java.util.Optional.empty(), 0)
                    .isPresent(), "looks-like line disagrees with the profiles");
            helper.assertTrue(Journals.of(p).orElseThrow().has("species.wild_carrot"), "lens did not discover the species");
            helper.assertTrue(looks == Journals.of(p).orElseThrow().has("hint.looks_like"), "looks_like hint");
            helper.assertTrue(Lens.inspectPlant(level, abs.above(), p).isEmpty(), "air gave a reading");
        } finally {
            rec.close();
            p.discard();
        }
        helper.succeed();
    }

    @GameTest(template = SmokeGameTests.FLOOR_3X3X3, batch = "wr18x_journal")
    public static void lensOnALotSetsInspectedAndRewritesDisp(GameTestHelper helper) {
        JournalGameTests.Recorder rec = new JournalGameTests.Recorder();
        ServerPlayer p = JournalGameTests.survival(helper, "wr183_lot");
        try {
            ServerLevel level = helper.getLevel();
            ItemStack lot = PlantingGameTests.lot(level, 12);
            LotNbt.Lot before = (LotNbt.Lot) LotStacks.read(lot);
            int dispBefore = LotNbt.disp(LotNbt.root(lot.getTag())).size();
            helper.assertTrue((before.flags() & LotFlags.INSPECTED) == 0, "fresh lot already inspected");
            p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.HAND_LENS.get()));
            p.setItemInHand(InteractionHand.OFF_HAND, lot);
            p.getItemInHand(InteractionHand.MAIN_HAND).use(level, p, InteractionHand.MAIN_HAND);
            ItemStack after = p.getItemInHand(InteractionHand.OFF_HAND);
            LotNbt.Lot a = (LotNbt.Lot) LotStacks.read(after);
            helper.assertTrue((a.flags() & LotFlags.INSPECTED) != 0, "inspected flag not set");
            helper.assertTrue(a.lotId() == before.lotId() && a.mother().equals(before.mother()) && after.getCount() == 12,
                    "the lot changed beyond the flag and disp");
            List<DispCodec.Line> disp = LotNbt.disp(LotNbt.root(after.getTag()));
            helper.assertTrue(disp.size() > dispBefore, "disp not rewritten: " + disp);
            helper.assertTrue(disp.stream().anyMatch(l -> l.key().equals(io.github.slugblue.wildroots.lens.LensRow.KEY)), "no tier-1 row");
            helper.assertTrue(io.github.slugblue.wildroots.core.safety.NbtLimits.lot(LotNbt.root(after.getTag())).ok(),
                    "inspected lot over its limit");
            // the tier stays: save and load the stack, split it, the lines are still there
            ItemStack reloaded = ItemStack.of(after.save(new net.minecraft.nbt.CompoundTag()));
            helper.assertTrue(LotNbt.disp(LotNbt.root(reloaded.getTag())).equals(disp), "disp lost on save");
            ItemStack half = after.split(6);
            helper.assertTrue(LotNbt.disp(LotNbt.root(half.getTag())).equals(disp), "disp lost on split");
            // a lazy lot is realised and inspected too
            ItemStack lazy = LotStacks.lazy(level, PlantFixtures.SP, "meadow", 3, LotNbt.RareSource.NONE);
            helper.assertTrue(Lens.inspectLot(lazy, level, p), "lazy lot not inspected");
            helper.assertTrue(LotStacks.read(lazy) instanceof LotNbt.Lot l && (l.flags() & LotFlags.INSPECTED) != 0,
                    "lazy lot not realised and marked");
        } finally {
            rec.close();
            p.discard();
        }
        helper.succeed();
    }

    /**
     * Review 1, M1: the worst lot (4 fathers, a 50-character CJK line name,
     * pollen flags) keeps every tier-1 line: all 7 traits, bitterness,
     * pollination, inbreeding and "looks like", within 1 KiB, nothing left out.
     */
    @GameTest(template = SmokeGameTests.FLOOR_3X3X3, batch = "wr18x_journal")
    public static void worstCaseLotKeepsTheWholeTierOne(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        var gs = io.github.slugblue.wildroots.data.Definitions.current().genetics();
        List<io.github.slugblue.wildroots.item.LotFathersNbt.Father> fathers = new java.util.ArrayList<>();
        for (int i = 0; i < io.github.slugblue.wildroots.core.safety.NbtLimits.LOT_MAX_FATHERS; i++) {
            fathers.add(new io.github.slugblue.wildroots.item.LotFathersNbt.Father(
                    gs.encode(PlantFixtures.genome(700 + i)).toFather(), 2f, i == 0));
        }
        String name = "漢".repeat(io.github.slugblue.wildroots.core.safety.PlayerText.LINE_NAME_MAX);
        LotStacks.Parent mother = new LotStacks.Parent(PlantFixtures.SP, java.util.Optional.empty(),
                gs.encode(PlantFixtures.genome(699)), java.util.Optional.of(name), 41, LotFlags.FROM_WILD);
        ItemStack stack = LotStacks.stack(LotStacks.seedsTag(mother, fathers, 0.75f, LotFlags.BEES, 123456789L,
                LotStacks.RareClaim.NONE), 9);
        LotNbt.Lot lot = (LotNbt.Lot) LotStacks.read(stack);
        helper.assertTrue(lot.fathers().size() == 4 && lot.lineName().equals(java.util.Optional.of(name)), "fixture");
        String traits = "s5*y5g5f5Wc5h5d5";
        io.github.slugblue.wildroots.lens.LensRow row = new io.github.slugblue.wildroots.lens.LensRow(traits, 'd', 'B',
                true, java.util.Optional.of("eastern_carrot"), false);
        helper.assertTrue(LotStacks.inspect(stack, lot, row.degradations().stream()
                .map(io.github.slugblue.wildroots.lens.LensRow::line).toList()), "not inspected");
        var root = LotNbt.root(stack.getTag());
        var budget = io.github.slugblue.wildroots.core.safety.NbtLimits.lot(root);
        helper.assertTrue(budget.ok(), "over the limit: " + budget);
        List<DispCodec.Line> disp = LotNbt.disp(root);
        var kept = disp.stream().map(io.github.slugblue.wildroots.lens.LensRow::parse).flatMap(java.util.Optional::stream)
                .findFirst().orElseThrow();
        helper.assertTrue(kept.equals(row), "tier 1 shortened: " + kept + " (lot " + budget.bytes() + " B)");
        helper.assertTrue(kept.expand(PlantFixtures.SP).size() == 7 + 4, "expanded lines " + kept.expand(PlantFixtures.SP));
        org.slf4j.LoggerFactory.getLogger(HandLensGameTests.class).info("WR-183 worst inspected lot: {} B", budget.bytes());
        helper.succeed();
    }

    /** Review 1, M1: the real lens path writes one compact row and the tooltip expands every trait. */
    @GameTest(template = SmokeGameTests.FLOOR_3X3X3, batch = "wr18x_journal")
    public static void inspectedLotTooltipShowsEveryTrait(GameTestHelper helper) {
        JournalGameTests.Recorder rec = new JournalGameTests.Recorder();
        ServerPlayer p = JournalGameTests.survival(helper, "wr183_tooltip");
        try {
            ItemStack lot = PlantingGameTests.lot(helper.getLevel(), 2);
            helper.assertTrue(Lens.inspectLot(lot, helper.getLevel(), p), "not inspected");
            List<net.minecraft.network.chat.Component> tip = new java.util.ArrayList<>();
            lot.getItem().appendHoverText(lot, helper.getLevel(), tip, net.minecraft.world.item.TooltipFlag.NORMAL);
            long traitLines = tip.stream().filter(c -> c.getContents()
                    instanceof net.minecraft.network.chat.contents.TranslatableContents t
                    && t.getKey().equals(LensReading.TRAIT)).count();
            int quant = PlantFixtures.carrot().def().quantitative().size();
            helper.assertTrue(traitLines == quant, "tooltip trait lines " + traitLines + " of " + quant + ": " + tip);
            helper.assertTrue(tip.stream().noneMatch(c -> c.getContents()
                    instanceof net.minecraft.network.chat.contents.TranslatableContents t
                    && t.getKey().equals(io.github.slugblue.wildroots.lens.LensRow.MORE_KEY)), "'and more' on a normal lot");
        } finally {
            rec.close();
            p.discard();
        }
        helper.succeed();
    }

    @GameTest(template = SmokeGameTests.FLOOR_3X3X3, batch = "wr18x_journal")
    public static void handLensRecipeIsCopperGlassPaneStick(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Recipe<?> r = level.getRecipeManager().byKey(io.github.slugblue.wildroots.core.Ids.mod("hand_lens")).orElseThrow();
        helper.assertTrue(r.getResultItem(level.registryAccess()).is(ModItems.HAND_LENS.get()), "result");
        List<String> items = r.getIngredients().stream().filter(i -> !i.isEmpty())
                .map(i -> net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(i.getItems()[0].getItem()).toString())
                .sorted().toList();
        helper.assertTrue(items.equals(List.of("minecraft:copper_ingot", "minecraft:glass_pane", "minecraft:stick")),
                "ingredients " + items);
        helper.succeed();
    }
}
