package io.github.slugblue.wildroots.gametest;

import io.github.slugblue.wildroots.WildRoots;
import io.github.slugblue.wildroots.core.config.ServerConfig;
import io.github.slugblue.wildroots.core.registry.ModBlocks;
import io.github.slugblue.wildroots.genetics.api.ClimateStress;
import io.github.slugblue.wildroots.genetics.api.Environment;
import io.github.slugblue.wildroots.genetics.api.SpeciesDef;
import io.github.slugblue.wildroots.genetics.model.Genome;
import io.github.slugblue.wildroots.plant.ClimateService;
import io.github.slugblue.wildroots.plant.FieldPlantBlock;
import io.github.slugblue.wildroots.plant.GrowthLogic;
import io.github.slugblue.wildroots.plant.Passport;
import io.github.slugblue.wildroots.plant.PlantBlockEntity;
import io.github.slugblue.wildroots.plant.PlantState;
import io.github.slugblue.wildroots.plant.Wilting;
import io.github.slugblue.wildroots.worldgen.StarterPatch;
import io.github.slugblue.wildroots.worldgen.WildPatchFeature;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * WR-711, WR-712, WR-714: wilting and protection (docs/design/carrot-colours-climate.md §4). The world has a random
 * seed, so every test paints the biome it needs ({@link ClimateKit#setBiome}) and switches the flag on for the span
 * of its own synchronous body ({@link ClimateKit#withWilting}); the numbers of the stress are read back through
 * {@link Wilting#read} and the assertions state the relation the design promises, not a copy of the formula.
 *
 * <p>Underground cells (the test areas, sky light 0) are sheltered by §4.5; the cells that need the open sky are on
 * the {@link SkyPlatform} at y 250, whose temperature is the biome's minus 0.21 (the height, GDD §3.5). Growth is
 * denied by a probe where a test counts ticks, so a stage change does not end a plant's strain.
 */
@GameTestHolder(WildRoots.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ClimateStressGameTests {

    static final String FLOOR = SmokeGameTests.FLOOR_3X3X3;
    static final BlockPos A = new BlockPos(0, 2, 0);
    static final BlockPos B = new BlockPos(2, 2, 0);
    static final BlockPos C = new BlockPos(1, 2, 2);
    static final int TIMEOUT = 600;

    private ClimateStressGameTests() {
    }

    // ------------------------------------------------------------------ helpers

    static SpeciesDef def() {
        return PlantFixtures.carrot().def();
    }

    /** Paints {@code biome}, lights the cells, waits for the light, runs {@code body} with wilting on, restores. */
    static void underground(GameTestHelper helper, ResourceKey<Biome> biome, List<BlockPos> cells,
                            Consumer<ServerLevel> body) {
        ServerLevel level = helper.getLevel();
        Runnable undo = ClimateKit.setBiome(level, helper.absolutePos(PlantFixtures.PLANT), 12, biome);
        for (BlockPos c : cells) {
            PlantFixtures.litFarmland(helper, c);
        }
        PlantFixtures.whenLit(helper, cells, () -> {
            try {
                ClimateKit.withWilting(true, () -> body.accept(level));
            } finally {
                undo.run();
            }
            helper.succeed();
        }, 0);
    }

    static ClimateStressGameTests.Reading read(ServerLevel level, BlockPos abs) {
        PlantBlockEntity be = (PlantBlockEntity) level.getBlockEntity(abs);
        return new Reading(Wilting.read(level, abs, level.getBlockState(abs), be, be.live(level).orElseThrow())
                .orElseThrow(() -> new IllegalStateException("no reading at " + abs)));
    }

    /** A reading with its text for assertion messages. */
    record Reading(Wilting.Reading r) {
        double s() {
            return r.stress();
        }

        @Override
        public String toString() {
            return String.format(java.util.Locale.ROOT, "S %.3f (unprotected %.3f, margin %.2f, sheltered %b, irrigated %b, exempt %s)",
                    r.stress(), r.unprotectedStress(), r.margin(), r.sheltered(), r.irrigated(), r.exemption());
        }
    }

    /** Denies growth at the given cells for the span of {@code body}. */
    static void denyingGrowth(List<BlockPos> cells, Runnable body) {
        List<GrowthGameTests.CropProbe> probes = new ArrayList<>();
        for (BlockPos c : cells) {
            GrowthGameTests.CropProbe p = new GrowthGameTests.CropProbe(c, true);
            MinecraftForge.EVENT_BUS.register(p);
            probes.add(p);
        }
        try {
            body.run();
        } finally {
            probes.forEach(MinecraftForge.EVENT_BUS::unregister);
        }
    }

    // ------------------------------------------------------------------ WR-711 / WR-712: sheltered desert (underground)

    /**
     * A purple carrot without heat tolerance in the desert, sheltered (underground: sky light 0): without irrigation
     * the stress is over the margin and it wilts at the third random tick (drooping at the first); with irrigated
     * farmland next to the shelter it stays at 0 however long it ticks.
     */
    @GameTest(template = FLOOR, batch = "climate_underground", timeoutTicks = TIMEOUT)
    public static void shelteredDesertWiltsTheDryPlantAndNotTheIrrigatedOne(GameTestHelper helper) {
        underground(helper, Biomes.DESERT, List.of(A, B), level -> {
            BlockPos a = helper.absolutePos(A);
            BlockPos b = helper.absolutePos(B);
            Genome g = ClimateKit.genome(910, ClimateKit.PURPLE, 0, 0);
            ClimateKit.plant(level, a, 1, g, Passport.PLAYER, 0, 3);
            ClimateKit.plant(level, b, 1, g, Passport.PLAYER, 0, 7);
            Reading ra = read(level, a);
            Reading rb = read(level, b);
            helper.assertTrue(ra.r().sheltered() && !ra.r().irrigated(), "dry cell " + ra);
            helper.assertTrue(rb.r().sheltered() && rb.r().irrigated(), "irrigated cell " + rb);
            helper.assertTrue(ra.r().atMargin(), "the dry plant is at the margin: " + ra);
            helper.assertTrue(!rb.r().atMargin() && rb.s() < ra.s() - 0.05, "irrigation takes the stress off: " + rb + " vs " + ra);
            helper.assertTrue(ra.r().unprotectedStress() > ra.s(), "the shelter lowers the stress: " + ra);
            RandomSource r = RandomSource.create(910);
            denyingGrowth(List.of(a, b), () -> {
                ClimateKit.tick(level, a, r, 1);
                helper.assertTrue(ClimateKit.strain(level, a) == 1, "drooping after the first tick: " + ClimateKit.strain(level, a));
                ClimateKit.tick(level, a, r, 1);
                helper.assertTrue(ClimateKit.strain(level, a) == 1, "still drooping after the second tick");
                ClimateKit.tick(level, a, r, 1);
                helper.assertTrue(ClimateKit.strain(level, a) == FieldPlantBlock.WILTED, "wilted after the third tick");
                for (int i = 0; i < 40; i++) {
                    ClimateKit.tick(level, b, r, 1);
                    helper.assertTrue(ClimateKit.strain(level, b) == 0, "the irrigated plant drooped at tick " + i);
                }
            });
        });
    }

    /**
     * A wilted plant does not grow, is no bone meal target, and gives nothing; a drooping one still gives its
     * seedling lot (the wilting is not final before the third tick).
     */
    @GameTest(template = FLOOR, batch = "climate_underground", timeoutTicks = TIMEOUT)
    public static void wiltedPlantGrowsNotAndYieldsNothing(GameTestHelper helper) {
        underground(helper, Biomes.DESERT, List.of(A, B), level -> {
            BlockPos a = helper.absolutePos(A);
            BlockPos b = helper.absolutePos(B);
            Genome g = ClimateKit.genome(911, ClimateKit.WHITE, 0, 0);
            ClimateKit.plant(level, a, 1, g, Passport.PLAYER, 0, 3);
            ClimateKit.plant(level, b, 1, g, Passport.PLAYER, 0, 3);
            Player player = helper.makeMockSurvivalPlayer();
            RandomSource r = RandomSource.create(911);
            denyingGrowth(List.of(a, b), () -> {
                ClimateKit.tick(level, a, r, 3);
                ClimateKit.tick(level, b, r, 1);
            });
            helper.assertTrue(ClimateKit.strain(level, a) == FieldPlantBlock.WILTED, "a wilted");
            helper.assertTrue(ClimateKit.strain(level, b) == 1, "b drooping");
            helper.assertTrue(ClimateKit.drops(level, a, player).isEmpty(),
                    "a wilted plant dropped " + ClimateKit.drops(level, a, player));
            List<ItemStack> young = ClimateKit.drops(level, b, player);
            helper.assertTrue(young.size() == 1 && young.get(0).is(io.github.slugblue.wildroots.core.registry.ModItems.SEED_LOT.get()),
                    "a drooping plant gives its seedling lot: " + young);
            // no growth however long, no bone meal target, bone meal and growCrops do nothing
            FieldPlantBlock block = ModBlocks.WILD_CARROT.get();
            BlockState wilted = level.getBlockState(a);
            helper.assertFalse(block.isValidBonemealTarget(level, a, wilted, false), "a wilted plant is a bone meal target");
            helper.assertTrue(block.isValidBonemealTarget(level, b, level.getBlockState(b), false), "a drooping plant is not");
            for (int i = 0; i < 200; i++) {
                level.getBlockState(a).randomTick(level, a, r);
            }
            block.performBonemeal(level, r, a, level.getBlockState(a));
            block.growCrops(level, a, level.getBlockState(a));
            helper.assertTrue(FieldPlantBlock.stage(level.getBlockState(a)) == 1
                    && ClimateKit.strain(level, a) == FieldPlantBlock.WILTED, "a wilted plant moved: " + level.getBlockState(a));
        });
    }

    /**
     * Grace and recovery (design §4.2): two ticks of stress make it droop; irrigating the farmland brings the
     * counter back down by one per tick and the plant stands up; the wilting is not final before the third tick.
     */
    @GameTest(template = FLOOR, batch = "climate_underground", timeoutTicks = TIMEOUT)
    public static void aDroopingPlantRecoversWhenTheStressGoes(GameTestHelper helper) {
        underground(helper, Biomes.DESERT, List.of(A), level -> {
            BlockPos a = helper.absolutePos(A);
            Genome g = ClimateKit.genome(912, ClimateKit.PURPLE, 0, 0);
            ClimateKit.plant(level, a, 1, g, Passport.PLAYER, 0, 3);
            RandomSource r = RandomSource.create(912);
            denyingGrowth(List.of(a), () -> {
                ClimateKit.tick(level, a, r, 2);
                helper.assertTrue(ClimateKit.strain(level, a) == 1, "drooping after 2 ticks");
                ClimateKit.farmland(level, a, 7); // the irrigation arrives
                helper.assertTrue(!read(level, a).r().atMargin(), "irrigated: " + read(level, a));
                ClimateKit.tick(level, a, r, 1);
                helper.assertTrue(ClimateKit.strain(level, a) == 1, "counter 1 is still drooping");
                ClimateKit.tick(level, a, r, 1);
                helper.assertTrue(ClimateKit.strain(level, a) == 0, "stands up after two ticks without stress");
                // and the stress comes back: the counter starts again, no leftover
                ClimateKit.farmland(level, a, 3);
                ClimateKit.tick(level, a, r, 2);
                helper.assertTrue(ClimateKit.strain(level, a) == 1, "two ticks of stress droop again");
            });
        });
    }

    /**
     * The grace counter lives in the block's {@code strain}, not in the block entity (nothing new is saved): a block
     * entity reloaded from its save while the plant droops (the chunk unloaded) continues from 1.
     */
    @GameTest(template = FLOOR, batch = "climate_underground", timeoutTicks = TIMEOUT)
    public static void theCounterSurvivesAChunkReload(GameTestHelper helper) {
        underground(helper, Biomes.DESERT, List.of(A), level -> {
            BlockPos a = helper.absolutePos(A);
            ClimateKit.plant(level, a, 1, ClimateKit.genome(913, ClimateKit.PURPLE, 0, 0), Passport.PLAYER, 0, 3);
            RandomSource r = RandomSource.create(913);
            denyingGrowth(List.of(a), () -> {
                ClimateKit.tick(level, a, r, 1);
                helper.assertTrue(ClimateKit.strain(level, a) == 1, "drooping");
                PlantBlockEntity reloaded = PlantFixtures.saveLoad((PlantBlockEntity) level.getBlockEntity(a));
                level.setBlockEntity(reloaded);
                ClimateKit.tick(level, a, r, 1);
                helper.assertTrue(ClimateKit.strain(level, a) == 1, "counter 2 after the reload");
                ClimateKit.tick(level, a, r, 1);
                helper.assertTrue(ClimateKit.strain(level, a) == FieldPlantBlock.WILTED, "wilted at the third tick in all");
            });
        });
    }

    // ------------------------------------------------------------------ the window (risk table: the starter patch, the gate)

    /**
     * 0.5 to 0.8 (the climate of the starter patch and of the M1a gate): no plant of the player ever has a stress
     * over zero or any strain, whatever its tolerance or colour, sheltered underground. The biomes are the ones
     * of the worldgen categories of the carrot.
     */
    @GameTest(template = FLOOR, batch = "climate_underground", timeoutTicks = TIMEOUT)
    public static void insideTheClimateWindowNothingDroops(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        List<ResourceKey<Biome>> biomes = List.of(Biomes.PLAINS, Biomes.SUNFLOWER_PLAINS, Biomes.FOREST, Biomes.FLOWER_FOREST,
                Biomes.MEADOW);
        List<BlockPos> cells = List.of(A, B);
        for (BlockPos c : cells) {
            PlantFixtures.litFarmland(helper, c);
        }
        PlantFixtures.whenLit(helper, cells, () -> ClimateKit.withWilting(true, () -> {
            for (ResourceKey<Biome> biome : biomes) {
                Runnable undo = ClimateKit.setBiome(level, helper.absolutePos(PlantFixtures.PLANT), 12, biome);
                try {
                    for (int[] colour : new int[][] {ClimateKit.WHITE, ClimateKit.PURPLE}) {
                        for (int tol : new int[] {0, 2}) {
                            BlockPos a = helper.absolutePos(A);
                            ClimateKit.plant(level, a, 1, ClimateKit.genome(920 + tol, colour, tol, tol), Passport.PLAYER, 0, 3);
                            double t = ClimateService.temperature(level, a);
                            helper.assertTrue(t >= 0.45 && t <= 0.85, biome.location() + " has temperature " + t);
                            RandomSource r = RandomSource.create(920);
                            denyingGrowth(List.of(a), () -> {
                                for (int i = 0; i < 30; i++) {
                                    ClimateKit.tick(level, a, r, 1);
                                    helper.assertTrue(ClimateKit.strain(level, a) == 0, biome.location() + ": strain at tick " + i);
                                }
                            });
                            Reading rd = read(level, a);
                            helper.assertTrue(rd.s() < 0 && rd.r().unprotectedStress() < 0, biome.location() + ": " + rd);
                        }
                    }
                } finally {
                    undo.run();
                }
            }
            helper.succeed();
        }), 0);
    }

    // ------------------------------------------------------------------ jungle and E (design §3.2)

    /**
     * The jungle's humid penalty and the protection are in the temperature that {@code E} sees (the environment of
     * the plant), not in {@code ClimateService}: T_eff = T_mid + (T + humid − T_mid)(1 − buffer); a species without
     * climate stress keeps the plain cell temperature.
     */
    @GameTest(template = FLOOR, batch = "climate_underground", timeoutTicks = TIMEOUT)
    public static void theEnvironmentSeesTheHumidJungleAndTheShelter(GameTestHelper helper) {
        underground(helper, Biomes.JUNGLE, List.of(A, B), level -> {
            BlockPos a = helper.absolutePos(A);
            BlockPos b = helper.absolutePos(B);
            Genome g = ClimateKit.genome(930, ClimateKit.WHITE, 1, 1);
            ClimateKit.plant(level, a, 1, g, Passport.PLAYER, 0, 3);
            ClimateKit.plant(level, b, 1, g, Passport.PLAYER, 0, 7);
            SpeciesDef def = def();
            ClimateStress cs = def.climateStress();
            double cell = ClimateService.temperature(level, a);
            helper.assertTrue(Math.abs(cell - 0.95) < 1e-6, "jungle temperature " + cell);
            double mid = ClimateStress.mid(def);
            Environment dry = GrowthLogic.environment(level, a, def);
            Environment wet = GrowthLogic.environment(level, b, def);
            helper.assertTrue(Math.abs(dry.temperature() - ClimateStress.effectiveTemperature(mid, cell, 0.40, 0.25)) < 1e-9,
                    "sheltered jungle: " + dry.temperature());
            helper.assertTrue(Math.abs(wet.temperature() - ClimateStress.effectiveTemperature(mid, cell, 0.40, 0.35)) < 1e-9,
                    "sheltered and irrigated jungle: " + wet.temperature());
            helper.assertTrue(GrowthLogic.environment(level, a).temperature() == cell, "the plain environment is unchanged");
            Reading ra = read(level, a);
            double raw = ClimateStress.stress(1.35, ClimateStress.limits(def, GrowthLogic.phenotype(
                    ((PlantBlockEntity) level.getBlockEntity(a)).live(level).orElseThrow(), Environment.neutral(mid)).orElseThrow())[0],
                    ClimateStress.limits(def, GrowthLogic.phenotype(((PlantBlockEntity) level.getBlockEntity(a)).live(level)
                            .orElseThrow(), Environment.neutral(mid)).orElseThrow())[1]);
            helper.assertTrue(Math.abs(ra.r().unprotectedStress() - raw) < 1e-4,
                    "unprotected stress counts the humid penalty: " + ra + " expected " + raw);
            helper.assertTrue(ra.s() < cs.wiltMargin(), "the shelter keeps the jungle below the margin: " + ra);
        });
    }

    // ------------------------------------------------------------------ WR-714: exemptions

    /**
     * Never wilt, however hot, dry and long: wild and village plants, a mother root, a seed (stage 0) and a ripe root
     * (stage 3); the control (a young plant of the player) wilts. {@code Wilting.exemption} names the reason.
     */
    @GameTest(template = FLOOR, batch = "climate_underground", timeoutTicks = TIMEOUT)
    public static void exemptClassesNeverWilt(GameTestHelper helper) {
        underground(helper, Biomes.DESERT, List.of(A), level -> {
            BlockPos a = helper.absolutePos(A);
            Genome g = ClimateKit.genome(940, ClimateKit.WHITE, 0, 0);
            Passport village = new Passport(Passport.Origin.VILLAGE, 0, "", false, false, "landrace", 0, 0);
            Passport wild = Passport.wild(12345L, "meadow", false, false);
            Passport starter = Passport.wild(12345L, "meadow", false, true);
            record Case(String name, Passport passport, int flags, int stage, String reason) {
            }
            List<Case> cases = List.of(
                    new Case("wild", wild, 0, 1, "origin"), new Case("starter patch", starter, 0, 2, "origin"),
                    new Case("village", village, 0, 1, "origin"),
                    new Case("mother root", Passport.PLAYER, PlantState.MOTHER, 1, "mother"),
                    new Case("seed", Passport.PLAYER, 0, 0, "stage"), new Case("ripe root", Passport.PLAYER, 0, 3, "stage"),
                    new Case("control: young plant of the player", Passport.PLAYER, 0, 1, null));
            RandomSource r = RandomSource.create(940);
            for (Case c : cases) {
                PlantBlockEntity be = ClimateKit.plant(level, a, c.stage(), g, c.passport(), c.flags(), 3);
                String reason = Wilting.exemption(level, level.getBlockState(a), be.live(level).orElseThrow());
                helper.assertTrue(java.util.Objects.equals(reason, c.reason()), c.name() + ": exemption " + reason);
                int[] max = {0};
                denyingGrowth(List.of(a), () -> {
                    for (int i = 0; i < 30; i++) {
                        ClimateKit.tick(level, a, r, 1);
                        max[0] = Math.max(max[0], ClimateKit.strain(level, a));
                    }
                });
                if (c.reason() == null) {
                    helper.assertTrue(max[0] == FieldPlantBlock.WILTED, c.name() + " did not wilt: " + read(level, a));
                } else {
                    helper.assertTrue(max[0] == 0, c.name() + " got strain " + max[0]);
                }
                level.setBlock(a, Blocks.AIR.defaultBlockState(), 2);
            }
        });
    }

    /** {@code climate.wilting = false}: nothing wilts, and a plant that wilted stands up and yields again. */
    @GameTest(template = FLOOR, batch = "climate_underground", timeoutTicks = TIMEOUT)
    public static void switchedOffNothingWiltsAndAWiltedPlantStandsUp(GameTestHelper helper) {
        underground(helper, Biomes.DESERT, List.of(A, B), level -> {
            BlockPos a = helper.absolutePos(A);
            BlockPos b = helper.absolutePos(B);
            Genome g = ClimateKit.genome(950, ClimateKit.WHITE, 0, 0);
            ClimateKit.plant(level, a, 1, g, Passport.PLAYER, 0, 3);
            ClimateKit.plant(level, b, 1, g, Passport.PLAYER, 0, 3);
            Player player = helper.makeMockSurvivalPlayer();
            RandomSource r = RandomSource.create(950);
            denyingGrowth(List.of(a, b), () -> ClimateKit.tick(level, a, r, 3));
            helper.assertTrue(ClimateKit.strain(level, a) == FieldPlantBlock.WILTED, "on: a wilted");
            helper.assertTrue(ClimateKit.drops(level, a, player).isEmpty(), "on: a wilted plant drops nothing");
            ClimateKit.withWilting(false, () -> {
                helper.assertFalse(Wilting.wilted(level.getBlockState(a)), "off: a wilted state does not count");
                helper.assertFalse(ClimateKit.drops(level, a, player).isEmpty(), "off: the plant yields again");
                denyingGrowth(List.of(a, b), () -> {
                    for (int i = 0; i < 20; i++) {
                        ClimateKit.tick(level, b, r, 1);
                        helper.assertTrue(ClimateKit.strain(level, b) == 0, "off: b drooped at tick " + i);
                    }
                    ClimateKit.tick(level, a, r, 1);
                });
                helper.assertTrue(ClimateKit.strain(level, a) == 0, "off: the wilted plant stands up on its next tick");
            });
            // the stress itself is still there for E: the growth penalty remains with the flag off
            helper.assertTrue(read(level, b).r().atMargin(), "the stress is a fact of the cell, not of the flag: " + read(level, b));
        });
    }

    /**
     * A plant from before this version (no climate flag in its root) does not wilt for the first ten minutes after
     * its chunk loaded, then it is climate-aware and wilts like any other; a new plant has the flag from the start.
     */
    @GameTest(template = FLOOR, batch = "climate_underground", timeoutTicks = TIMEOUT)
    public static void aPlantOfAnOldSaveHasTenMinutesOfGrace(GameTestHelper helper) {
        underground(helper, Biomes.DESERT, List.of(A), level -> {
            BlockPos a = helper.absolutePos(A);
            PlantBlockEntity be = ClimateKit.plant(level, a, 1, ClimateKit.genome(960, ClimateKit.WHITE, 0, 0), Passport.PLAYER, 0, 3);
            helper.assertTrue(be.data().has(PlantState.CLIMATE_AWARE), "a new plant carries the flag");
            be.setPlantFlag(PlantState.CLIMATE_AWARE, false); // what a root written by an older version looks like
            RandomSource r = RandomSource.create(960);
            denyingGrowth(List.of(a), () -> {
                for (int i = 0; i < 60; i++) {
                    ClimateKit.tick(level, a, r, 1);
                    helper.assertTrue(ClimateKit.strain(level, a) == 0, "an old plant drooped in its grace, tick " + i);
                }
                helper.assertFalse(be.data().has(PlantState.CLIMATE_AWARE), "the flag is not set while the grace runs");
                Wilting.endGraceForTests(be);
                ClimateKit.tick(level, a, r, 3);
            });
            helper.assertTrue(be.data().has(PlantState.CLIMATE_AWARE), "the flag is set when the grace is over");
            helper.assertTrue(ClimateKit.strain(level, a) == FieldPlantBlock.WILTED, "wilted three ticks after the grace");
        });
    }

    // ------------------------------------------------------------------ WR-712: open sky, roof, irrigation

    /** Runs {@code body} when {@code ready} holds (at most 300 ticks; the light engine runs behind {@code setBlock}). */
    static void whenSky(GameTestHelper helper, BooleanSupplier ready, Runnable body, int waited, Supplier<String> what,
                        Runnable cleanup) {
        if (ready.getAsBoolean()) {
            body.run();
            return;
        }
        if (waited >= 300) {
            cleanup.run();
            helper.fail("sky light not as expected after " + waited + " ticks: " + what.get());
            return;
        }
        helper.runAfterDelay(1, () -> whenSky(helper, ready, body, waited + 1, what, cleanup));
    }

    /**
     * Under the open sky of a desert platform (y 250, about 0.21 below the biome's temperature) a purple carrot
     * without heat tolerance wilts bare and with irrigation alone (S 0.54 and 0.42); under a wide roof (sky light
     * 12 or less) it wilts neither dry nor irrigated (0.24 and 0.12). The roof is ordinary planks.
     */
    @GameTest(template = FLOOR, batch = "climate_sky_roof", timeoutTicks = TIMEOUT + 300)
    public static void aRoofAndIrrigationProtectUnderTheOpenSky(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        SkyPlatform sky = SkyPlatform.build(helper, 18);
        Runnable undoBiome = ClimateKit.setBiome(level, sky.center.above(), 30, Biomes.DESERT);
        BlockPos roofed = sky.center.offset(0, 1, 0);
        BlockPos roofedDry = sky.center.offset(2, 1, 0);
        BlockPos bareDry = sky.center.offset(14, 1, 0);
        BlockPos bareWet = sky.center.offset(14, 1, 4);
        List<BlockPos> cells = List.of(roofed, roofedDry, bareDry, bareWet);
        for (int dx = -6; dx <= 6; dx++) {
            for (int dz = -6; dz <= 6; dz++) {
                level.setBlock(sky.center.offset(dx, 4, dz), Blocks.OAK_PLANKS.defaultBlockState(), 2);
            }
        }
        // a crop needs raw light 8 to stay: block light under the roof, which does not touch the sky light
        level.setBlock(roofed.above(), Blocks.LIGHT.defaultBlockState(), 2);
        level.setBlock(roofedDry.above(), Blocks.LIGHT.defaultBlockState(), 2);
        Runnable finish = () -> {
            undoBiome.run();
            sky.fill(Blocks.AIR.defaultBlockState());
            sky.clear();
        };
        whenSky(helper, () -> level.getBrightness(LightLayer.SKY, roofed) <= 12
                && level.getBrightness(LightLayer.SKY, roofedDry) <= 12 && level.getBrightness(LightLayer.SKY, bareDry) >= 15
                && level.getBrightness(LightLayer.SKY, bareWet) >= 15, () -> {
            try {
                ClimateKit.withWilting(true, () -> {
                    Genome g = ClimateKit.genome(970, ClimateKit.PURPLE, 0, 0);
                    int[] moisture = {7, 3, 3, 7};
                    List<BlockPos> all = new ArrayList<>(cells);
                    for (int i = 0; i < cells.size(); i++) {
                        ClimateKit.plant(level, cells.get(i), 1, g, Passport.PLAYER, 0, moisture[i]);
                    }
                    Reading r0 = read(level, roofed);
                    Reading r1 = read(level, roofedDry);
                    Reading r2 = read(level, bareDry);
                    Reading r3 = read(level, bareWet);
                    helper.assertTrue(r0.r().sheltered() && r0.r().irrigated() && !r0.r().atMargin(), "roof + irrigation: " + r0);
                    helper.assertTrue(r1.r().sheltered() && !r1.r().irrigated() && !r1.r().atMargin(), "roof only: " + r1);
                    helper.assertTrue(!r2.r().sheltered() && !r2.r().irrigated() && r2.r().atMargin(), "bare: " + r2);
                    helper.assertTrue(!r3.r().sheltered() && r3.r().irrigated() && r3.r().atMargin(), "irrigation only: " + r3);
                    helper.assertTrue(r0.s() < r1.s() && r1.s() < r3.s() && r3.s() < r2.s(),
                            "the protections order: " + r0 + " | " + r1 + " | " + r3 + " | " + r2);
                    RandomSource r = RandomSource.create(970);
                    denyingGrowth(all, () -> {
                        for (int i = 0; i < 10; i++) {
                            all.forEach(c -> ClimateKit.tick(level, c, r, 1));
                        }
                    });
                    helper.assertTrue(ClimateKit.strain(level, roofed) == 0, "roof + irrigation wilted");
                    helper.assertTrue(ClimateKit.strain(level, roofedDry) == 0, "roof only wilted");
                    helper.assertTrue(ClimateKit.strain(level, bareDry) == FieldPlantBlock.WILTED, "bare did not wilt");
                    helper.assertTrue(ClimateKit.strain(level, bareWet) == FieldPlantBlock.WILTED, "irrigation only did not wilt");
                });
            } finally {
                finish.run();
            }
            helper.succeed();
        }, 0, () -> "sky light roofed " + level.getBrightness(LightLayer.SKY, roofed) + ", roofed dry "
                + level.getBrightness(LightLayer.SKY, roofedDry) + ", bare " + level.getBrightness(LightLayer.SKY, bareDry)
                + ", bare wet " + level.getBrightness(LightLayer.SKY, bareWet), finish);
    }

    // ------------------------------------------------------------------ WR-714: starter patch

    /**
     * Starter patches (three origins, so three patch seeds) in the desert: no plant of a patch ever droops, however
     * often it ticks, and its passport says why ({@code starter}). A plant of the player in the open desert next to a
     * patch that was placed outside the window ({@code StarterPatch.Data}) does not wilt either, within 96 blocks;
     * with a patch centre farther away, or one placed inside the window, it wilts.
     */
    @GameTest(template = FLOOR, batch = "climate_sky_starter", timeoutTicks = TIMEOUT + 300)
    public static void theStarterPatchNeverWiltsAndShieldsItsNeighbourhood(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        SkyPlatform sky = SkyPlatform.build(helper, 18);
        Runnable undoBiome = ClimateKit.setBiome(level, sky.center.above(), 30, Biomes.DESERT);
        Runnable cleanup = () -> {
            undoBiome.run();
            sky.fill(Blocks.AIR.defaultBlockState());
            sky.clear();
        };
        whenSky(helper, sky::lightSettled, () -> {
            net.minecraft.world.level.storage.DimensionDataStorage storage = level.getServer().overworld().getDataStorage();
            StarterPatch.Data before = StarterPatch.data(level.getServer()).orElse(null);
            try {
                ClimateKit.withWilting(true, () -> starter(helper, level, sky, storage));
            } finally {
                storage.set(StarterPatch.DATA, before == null ? new StarterPatch.Data() : before);
                undoBiome.run();
                sky.fill(Blocks.AIR.defaultBlockState());
                sky.clear();
            }
            helper.succeed();
        }, 0, () -> "the platform's sky light is not back", cleanup);
    }

    private static void starter(GameTestHelper helper, ServerLevel level, SkyPlatform sky,
                                net.minecraft.world.level.storage.DimensionDataStorage storage) {
        List<BlockPos> origins = List.of(sky.surface().offset(-10, 0, -10), sky.surface().offset(10, 0, -10),
                sky.surface().offset(0, 0, 10));
        RandomSource r = RandomSource.create(980);
        int ticked = 0;
        for (BlockPos origin : origins) {
            WildPatchFeature.Result res = WildPatchGameTests.place(level, origin, WildPatchGameTests.carrotConfig(level),
                    new WildPatchFeature.Options(true, Optional.empty(), Optional.empty(), "meadow", 0, WildPatchFeature.Rich.NEVER));
            helper.assertTrue(res.placed() && res.plants().size() >= 5, "patch not placed at " + origin);
            for (BlockPos p : res.plants()) {
                BlockState s = level.getBlockState(p);
                if (!(s.getBlock() instanceof FieldPlantBlock)) {
                    continue;
                }
                PlantBlockEntity be = (PlantBlockEntity) level.getBlockEntity(p);
                helper.assertTrue(be.data().passport().origin() == Passport.Origin.WILD && be.data().passport().starter(),
                        "passport " + be.data().passport());
                for (int i = 0; i < 25; i++) {
                    level.getBlockState(p).randomTick(level, p, r);
                    ticked++;
                    helper.assertTrue(ClimateKit.strain(level, p) == 0, "a plant of the starter patch has strain "
                            + ClimateKit.strain(level, p) + " at " + p);
                }
            }
        }
        helper.assertTrue(ticked > 100, "only " + ticked + " ticks");
        // a player's plant in the open desert
        BlockPos spot = sky.surface().offset(14, 0, 14);
        Genome g = ClimateKit.genome(981, ClimateKit.WHITE, 0, 0);
        record Case(String name, StarterPatch.Data data, boolean wilts) {
        }
        StarterPatch.Data near = new StarterPatch.Data();
        near.setPatch(spot.offset(60, 0, -60), true); // 84.9 blocks away
        StarterPatch.Data far = new StarterPatch.Data();
        far.setPatch(spot.offset(80, 0, 80), true); // 113 blocks away
        StarterPatch.Data inside = new StarterPatch.Data();
        inside.setPatch(spot, false); // placed inside the window: no shield
        helper.assertTrue(near.exempts(spot) && !far.exempts(spot) && !inside.exempts(spot), "Data.exempts");
        for (Case c : List.of(new Case("near, outside the window", near, false), new Case("far", far, true),
                new Case("inside the window", inside, true), new Case("no patch at all", null, true))) {
            storage.set(StarterPatch.DATA, c.data() == null ? new StarterPatch.Data() : c.data());
            ClimateKit.plant(level, spot, 1, g, Passport.PLAYER, 0, 3);
            int[] max = {0};
            denyingGrowth(List.of(spot), () -> {
                for (int i = 0; i < 10; i++) {
                    ClimateKit.tick(level, spot, r, 1);
                    max[0] = Math.max(max[0], ClimateKit.strain(level, spot));
                }
            });
            helper.assertTrue((max[0] == FieldPlantBlock.WILTED) == c.wilts(), c.name() + ": strain " + max[0] + " "
                    + read(level, spot));
            level.setBlock(spot, Blocks.AIR.defaultBlockState(), 2);
        }
        // placement records the shield: a patch in the desert is outside the window, one in the plains is not
        StarterPatch.Data placed = new StarterPatch.Data();
        StarterPatch.Placement p = StarterPatch.attempt(level, () -> placed, sky.surface().offset(-28, 0, 0),
                q -> Math.abs(q.getX() - sky.center.getX()) <= 17 && Math.abs(q.getZ() - sky.center.getZ()) <= 17
                        && q.getY() >= SkyPlatform.Y, StarterPatch.Trigger.START);
        helper.assertTrue(p.outcome().success(), "starter attempt " + p);
        helper.assertTrue(placed.exempts(p.origin().offset(50, 0, 0)), "a patch placed in the desert shields its surroundings");
        helper.assertFalse(placed.exempts(p.origin().offset(100, 0, 0)), "and not beyond 96 blocks");
        Runnable plains = ClimateKit.setBiome(level, sky.center.above(), 30, Biomes.PLAINS);
        try {
            StarterPatch.Data inPlains = new StarterPatch.Data();
            StarterPatch.attempt(level, () -> inPlains, sky.surface().offset(-28, 0, 0),
                    q -> Math.abs(q.getX() - sky.center.getX()) <= 17 && Math.abs(q.getZ() - sky.center.getZ()) <= 17
                            && q.getY() >= SkyPlatform.Y, StarterPatch.Trigger.START);
            helper.assertFalse(inPlains.exempts(p.origin()), "a patch placed in the window shields nothing");
        } finally {
            plains.run();
        }
    }

    /** The config flag is the one the tests switch: on by default in a real game (the GameTest server starts it off). */
    @GameTest(template = FLOOR)
    public static void theConfigDefaultIsOnAndTheGameTestServerStartsItOff(GameTestHelper helper) {
        helper.assertTrue(ServerConfig.CLIMATE_WILTING.getDefault(), "default on");
        helper.assertFalse(ServerConfig.value(ServerConfig.CLIMATE_WILTING), "the GameTest server overrides it to off");
        helper.succeed();
    }
}
