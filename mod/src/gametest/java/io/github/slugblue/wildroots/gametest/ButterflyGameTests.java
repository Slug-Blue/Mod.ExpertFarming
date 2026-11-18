package io.github.slugblue.wildroots.gametest;

import io.github.slugblue.wildroots.WildRoots;
import io.github.slugblue.wildroots.butterfly.ButterflyEntity;
import io.github.slugblue.wildroots.butterfly.ButterflySpawning;
import io.github.slugblue.wildroots.core.registry.ModEntities;
import io.github.slugblue.wildroots.core.registry.ModItems;
import io.github.slugblue.wildroots.plant.FieldPlantBlock;
import io.github.slugblue.wildroots.plant.PlantBlockEntity;
import io.github.slugblue.wildroots.pollination.BeePollen;
import io.github.slugblue.wildroots.pollination.PollinationService;
import io.github.slugblue.wildroots.pollination.PollinationTiming;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.ServerLevelData;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(WildRoots.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ButterflyGameTests {
    private ButterflyGameTests() { }
    /** ServerLevel normally advances tickCount before Entity.tick; these detached fixtures need the same clock. */
    private static void tick(ButterflyEntity butterfly) { butterfly.tickCount++; butterfly.tick(); }

    static final String FLOOR = SmokeGameTests.FLOOR_3X3X3;
    static final BlockPos A = new BlockPos(1, 2, 1), B = new BlockPos(2, 2, 1);

    @GameTest(template = FLOOR, batch = "butterfly_revisit", timeoutTicks = 400)
    public static void stalePollenDoesNotPreventVisitingANewGenerationAtTheSameCell(GameTestHelper helper) {
        var cells = List.of(A);
        cells.forEach(p -> PlantFixtures.litFarmland(helper, p));
        PlantFixtures.whenLit(helper, cells, () -> {
            var level = helper.getLevel();
            var plant = SeedSetGameTests.flowering(helper, A, 910);
            BlockPos at = helper.absolutePos(A);
            var butterfly = ModEntities.BUTTERFLY.get().create(level);
            try (Weather weather = new Weather(level, false, false)) {
                butterfly.moveTo(at.getX() + .5, at.getY() + .9, at.getZ() + .5);
                butterfly.pollen().collect(PlantFixtures.SP, at.asLong(), plant.data().genome().toFather());
                butterfly.pollen().setCounters(0, BeePollen.MAX_AGE_OUTSIDE + 1);
                for (int i = 0; i < 200; i++) tick(butterfly);
                helper.assertTrue(plant.data().pending() != null && plant.data().pending().butterflies(),
                        "never revisited a cell after the old pollen expired");
            } finally { butterfly.discard(); }
            helper.succeed();
            }, 0);
    }

    @GameTest(template = FLOOR, batch = "butterfly_neighbours", timeoutTicks = 400)
    public static void receivedNeighbourPollenKeepsItsTimeAndFatherAfterNeighbourIsGone(GameTestHelper helper) {
        var cells = List.of(A, B);
        cells.forEach(p -> PlantFixtures.litFarmland(helper, p));
        PlantFixtures.whenLit(helper, cells, () -> {
            var a = SeedSetGameTests.flowering(helper, A, 908);
            var b = SeedSetGameTests.flowering(helper, B, 909);
            var level = helper.getLevel();
            helper.assertTrue(PollinationTiming.method(level, helper.absolutePos(B), b.live(level).orElseThrow())
                    == PollinationTiming.Method.NEIGHBOURS, "neighbour did not pollinate");
            helper.setBlock(A, Blocks.AIR.defaultBlockState());
            helper.assertTrue(PollinationTiming.method(level, helper.absolutePos(B), b.live(level).orElseThrow())
                    == PollinationTiming.Method.NEIGHBOURS, "lost an already received pollination");
            PollinationService.seedSet(level, helper.absolutePos(B), b, b.live(level).orElseThrow());
            helper.assertTrue(!b.data().pending().fathers().isEmpty(), "lost the copied father");
            helper.succeed();
            }, 0);
    }

    @GameTest(template = FLOOR, batch = "butterfly_four_flowers")
    public static void actualChunkCounterAcceptsExactlyFourAndDoesNotCountAnUpperHalfTwice(GameTestHelper helper) {
        var level = helper.getLevel();
        var chunk = level.getChunkAt(helper.absolutePos(A));
        var changed = new java.util.LinkedHashMap<BlockPos, net.minecraft.world.level.block.state.BlockState>();
        var sections = chunk.getSections();
        for (int sectionIndex = 0; sectionIndex < sections.length; sectionIndex++) {
            var section = sections[sectionIndex];
            if (!section.maybeHas(s -> s.is(net.minecraft.tags.BlockTags.FLOWERS))) continue;
            for (int y = 0; y < 16; y++) for (int x = 0; x < 16; x++) for (int z = 0; z < 16; z++) {
                var state = section.getBlockState(x, y, z);
                if (state.is(net.minecraft.tags.BlockTags.FLOWERS)) {
                    BlockPos pos = new BlockPos(chunk.getPos().getMinBlockX() + x,
                            level.getMinBuildHeight() + sectionIndex * 16 + y, chunk.getPos().getMinBlockZ() + z);
                    changed.put(pos, state);
                }
            }
        }
        try {
            changed.keySet().forEach(p -> level.setBlock(p, Blocks.AIR.defaultBlockState(), 2));
            var min = chunk.getPos().getWorldPosition().atY(helper.absolutePos(A).getY());
            for (int i = 0; i < 4; i++) {
                BlockPos at = min.offset(1 + i % 2, 0, 1 + i / 2);
                changed.putIfAbsent(at.below(), level.getBlockState(at.below()));
                level.setBlock(at.below(), Blocks.GRASS_BLOCK.defaultBlockState(), 2);
                changed.putIfAbsent(at, level.getBlockState(at));
                level.setBlock(at, Blocks.POPPY.defaultBlockState(), 2);
                helper.assertTrue(ButterflySpawning.countFlowers(chunk) == i + 1, "flower count " + (i + 1));
                helper.assertTrue(ButterflySpawning.eligible(false, ButterflySpawning.countFlowers(chunk)) == (i == 3),
                        "wrong threshold at " + (i + 1));
            }
            BlockPos upper = min.offset(4, 0, 4);
            changed.putIfAbsent(upper.below(2), level.getBlockState(upper.below(2)));
            changed.putIfAbsent(upper.below(), level.getBlockState(upper.below()));
            changed.putIfAbsent(upper, level.getBlockState(upper));
            level.setBlock(upper.below(2), Blocks.GRASS_BLOCK.defaultBlockState(), 2);
            level.setBlock(upper.below(), Blocks.SUNFLOWER.defaultBlockState(), 2);
            level.setBlock(upper, Blocks.SUNFLOWER.defaultBlockState().setValue(
                    net.minecraft.world.level.block.DoublePlantBlock.HALF,
                    net.minecraft.world.level.block.state.properties.DoubleBlockHalf.UPPER), 2);
            // Two poppies plus one whole sunflower are three flowers, not four halves.
            level.setBlock(min.offset(1, 0, 1), Blocks.AIR.defaultBlockState(), 2);
            level.setBlock(min.offset(2, 0, 1), Blocks.AIR.defaultBlockState(), 2);
            helper.assertTrue(ButterflySpawning.countFlowers(chunk) == 3, "upper flower half counted twice");
        } finally { changed.forEach((p, state) -> level.setBlock(p, state, 2)); }
        helper.succeed();
    }

    static final class Weather implements AutoCloseable {
        final ServerLevel level;
        final long day;
        final int rainTime, thunderTime, clearTime;
        final boolean rain, thunder;
        final float rainLevel, thunderLevel;
        Weather(ServerLevel level, boolean raining, boolean night) {
            this.level = level;
            var data = (ServerLevelData) level.getLevelData();
            day = level.getDayTime(); rainTime = data.getRainTime(); thunderTime = data.getThunderTime();
            clearTime = data.getClearWeatherTime(); rain = data.isRaining(); thunder = data.isThundering();
            rainLevel = level.getRainLevel(1); thunderLevel = level.getThunderLevel(1);
            level.setDayTime(night ? 18000 : 1000);
            level.setWeatherParameters(0, 6000, raining, false);
            level.setRainLevel(raining ? 1 : 0); level.setThunderLevel(0); level.updateSkyBrightness();
        }
        public void close() {
            var data = (ServerLevelData) level.getLevelData();
            level.setDayTime(day); data.setRainTime(rainTime); data.setThunderTime(thunderTime);
            data.setClearWeatherTime(clearTime); data.setRaining(rain); data.setThundering(thunder);
            level.setRainLevel(rainLevel); level.setThunderLevel(thunderLevel); level.updateSkyBrightness();
        }
    }

    @GameTest(template = FLOOR, batch = "butterfly_pollen", timeoutTicks = 400)
    public static void butterflyTransfersRealPollenAndRejectsLaterBee(GameTestHelper helper) {
        var cells = List.of(A, B);
        cells.forEach(p -> PlantFixtures.litFarmland(helper, p));
        PlantFixtures.whenLit(helper, cells, () -> {
            var a = SeedSetGameTests.flowering(helper, A, 901);
            var b = SeedSetGameTests.flowering(helper, B, 902);
            BeePollen pollen = new BeePollen();
            helper.assertTrue(PollinationService.butterflyVisit(helper.getLevel(), pollen, helper.absolutePos(A))
                    == PollinationService.Visit.VISITED, "first flower did not supply pollen");
            helper.assertTrue(PollinationService.butterflyVisit(helper.getLevel(), pollen, helper.absolutePos(B))
                    == PollinationService.Visit.DONOR, "pollen not delivered");
            helper.assertTrue(b.data().pending().butterflies() && !b.data().pending().bees(), "wrong insect provenance");
            helper.assertTrue(PollinationTiming.method(helper.getLevel(), helper.absolutePos(B), b.live(helper.getLevel()).orElseThrow())
                    == PollinationTiming.Method.BUTTERFLIES, "wrong butterfly time");
            PollinationService.seedSet(helper.getLevel(), helper.absolutePos(B), b, b.live(helper.getLevel()).orElseThrow());
            helper.assertTrue(b.data().pending().butterflies() && !b.data().pending().fathers().isEmpty(), "lost pollen at seed set");
            b.beeVisit(null, 1);
            helper.assertTrue(PollinationTiming.method(helper.getLevel(), helper.absolutePos(B), b.live(helper.getLevel()).orElseThrow())
                    == PollinationTiming.Method.BUTTERFLIES, "late bee replaced the accepted foreign pollen");
            helper.succeed();
            }, 0);
    }

    @GameTest(template = FLOOR, batch = "butterfly_clock", timeoutTicks = 400)
    public static void seedsWaitForGameTicksAndWorkSurvivesReload(GameTestHelper helper) {
        var cells = List.of(A);
        cells.forEach(p -> PlantFixtures.litFarmland(helper, p));
        PlantFixtures.whenLit(helper, cells, () -> {
            var level = helper.getLevel();
            var be = SeedSetGameTests.flowering(helper, A, 903);
            be.setFloweringSince(level.getGameTime());
            BlockPos abs = helper.absolutePos(A);
            for (int i = 0; i < 2; i++) level.getBlockState(abs).randomTick(level, abs, RandomSource.create(i));
            helper.assertTrue(FieldPlantBlock.stage(level.getBlockState(abs)) == 4, "flowering finished early");
            var saved = PlantFixtures.saveLoad(be);
            helper.assertTrue(saved.data().floweringSince() == level.getGameTime()
                    && saved.data().pollinationWork() == be.data().pollinationWork(), "progress lost on reload");
            long before = level.getGameTime();
            try {
                ((ServerLevelData) level.getLevelData()).setGameTime(before + PollinationTiming.Method.SELF.ticks());
                helper.assertTrue(FieldPlantBlock.stage(level.getBlockState(abs)) == 4, "game time bypassed random ticks");
                PlantFixtures.acceleratedGrowthTick(level, abs, RandomSource.create(1));
                helper.assertTrue(FieldPlantBlock.stage(level.getBlockState(abs)) == 5, "random ticks did not finish selfing");
            } finally { ((ServerLevelData) level.getLevelData()).setGameTime(before); }
            helper.succeed();
            }, 0);
    }

    @GameTest(template = FLOOR, batch = "butterfly_brush", timeoutTicks = 400)
    public static void brushChoosesTheFatherAndInsectsCannotReplaceIt(GameTestHelper helper) {
        var cells = List.of(A, B);
        cells.forEach(p -> PlantFixtures.litFarmland(helper, p));
        PlantFixtures.whenLit(helper, cells, () -> {
            var level = helper.getLevel();
            var a = SeedSetGameTests.flowering(helper, A, 904);
            var b = SeedSetGameTests.flowering(helper, B, 905);
            helper.setBlock(B, helper.getBlockState(B).setValue(FieldPlantBlock.ACTIVE, false));
            var player = JournalGameTests.survival(helper, "brush_parent");
            var brush = ModItems.POLLINATION_BRUSH.get();
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(brush));
            var old = io.github.slugblue.wildroots.journal.Journals.setSender(
                    io.github.slugblue.wildroots.journal.Journals.recording((p, s) -> { }, (p, s) -> { }));
            try {
                for (BlockPos relative : List.of(A, B)) {
                    player.getCooldowns().removeCooldown(brush);
                    BlockPos abs = helper.absolutePos(relative);
                    var ctx = new UseOnContext(player, InteractionHand.MAIN_HAND,
                            new BlockHitResult(Vec3.atCenterOf(abs), Direction.UP, abs, false));
                    helper.assertTrue(brush.useOn(ctx) == InteractionResult.CONSUME, "brush failed at " + relative);
                }
                helper.assertTrue(b.data().pending().brush() == io.github.slugblue.wildroots.genetics.pollination.Brush.CROSS,
                        "no controlled crossing");
                helper.assertTrue(helper.getBlockState(B).getValue(FieldPlantBlock.ACTIVE), "pollinated wild flower stayed static");
                b.beeVisit(b.data().genome().toFather(), 100);
                PollinationService.seedSet(level, helper.absolutePos(B), b, b.live(level).orElseThrow());
                helper.assertTrue(b.data().pending().pOut() == 1 && b.data().pending().fathers().size() == 1, "uncontrolled fathers");
                helper.assertTrue(java.util.Arrays.equals(a.data().genome().a(), b.data().pending().fathers().get(0).genome().a()),
                        "wrong father");
                helper.assertTrue(PollinationTiming.method(level, helper.absolutePos(B), b.live(level).orElseThrow())
                        == PollinationTiming.Method.BRUSH, "brush not fastest");
            } finally { io.github.slugblue.wildroots.journal.Journals.setSender(old); player.discard(); }
            helper.succeed();
            }, 0);
    }

    @GameTest(template = FLOOR, batch = "butterfly_weather", timeoutTicks = 200)
    public static void rainKillsExposedButterflyButRoofsAndLitNightSheltersProtect(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        SkyPlatform sky = SkyPlatform.build(helper, 8);
        BlockPos open = sky.surface(), safe = open.offset(3, 0, 0);
        Runnable undo = ClimateKit.setBiome(level, open, 16, Biomes.PLAINS);
        ButterflyEntity exposed = ModEntities.BUTTERFLY.get().create(level);
        ButterflyEntity sheltered = ModEntities.BUTTERFLY.get().create(level);
        try {
            level.setBlock(safe.above(2), Blocks.GLOWSTONE.defaultBlockState(), 2);
            try (Weather weather = new Weather(level, true, false)) {
                helper.assertTrue(level.isRainingAt(open), "rain fixture is dry");
                // Keep this specimen high enough that no reachable cover exists during the 70 wet ticks.
                exposed.moveTo(open.getX() - 6 + .5, open.getY() + 12, open.getZ() + .5);
                sheltered.moveTo(safe.getX() + .5, safe.getY(), safe.getZ() + .5);
                for (int i = 0; i < 69; i++) { tick(exposed); tick(sheltered); }
                helper.assertTrue(exposed.isAlive() && exposed.wetTicks() == 69, "did not survive the first 69 rain ticks");
                tick(exposed); tick(sheltered);
                helper.assertTrue(!exposed.isAlive(), "survived uncovered rain");
                helper.assertTrue(sheltered.isAlive() && sheltered.wetTicks() == 0 && sheltered.resting(), "roof did not protect");
            }
            try (Weather weather = new Weather(level, false, true)) {
                sheltered.tickCount = 200;
                for (int i = 0; i < 220; i++) tick(sheltered);
                helper.assertTrue(sheltered.resting(), "artificial light prevented night rest");
                helper.assertTrue(ButterflyEntity.nearestShelter(level, safe).equals(safe), "not the nearest shelter");
            }
        } finally { exposed.discard(); sheltered.discard(); undo.run(); sky.clear(); }
        helper.succeed();
    }

    @GameTest(template = FLOOR, batch = "butterfly_landing", timeoutTicks = 400)
    public static void nightFlightFindsRoofAndLandsOnItsSupportingSurface(GameTestHelper helper) {
        var level = helper.getLevel();
        SkyPlatform sky = SkyPlatform.build(helper, 12);
        BlockPos safe = sky.surface().offset(4, 0, 0);
        var butterfly = ModEntities.BUTTERFLY.get().create(level);
        try (Weather weather = new Weather(level, false, true)) {
            for (int x = -1; x <= 1; x++) for (int z = -1; z <= 1; z++)
                level.setBlock(safe.offset(x, 3, z), Blocks.OAK_LEAVES.defaultBlockState(), 2);
            Vec3 start = Vec3.atBottomCenterOf(sky.surface()).add(0, 1.4, 0);
            butterfly.moveTo(start.x, start.y, start.z);
            butterfly.tickCount = 180;
            for (int i = 0; i < 400; i++) tick(butterfly);
            helper.assertTrue(butterfly.resting(), "never landed under a nearby roof: " + butterfly.position());
            helper.assertTrue(ButterflyEntity.covered(level, butterfly.blockPosition()), "rested outside cover");
            helper.assertTrue(butterfly.hanging() || Math.abs(butterfly.getY() - safe.getY()) < .03, "rested without an attachment: " + butterfly.getY());
            helper.assertTrue(butterfly.getDeltaMovement().lengthSqr() < .00001, "slid while resting");
            // Removing the roof wakes it; no permanent frozen state remains.
            for (int x = -1; x <= 1; x++) for (int z = -1; z <= 1; z++)
                level.setBlock(safe.offset(x, 3, z), Blocks.AIR.defaultBlockState(), 2);
            tick(butterfly);
            helper.assertTrue(!butterfly.resting() && butterfly.flying(), "continued sleeping after cover was removed");
        } finally { butterfly.discard(); sky.clear(); }
        helper.succeed();
    }

    @GameTest(template = FLOOR, batch = "butterfly_midair", timeoutTicks = 400)
    public static void coveredAirIsNotASleepingSurfaceAndVariantSurvivesSaving(GameTestHelper helper) {
        var level = helper.getLevel();
        SkyPlatform sky = SkyPlatform.build(helper, 8);
        BlockPos safe = sky.surface();
        var butterfly = ModEntities.BUTTERFLY.get().create(level);
        var restored = ModEntities.BUTTERFLY.get().create(level);
        try (Weather weather = new Weather(level, false, true)) {
            level.setBlock(safe.above(3), Blocks.STONE.defaultBlockState(), 2);
            butterfly.moveTo(safe.getX() + .5, safe.getY() + 1, safe.getZ() + .5);
            butterfly.tickCount = 180;
            tick(butterfly);
            helper.assertTrue(!butterfly.resting(), "a roof alone froze it in the air");
            for (int i = 0; i < 400; i++) tick(butterfly);
            helper.assertTrue(butterfly.resting() && (butterfly.hanging() || Math.abs(butterfly.getY() - safe.getY()) < .03),
                    "did not descend to its perch: " + butterfly.position());
            var saved = new net.minecraft.nbt.CompoundTag();
            butterfly.addAdditionalSaveData(saved);
            restored.readAdditionalSaveData(saved);
            helper.assertTrue(restored.variant() == butterfly.variant(), "lost colour variant");
            saved.putInt("Variant", 999);
            restored.readAdditionalSaveData(saved);
            helper.assertTrue(restored.variant() == 2, "unbounded variant");
        } finally { butterfly.discard(); restored.discard(); sky.clear(); }
        helper.succeed();
    }

    @GameTest(template = FLOOR, batch = "butterfly_open_night", timeoutTicks = 400)
    public static void nightWithoutAShelterKeepsSearchingInsteadOfFreezing(GameTestHelper helper) {
        var level = helper.getLevel();
        SkyPlatform sky = SkyPlatform.build(helper, 12);
        var butterfly = ModEntities.BUTTERFLY.get().create(level);
        try (Weather weather = new Weather(level, false, true)) {
            var start = Vec3.atBottomCenterOf(sky.surface()).add(0, 2, 0);
            butterfly.moveTo(start.x, start.y, start.z);
            double travelled = 0;
            for (int i = 0; i < 120; i++) {
                var before = butterfly.position(); tick(butterfly);
                travelled += butterfly.position().distanceTo(before);
                helper.assertTrue(!butterfly.resting(), "slept without a roof");
            }
            helper.assertTrue(travelled > 3, "hovered in place without a shelter: " + travelled);
        } finally { butterfly.discard(); sky.clear(); }
        helper.succeed();
    }

    @GameTest(template = FLOOR, batch = "butterfly_obstacle", timeoutTicks = 400)
    public static void flightPassesAnObstacleAndActuallyReachesTheFlower(GameTestHelper helper) {
        var level = helper.getLevel();
        SkyPlatform sky = SkyPlatform.build(helper, 10);
        BlockPos flower = sky.surface().offset(5, 0, 0);
        var butterfly = ModEntities.BUTTERFLY.get().create(level);
        try (Weather weather = new Weather(level, false, false)) {
            level.setBlock(flower.below(), Blocks.FARMLAND.defaultBlockState(), 2);
            level.setBlock(flower, io.github.slugblue.wildroots.core.registry.ModBlocks.WILD_CARROT.get().getStateForAge(4), 2);
            var plant = PlantFixtures.plant((PlantBlockEntity) level.getBlockEntity(flower), 911);
            for (int z = -1; z <= 1; z++) level.setBlock(sky.surface().offset(2, 0, z), Blocks.STONE.defaultBlockState(), 2);
            butterfly.moveTo(sky.surface().getX() + .5, sky.surface().getY() + .6, sky.surface().getZ() + .5);
            for (int i = 0; i < 360; i++) tick(butterfly);
            helper.assertTrue(plant.data().pending() != null && plant.data().pending().butterflies(),
                    "failed to fly around obstacle and visit flower: " + butterfly.position());
        } finally { butterfly.discard(); sky.clear(); }
        helper.succeed();
    }

    @GameTest(template = FLOOR, batch = "butterfly_forecast")
    public static void rainCountdownStartsShelterFlightTenTicksBeforeRain(GameTestHelper helper) {
        var level = helper.getLevel();
        SkyPlatform sky = SkyPlatform.build(helper, 10);
        BlockPos start = sky.surface(), safe = start.offset(4, 0, 0);
        Runnable undo = ClimateKit.setBiome(level, start, 16, Biomes.PLAINS);
        var butterfly = ModEntities.BUTTERFLY.get().create(level);
        var weatherRule = level.getGameRules().getRule(net.minecraft.world.level.GameRules.RULE_WEATHER_CYCLE);
        boolean oldRule = weatherRule.get();
        try (Weather weather = new Weather(level, false, false)) {
            weatherRule.set(true, level.getServer());
            for (int x = -1; x <= 1; x++) for (int z = -1; z <= 1; z++)
                level.setBlock(safe.offset(x, 3, z), Blocks.STONE.defaultBlockState(), 2);
            var data = (ServerLevelData) level.getLevelData();
            data.setClearWeatherTime(0); data.setRainTime(11);
            helper.assertTrue(!ButterflyEntity.rainSoon(level, start), "reacted earlier than warning window");
            data.setRainTime(10);
            helper.assertTrue(ButterflyEntity.rainSoon(level, start) && !level.isRainingAt(start), "missing dry-weather forecast");
            butterfly.moveTo(start.getX() + .5, start.getY() + .7, start.getZ() + .5);
            double before = butterfly.position().distanceToSqr(Vec3.atBottomCenterOf(safe));
            tick(butterfly);
            helper.assertTrue(butterfly.flying() && butterfly.getDeltaMovement().x > .01, "did not immediately fly toward cover");
            for (int i = 1; i < 10; i++) tick(butterfly);
            helper.assertTrue(butterfly.position().distanceToSqr(Vec3.atBottomCenterOf(safe)) < before - 1,
                    "warning did not produce shelter flight");
            helper.assertTrue(butterfly.wetTicks() == 0 && butterfly.isNoGravity(), "fell or became wet before rain");
        } finally { weatherRule.set(oldRule, level.getServer()); butterfly.discard(); undo.run(); sky.clear(); }
        helper.succeed();
    }

    @GameTest(template = FLOOR, batch = "butterfly_dry_biome")
    public static void globalRainDoesNotGroundButterfliesInADesert(GameTestHelper helper) {
        var level = helper.getLevel();
        SkyPlatform sky = SkyPlatform.build(helper, 12);
        BlockPos at = sky.surface();
        Runnable undo = ClimateKit.setBiome(level, at, 32, Biomes.DESERT);
        var butterfly = ModEntities.BUTTERFLY.get().create(level);
        try (Weather weather = new Weather(level, true, false)) {
            helper.assertTrue(!level.isRainingAt(at) && !ButterflyEntity.rainSoon(level, at), "desert reacted to global rain");
            butterfly.moveTo(at.getX() + .5, at.getY() + 1.5, at.getZ() + .5);
            double travelled = 0;
            for (int i = 0; i < 60; i++) {
                Vec3 before = butterfly.position(); tick(butterfly);
                travelled += butterfly.position().distanceTo(before);
                helper.assertTrue(butterfly.flying() && butterfly.isNoGravity() && butterfly.wetTicks() == 0,
                        "grounded or wetted a desert butterfly");
            }
            helper.assertTrue(travelled > 1 && butterfly.isAlive(), "desert flight stopped");
        } finally { butterfly.discard(); undo.run(); sky.clear(); }
        helper.succeed();
    }

    @GameTest(template = FLOOR, batch = "butterfly_hanging")
    public static void sleepsAttachedToCeilingAndResumesQuieterNightFlight(GameTestHelper helper) {
        var level = helper.getLevel();
        SkyPlatform sky = SkyPlatform.build(helper, 8);
        BlockPos roof = sky.surface().above(4);
        var butterfly = ModEntities.BUTTERFLY.get().create(level);
        try (Weather weather = new Weather(level, false, true)) {
            level.setBlock(roof, Blocks.OAK_LEAVES.defaultBlockState(), 2);
            butterfly.moveTo(roof.getX() + .5, roof.getY() - .35, roof.getZ() + .5);
            butterfly.tickCount = 200;
            tick(butterfly);
            helper.assertTrue(butterfly.resting() && butterfly.hanging(), "did not attach to ceiling");
            helper.assertTrue(Math.abs(butterfly.getBoundingBox().maxY - roof.getY()) < .0001, "ceiling attachment has a gap");
            butterfly.tickCount = 600;
            tick(butterfly);
            helper.assertTrue(!butterfly.resting() && !butterfly.hanging() && butterfly.flying(), "night sleep never allowed flight");
            helper.assertTrue(butterfly.getDeltaMovement().length() < .08, "night movement was not quieter");
        } finally { butterfly.discard(); sky.clear(); }
        helper.succeed();
    }

    @GameTest(template = FLOOR, batch = "butterfly_birch")
    public static void birchSpawnsOneToThreeAndNeverExceedsTheChunkCap(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ChunkPos chunk = new ChunkPos(helper.absolutePos(A));
        SkyPlatform sky = SkyPlatform.at(level, new BlockPos(chunk.getMiddleBlockX(), SkyPlatform.Y, chunk.getMiddleBlockZ()), 8);
        BlockPos mid = new BlockPos(chunk.getMiddleBlockX(),
                level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                        chunk.getMiddleBlockX(), chunk.getMiddleBlockZ()), chunk.getMiddleBlockZ());
        Runnable undo = ClimateKit.setBiome(level, mid, 16, Biomes.BIRCH_FOREST);
        AABB box = new AABB(chunk.getMinBlockX(), level.getMinBuildHeight(), chunk.getMinBlockZ(),
                chunk.getMaxBlockX() + 1, level.getMaxBuildHeight(), chunk.getMaxBlockZ() + 1);
        try (Weather weather = new Weather(level, false, false)) {
            int first = ButterflySpawning.tryChunk(level, chunk, RandomSource.create(8));
            helper.assertTrue(first >= 1 && first <= 3, "birch group " + first);
            for (int i = 0; i < 10; i++) ButterflySpawning.tryChunk(level, chunk, RandomSource.create(20 + i));
            helper.assertTrue(level.getEntitiesOfClass(ButterflyEntity.class, box).size() <= 3, "chunk cap exceeded");
        } finally { level.getEntitiesOfClass(ButterflyEntity.class, box).forEach(ButterflyEntity::discard); undo.run(); sky.clear(); }
        helper.succeed();
    }
}
