package io.github.slugblue.wildroots.gametest;

import io.github.slugblue.wildroots.WildRoots;
import io.github.slugblue.wildroots.butterfly.ButterflyEntity;
import io.github.slugblue.wildroots.core.registry.ModEntities;
import io.github.slugblue.wildroots.genetics.codec.FatherRecord;
import io.github.slugblue.wildroots.genetics.pollination.Donor;
import io.github.slugblue.wildroots.plant.PlantBlockEntity;
import io.github.slugblue.wildroots.pollination.PollinationService;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(WildRoots.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ButterflyVisitGameTests {
    private static final BlockPos A = new BlockPos(8, 2, 8), B = A.east();
    private ButterflyVisitGameTests() { }
    private static void tick(ButterflyEntity b) { b.tickCount++; b.tick(); }

    private static FatherRecord compatible(PlantBlockEntity be) {
        var live = be.live((net.minecraft.server.level.ServerLevel) be.getLevel()).orElseThrow();
        for (int i = 1000; i < 1200; i++) {
            var candidate = PlantFixtures.genome(i);
            if (live.genetics().chooseFathers(live.def(), live.genome(), List.of(),
                    List.of(new Donor(candidate, 1, true)), 1).orElseThrow().compatibleDonors() > 0)
                return live.genetics().encode(candidate).toFather();
        }
        throw new AssertionError("no compatible fixture");
    }

    @GameTest(template = SeedSetGameTests.FIELD, batch = "butterfly_foreign_lock", timeoutTicks = 400)
    public static void foreignPollenIsAcceptedOnceSurvivesReloadAndExcludesLaterNeighbours(GameTestHelper helper) {
        List.of(A, B).forEach(p -> PlantFixtures.litFarmland(helper, p));
        PlantFixtures.whenLit(helper, List.of(A, B), () -> {
            var level = helper.getLevel();
            var mother = SeedSetGameTests.flowering(helper, A, 950);
            helper.setBlock(A, helper.getBlockState(A).setValue(io.github.slugblue.wildroots.plant.FieldPlantBlock.ACTIVE, false));
            helper.assertTrue(!mother.pollinatorVisit(new FatherRecord(0, null), 1, true)
                    && !mother.hasForeignPollen(), "invalid first pollen locked the mother");
            var father = compatible(mother);
            mother.setPendingLot(new io.github.slugblue.wildroots.plant.PendingLot(List.of(
                    new io.github.slugblue.wildroots.item.LotFathersNbt.Father(father, 1, false)), 0,
                    io.github.slugblue.wildroots.genetics.pollination.Brush.NONE, false));
            helper.assertTrue(!mother.hasForeignPollen(), "background neighbours pre-empted insect delivery");
            helper.assertTrue(mother.pollinatorVisit(father, 1, true), "compatible first donor rejected");
            helper.assertTrue(helper.getBlockState(A).getValue(io.github.slugblue.wildroots.plant.FieldPlantBlock.ACTIVE),
                    "accepted foreign pollen did not start a static plant cycle");
            helper.assertTrue(!mother.pollinatorVisit(father, 2, false), "second foreign delivery accepted");
            helper.assertTrue(!mother.pollinatorVisit(null, 1, false), "late visit changed accepted provenance");
            helper.assertTrue(PlantFixtures.saveLoad(mother).hasForeignPollen(), "lock lost on save/load");
            SeedSetGameTests.flowering(helper, B, 951);
            PollinationService.seedSet(level, helper.absolutePos(A), mother, mother.live(level).orElseThrow());
            helper.assertTrue(mother.data().pending().fathers().size() == 1
                    && mother.data().pending().fathers().get(0).genome().equals(father), "later neighbour bypassed lock at seed set");
            mother.setPendingLot(null);
            helper.assertTrue(!mother.hasForeignPollen(), "new cycle stayed locked");
            helper.succeed();
        }, 0);
    }

    @GameTest(template = SeedSetGameTests.FIELD, batch = "pollination_real_ticker", timeoutTicks = 400)
    public static void activatingAStaticFlowerInstallsItsRealWorldTicker(GameTestHelper helper) {
        List.of(A, B).forEach(p -> PlantFixtures.litFarmland(helper, p));
        PlantFixtures.whenLit(helper, List.of(A, B), () -> {
            var level = helper.getLevel();
            var rule = level.getGameRules().getRule(net.minecraft.world.level.GameRules.RULE_RANDOMTICKING);
            int old = rule.get();
            var mother = SeedSetGameTests.flowering(helper, A, 952);
            helper.setBlock(A, helper.getBlockState(A).setValue(io.github.slugblue.wildroots.plant.FieldPlantBlock.ACTIVE, false));
            helper.assertTrue(mother.pollinatorVisit(compatible(mother), 1, true), "activation failed");
            var b = PlantFixtures.mother(PlantFixtures.place(helper, B, 3), 953);
            io.github.slugblue.wildroots.plant.GrowthLogic.advance(level, helper.absolutePos(B), helper.getBlockState(B),
                    b, b.live(level).orElseThrow(), 4);
            rule.set(28800, level.getServer());
            helper.runAfterDelay(3, () -> {
                try {
                    helper.assertTrue(io.github.slugblue.wildroots.plant.FieldPlantBlock.stage(helper.getBlockState(A)) == 5,
                            "world did not install ticker after activation");
                    helper.assertTrue(io.github.slugblue.wildroots.plant.FieldPlantBlock.stage(helper.getBlockState(B)) == 5,
                            "world did not install ticker after entering flowering");
                    helper.succeed();
                } finally { rule.set(old, level.getServer()); }
            });
        }, 0);
    }

    @GameTest(template = SmokeGameTests.FLOOR_3X3X3, batch = "butterfly_flower_lease")
    public static void ordinaryFlowerHasOneVisitorAndCollectionDoesNotInventCropPollen(GameTestHelper helper) {
        var level = helper.getLevel(); var sky = SkyPlatform.build(helper, 8);
        BlockPos at = sky.surface();
        var first = ModEntities.BUTTERFLY.get().create(level);
        var second = ModEntities.BUTTERFLY.get().create(level);
        try (var weather = new ButterflyGameTests.Weather(level, false, false)) {
            level.setBlock(at, Blocks.POPPY.defaultBlockState(), 2);
            first.moveTo(at.getX() + .5, at.getY() + .625, at.getZ() + .5);
            second.moveTo(at.getX() + .5, at.getY() + 1.2, at.getZ() + .5);
            tick(first); tick(second);
            helper.assertTrue(first.feeding() && at.equals(first.flowerTarget()), "did not sit on an ordinary flower");
            helper.assertTrue(second.flowerTarget() == null && !second.feeding(), "two visitors claimed one flower");
            helper.assertTrue(!first.pollen().hasPollen(), "ordinary flower fabricated a crop genome");
            first.discard(); second.tickCount = 40; tick(second);
            helper.assertTrue(at.equals(second.flowerTarget()), "removed butterfly did not release its flower");
        } finally { first.discard(); second.discard(); sky.clear(); }
        helper.succeed();
    }

    @GameTest(template = SmokeGameTests.FLOOR_3X3X3, batch = "butterfly_night_priority")
    public static void nightInterruptsFeedingReleasesFlowerAndPrefersSleep(GameTestHelper helper) {
        var level = helper.getLevel(); var sky = SkyPlatform.build(helper, 8);
        BlockPos at = sky.surface(); var butterfly = ModEntities.BUTTERFLY.get().create(level);
        try {
            level.setBlock(at, Blocks.POPPY.defaultBlockState(), 2);
            level.setBlock(at.above(3), Blocks.STONE.defaultBlockState(), 2);
            butterfly.moveTo(at.getX() + .5, at.getY() + .625, at.getZ() + .5);
            try (var day = new ButterflyGameTests.Weather(level, false, false)) { tick(butterfly); }
            helper.assertTrue(butterfly.feeding(), "daytime visit did not begin");
            try (var night = new ButterflyGameTests.Weather(level, false, true)) {
                butterfly.tickCount = 180;
                for (int i = 0; i < 150; i++) {
                    tick(butterfly);
                    helper.assertTrue(butterfly.flowerTarget() == null && !butterfly.feeding(), "night restarted a flower visit");
                }
                helper.assertTrue(butterfly.resting(), "pollination or wandering displaced available sleep");
            }
        } finally { butterfly.discard(); sky.clear(); }
        helper.succeed();
    }
}
