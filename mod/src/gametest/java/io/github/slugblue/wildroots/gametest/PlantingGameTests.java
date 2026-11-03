package io.github.slugblue.wildroots.gametest;

import io.github.slugblue.wildroots.WildRoots;
import io.github.slugblue.wildroots.core.rng.RngKeys;
import io.github.slugblue.wildroots.core.safety.WorldSecret;
import io.github.slugblue.wildroots.data.Definitions;
import io.github.slugblue.wildroots.genetics.api.Classification;
import io.github.slugblue.wildroots.genetics.api.CultivarId;
import io.github.slugblue.wildroots.genetics.api.GeneticsService;
import io.github.slugblue.wildroots.genetics.api.LotRecord;
import io.github.slugblue.wildroots.genetics.model.Genome;
import io.github.slugblue.wildroots.genetics.phenotype.NoiseVector;
import io.github.slugblue.wildroots.item.LotNbt;
import io.github.slugblue.wildroots.item.LotStacks;
import io.github.slugblue.wildroots.plant.PlantBlockEntity;
import java.util.Arrays;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/** WR-133: planting a lot (GDD §3.8, §5.3, §6.2; docs/05-architecture.md §2, §4.3, §21 item 1; SEC-26). */
@GameTestHolder(WildRoots.MOD_ID)
@PrefixGameTestTemplate(false)
public final class PlantingGameTests {

    private PlantingGameTests() {
    }

    /** A realised wild carrot lot of {@code count} seeds. */
    static ItemStack lot(ServerLevel level, int count) {
        ItemStack s = LotStacks.lazy(level, PlantFixtures.SP, "meadow", count, LotNbt.RareSource.NONE);
        LotStacks.realise(s, level);
        return s;
    }

    /** {@code useOn} of {@code stack} in the main hand on the top of the farmland under {@code plantRel}. */
    static InteractionResult use(GameTestHelper helper, Player player, ItemStack stack, BlockPos plantRel) {
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        BlockPos farm = helper.absolutePos(plantRel.below());
        return stack.useOn(new UseOnContext(player, InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(farm).add(0, 0.5, 0), Direction.UP, farm, false)));
    }

    @GameTest(template = SmokeGameTests.FLOOR_3X3X3)
    public static void useOnPlantsTheKeyedOffspringAndShrinksTheStack(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Player player = helper.makeMockSurvivalPlayer();
        PlantFixtures.farmland(helper, PlantFixtures.PLANT);
        ItemStack stack = lot(level, 5);
        LotNbt.Lot l = SeedLotGameTests.lot(stack);
        InteractionResult r = use(helper, player, stack, PlantFixtures.PLANT);
        helper.assertTrue(r.consumesAction(), "useOn " + r);
        helper.assertTrue(stack.getCount() == 4, "count " + stack.getCount());
        PlantBlockEntity be = (PlantBlockEntity) helper.getBlockEntity(PlantFixtures.PLANT);
        PlantBlockEntity.Live live = be.live(level).orElseThrow();

        GeneticsService gs = Definitions.current().genetics();
        LotRecord rec = LotStacks.record(l, Definitions.current()).orElseThrow();
        long secret = WorldSecret.get(level.getServer());
        long packed = helper.absolutePos(PlantFixtures.PLANT).asLong();
        Genome expected = gs.offspring(live.def(), rec, RngKeys.offspring(secret, l.lotId(), 5, packed)).orElseThrow();
        NoiseVector noise = gs.rollNoise(live.def(), RngKeys.noise(secret, l.lotId(), 5, packed)).orElseThrow();
        helper.assertTrue(live.genome().equals(expected), "genome is not offspring(lot, Hash64(secret, lotId, 5, pos))");
        helper.assertTrue(live.noise().equals(noise), "noise is not keyed with \"noise\"");
        // classification at planting, planter recorded
        Classification c = gs.classify(expected, live.def(), Definitions.current().profiles(PlantFixtures.SP),
                Optional.empty()).orElseThrow();
        helper.assertTrue(c.tier() == be.data().tier()
                && java.util.Objects.equals(c.cultivar().map(CultivarId::value).orElse(null), be.data().cultivar()),
                "status " + be.data().cultivar() + " vs " + c);
        helper.assertTrue(player.getUUID().equals(be.data().planter()), "planter " + be.data().planter());
        helper.assertTrue(be.data().generation() == l.generation(), "generation");
        // inert lots and stone are refused
        ItemStack junk = stack.copy();
        junk.getTag().getCompound(LotNbt.KEY).putInt("v", 99);
        PlantFixtures.farmland(helper, new BlockPos(0, 2, 0));
        helper.assertTrue(!use(helper, player, junk, new BlockPos(0, 2, 0)).consumesAction(), "inert lot planted");
        helper.assertTrue(helper.getBlockState(new BlockPos(0, 2, 0)).isAir(), "inert lot left a plant");
        helper.setBlock(new BlockPos(2, 1, 2), Blocks.STONE);
        helper.assertTrue(!use(helper, player, stack, new BlockPos(2, 2, 2)).consumesAction(), "planted on stone");
        helper.succeed();
    }

    /**
     * Halves of a split stack planted in different cells differ; planting
     * the same lot with the same remaining count in the same cell again,
     * after the young plant was removed, rolls nothing new (SEC-26).
     */
    @GameTest(template = SmokeGameTests.FLOOR_3X3X3)
    public static void splitHalvesDifferButTheSameCellRollsNothingNew(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Player player = helper.makeMockSurvivalPlayer();
        BlockPos a = new BlockPos(0, 2, 1);
        BlockPos b = new BlockPos(2, 2, 1);
        PlantFixtures.farmland(helper, a);
        PlantFixtures.farmland(helper, b);
        ItemStack first = lot(level, 4);
        ItemStack second = first.split(2);
        ItemStack third = second.copy();
        helper.assertTrue(use(helper, player, first, a).consumesAction() && use(helper, player, second, b).consumesAction(),
                "planting failed");
        Genome ga = ((PlantBlockEntity) helper.getBlockEntity(a)).live(level).orElseThrow().genome();
        Genome gb = ((PlantBlockEntity) helper.getBlockEntity(b)).live(level).orElseThrow().genome();
        helper.assertTrue(!Arrays.equals(ga.bits(), gb.bits()), "two cells gave the same seedling");

        // break the stage-0 plant in cell a and plant again with the same remaining count (2)
        helper.setBlock(a, Blocks.AIR);
        helper.assertTrue(use(helper, player, third, a).consumesAction(), "replanting failed");
        Genome again = ((PlantBlockEntity) helper.getBlockEntity(a)).live(level).orElseThrow().genome();
        helper.assertTrue(again.equals(ga), "replanting the same cell with the same remaining count rerolled");
        helper.succeed();
    }
}
