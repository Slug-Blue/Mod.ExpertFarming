package io.github.slugblue.wildroots.gametest;

import io.github.slugblue.wildroots.WildRoots;
import io.github.slugblue.wildroots.core.registry.ModItems;
import io.github.slugblue.wildroots.data.Definitions;
import io.github.slugblue.wildroots.genetics.api.Classification;
import io.github.slugblue.wildroots.genetics.pollination.Brush;
import io.github.slugblue.wildroots.plant.FieldPlantBlock;
import io.github.slugblue.wildroots.plant.Passport;
import io.github.slugblue.wildroots.plant.PendingLot;
import io.github.slugblue.wildroots.plant.PlantBlockEntity;
import io.github.slugblue.wildroots.pollination.PollinationTiming;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.ServerLevelData;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/** Regressions for gamerule-scaled flowering and activated wild flowers keeping their natural soil. */
@GameTestHolder(WildRoots.MOD_ID)
@PrefixGameTestTemplate(false)
public final class PollinationProgressGameTests {
    private static final BlockPos A = new BlockPos(8, 2, 8), B = A.east();
    private PollinationProgressGameTests() { }

    @GameTest(template = SeedSetGameTests.FIELD, batch = "pollination_work_scheduler", timeoutTicks = 400)
    public static void floweringGameTickerStopsAtZeroAndScalesWithRandomTickSpeed(GameTestHelper helper) {
        PlantFixtures.litFarmland(helper, A);
        PlantFixtures.whenLit(helper, List.of(A), () -> {
            var level = helper.getLevel();
            var rule = level.getGameRules().getRule(GameRules.RULE_RANDOMTICKING);
            int previous = rule.get();
            long beforeTime = level.getGameTime();
            try {
                rule.set(0, level.getServer());
                var be = SeedSetGameTests.flowering(helper, A, 920);
                BlockPos abs = helper.absolutePos(A);
                for (int i = 0; i < 20; i++) PlantFixtures.pollinationGameTick(level, abs);
                helper.assertTrue(be.data().pollinationWork() == 0, "speed 0 advanced flowering");
                ((ServerLevelData) level.getLevelData()).setGameTime(beforeTime + 1000000);
                for (int i = 0; i < 10; i++) {
                    helper.getBlockState(A).randomTick(level, abs, RandomSource.create(i));
                    ((FieldPlantBlock) helper.getBlockState(A).getBlock()).growCrops(level, abs, helper.getBlockState(A));
                    helper.assertTrue(!PollinationTiming.ready(level, abs, be, be.live(level).orElseThrow()),
                            "time or growCrops bypassed random-tick work");
                }
                helper.assertTrue(FieldPlantBlock.stage(helper.getBlockState(A)) == 4 && be.data().pollinationWork() == 0,
                        "flowering finished with random ticks disabled");
                rule.set(2, level.getServer());
                PlantFixtures.pollinationGameTick(level, abs);
                helper.assertTrue(be.data().pollinationWork() == 2, "gamerule was not applied exactly once");
                var state = helper.getBlockState(A);
                ((FieldPlantBlock) state.getBlock()).getTicker(level, state,
                        io.github.slugblue.wildroots.core.registry.ModBlockEntities.PLANT.get()).tick(level, abs, state, be);
                helper.assertTrue(be.data().pollinationWork() == 2, "duplicate same-tick call gave extra work");
                rule.set(4000, level.getServer());
                for (int i = 0; i < 8 && FieldPlantBlock.stage(helper.getBlockState(A)) == 4; i++)
                    PlantFixtures.pollinationGameTick(level, abs);
                helper.assertTrue(FieldPlantBlock.stage(helper.getBlockState(A)) == 5,
                        "high randomTickSpeed did not accelerate the complete cycle");
            } finally {
                rule.set(previous, level.getServer());
                ((ServerLevelData) level.getLevelData()).setGameTime(beforeTime);
            }
            helper.succeed();
        }, 0);
    }

    @GameTest(template = SeedSetGameTests.FIELD, batch = "pollination_work_save", timeoutTicks = 400)
    public static void workSurvivesSavingAndAFasterPollinatorDoesNotResetIt(GameTestHelper helper) {
        PlantFixtures.litFarmland(helper, A);
        PlantFixtures.whenLit(helper, List.of(A), () -> {
            var level = helper.getLevel();
            var be = SeedSetGameTests.flowering(helper, A, 921);
            BlockPos abs = helper.absolutePos(A);
            long before = level.getGameTime();
            var rule = level.getGameRules().getRule(GameRules.RULE_RANDOMTICKING);
            int oldSpeed = rule.get();
            try {
                rule.set(3, level.getServer());
                for (int i = 0; i < 1200; i++) PlantFixtures.pollinationGameTick(level, abs);
                long work = 1200 * 3;
                helper.assertTrue(be.data().pollinationWork() == work, "game ticks gave incorrect work");
                helper.assertTrue(PlantFixtures.saveLoad(be).data().pollinationWork() == work, "lost saved flowering work");
                be.setPendingLot(new PendingLot(List.of(), 0, Brush.NONE, true));
                helper.assertTrue(!PollinationTiming.ready(level, abs, be, be.live(level).orElseThrow())
                        && be.data().pollinationWork() == work, "faster pollinator reset work or finished early");
                for (int i = 0; i < 1199; i++) PlantFixtures.pollinationGameTick(level, abs);
                helper.assertTrue(FieldPlantBlock.stage(helper.getBlockState(A)) == 4, "bee seeds ripened early");
                PlantFixtures.pollinationGameTick(level, abs);
                helper.assertTrue(FieldPlantBlock.stage(helper.getBlockState(A)) == 5, "bee time did not use prior work");
                helper.assertTrue(be.data().pollinationWork() == 0, "finished cycle retained work");
            } finally {
                rule.set(oldSpeed, level.getServer());
                ((ServerLevelData) level.getLevelData()).setGameTime(before);
            }
            helper.succeed();
        }, 0);
    }

    @GameTest(template = SeedSetGameTests.FIELD, batch = "pollination_brush_thirty_seconds", timeoutTicks = 400)
    public static void brushFinishesAtExactlySixHundredGameTicksAndOnlyFlowersTick(GameTestHelper helper) {
        PlantFixtures.litFarmland(helper, A);
        PlantFixtures.whenLit(helper, List.of(A), () -> {
            var level = helper.getLevel();
            var be = SeedSetGameTests.flowering(helper, A, 923);
            BlockPos abs = helper.absolutePos(A);
            long before = level.getGameTime();
            var rule = level.getGameRules().getRule(GameRules.RULE_RANDOMTICKING);
            int oldSpeed = rule.get();
            try {
                rule.set(3, level.getServer());
                be.setPendingLot(new PendingLot(List.of(), 0, Brush.SELF, false));
                var block = (FieldPlantBlock) helper.getBlockState(A).getBlock();
                var type = io.github.slugblue.wildroots.core.registry.ModBlockEntities.PLANT.get();
                for (int stage : new int[]{0, 1, 2, 3, 5}) helper.assertTrue(
                        block.getTicker(level, block.getStateForAge(stage), type) == null, "ticker outside flowering");
                helper.assertTrue(block.getTicker(level, block.getStateForAge(4).setValue(FieldPlantBlock.ACTIVE, false), type) == null,
                        "static wild flower got a ticker");
                helper.assertTrue(block.getTicker(level, block.getStateForAge(4), net.minecraft.world.level.block.entity.BlockEntityType.CHEST) == null,
                        "foreign block entity type got a ticker");
                for (int i = 0; i < 599; i++) PlantFixtures.pollinationGameTick(level, abs);
                helper.assertTrue(FieldPlantBlock.stage(helper.getBlockState(A)) == 4 && be.data().pollinationWork() == 1797,
                        "brush finished before thirty seconds");
                PlantFixtures.pollinationGameTick(level, abs);
                helper.assertTrue(FieldPlantBlock.stage(helper.getBlockState(A)) == 5, "brush missed the thirtieth second");
            } finally {
                rule.set(oldSpeed, level.getServer());
                ((ServerLevelData) level.getLevelData()).setGameTime(before);
            }
            helper.succeed();
        }, 0);
    }

    @GameTest(template = SeedSetGameTests.FIELD, batch = "pollination_wild_soil", timeoutTicks = 400)
    public static void brushKeepsBothWildPlantsAliveOnNaturalSoil(GameTestHelper helper) {
        List<BlockPos> cells = List.of(A, B);
        List<BlockPos> lit = List.of(A, B, A.west(3));
        lit.forEach(p -> PlantFixtures.litFarmland(helper, p));
        PlantFixtures.whenLit(helper, lit, () -> {
            var level = helper.getLevel();
            for (int i = 0; i < cells.size(); i++) {
                BlockPos at = cells.get(i), abs = helper.absolutePos(at);
                helper.setBlock(at, io.github.slugblue.wildroots.core.registry.ModBlocks.WILD_CARROT.get()
                        .getStateForAge(4).setValue(FieldPlantBlock.ACTIVE, false));
                var be = (PlantBlockEntity) helper.getBlockEntity(at);
                be.init(Definitions.current().genetics(), new PlantBlockEntity.Init(PlantFixtures.SP,
                        PlantFixtures.genome(904 + i), PlantFixtures.noise(905 + i),
                        Passport.wild(abs.asLong(), "meadow", false, false), null, Classification.WILD, null, 0, 0, 0));
                helper.setBlock(at.below(), Blocks.GRASS_BLOCK.defaultBlockState());
            }
            var player = JournalGameTests.survival(helper, "brush_wild_soil");
            var brush = ModItems.POLLINATION_BRUSH.get();
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(brush));
            var old = io.github.slugblue.wildroots.journal.Journals.setSender(
                    io.github.slugblue.wildroots.journal.Journals.recording((p, s) -> { }, (p, s) -> { }));
            try {
                for (BlockPos at : cells) {
                    player.getCooldowns().removeCooldown(brush);
                    BlockPos abs = helper.absolutePos(at);
                    var context = new UseOnContext(player, InteractionHand.MAIN_HAND,
                            new BlockHitResult(Vec3.atCenterOf(abs), Direction.UP, abs, false));
                    helper.assertTrue(brush.useOn(context).consumesAction(), "brush did not accept wild flower");
                }
                helper.assertTrue(helper.getBlockState(B).getValue(FieldPlantBlock.ACTIVE), "wild recipient stayed static");
                // A real support update exposed the original ACTIVE/wild-soil regression.
                cells.forEach(p -> helper.setBlock(p.below(), Blocks.DIRT.defaultBlockState()));
                for (BlockPos at : cells) helper.assertTrue(helper.getBlockState(at).getBlock() instanceof FieldPlantBlock
                        && helper.getBlockState(at).canSurvive(level, helper.absolutePos(at)), "wild flower popped after brushing");
                var recipient = (PlantBlockEntity) helper.getBlockEntity(B);
                helper.assertTrue(recipient.data().pending() != null && recipient.data().pending().brush() == Brush.CROSS,
                        "crossing disappeared during soil update");
                BlockPos control = A.west(3);
                PlantFixtures.plant(PlantFixtures.place(helper, control, 4), 922);
                helper.setBlock(control.below(), Blocks.DIRT.defaultBlockState());
                helper.assertTrue(!(helper.getBlockState(control).getBlock() instanceof FieldPlantBlock),
                        "planted crops incorrectly gained wild-soil support");
            } finally {
                io.github.slugblue.wildroots.journal.Journals.setSender(old);
                player.discard();
            }
            helper.succeed();
        }, 0);
    }
}
