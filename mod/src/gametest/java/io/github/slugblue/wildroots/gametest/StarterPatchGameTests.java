package io.github.slugblue.wildroots.gametest;

import io.github.slugblue.wildroots.WildRoots;
import io.github.slugblue.wildroots.core.config.ServerConfig;
import io.github.slugblue.wildroots.plant.PlantBlockEntity;
import io.github.slugblue.wildroots.worldgen.StarterPatch;
import io.github.slugblue.wildroots.worldgen.StarterPatchRules;
import io.github.slugblue.wildroots.worldgen.StarterPatchRules.Outcome;
import io.github.slugblue.wildroots.worldgen.WildPatchFeature;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/** WR-152: the starter patch (worldgen §6; GDD §11.2). */
@GameTestHolder(WildRoots.MOD_ID)
@PrefixGameTestTemplate(false)
public final class StarterPatchGameTests {

    static final String FLOOR = SmokeGameTests.FLOOR_3X3X3;

    private StarterPatchGameTests() {
    }

    static boolean starter(ServerLevel level, BlockPos p) {
        return level.getBlockEntity(p) instanceof PlantBlockEntity be && be.data() != null
                && be.data().passport().starter();
    }

    /**
     * On server start the starter step ran once. A world reused from an
     * earlier run is an existing world: then no data file and no patch (the
     * log says so). A placed or marked patch sets the flag; a failed search
     * (NOT_FOUND / NOT_LOADED) counts an attempt and leaves the flag unset
     * (review 2, T1). Later tests may retry (login), so the flag is only
     * checked for a success here.
     */
    @GameTest(template = FLOOR)
    public static void starterStepRanOnStart(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        StarterPatch.Placement p = StarterPatch.atStart().orElseThrow(() -> new IllegalStateException("did not run"));
        LOG.info("WR-152 starter on start: {} ({})", p, p.survey());
        Optional<StarterPatch.Data> data = StarterPatch.data(level.getServer());
        if (p.outcome() == Outcome.EXISTING_WORLD) {
            helper.assertTrue(data.isEmpty(), "existing world got the data file");
        } else if (p.outcome().success()) {
            helper.assertTrue(data.map(StarterPatch.Data::done).orElse(false), "starter_patch_done not set after " + p);
        } else if (p.outcome().failure()) {
            helper.assertTrue(data.isPresent() && data.get().attempts() >= 1,
                    "a failed start attempt was not counted: " + p);
        }
        helper.succeed();
    }

    static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(StarterPatchGameTests.class);

    /**
     * Centres of the loaded area: every column of the ring 24-128 has its
     * 22-box loaded. Reads loaded chunks only.
     */
    static List<BlockPos> loadedCentres(ServerLevel level) {
        List<BlockPos> out = new ArrayList<>();
        BlockPos spawn = level.getSharedSpawnPos();
        for (int x = -1024; x <= 1024; x += 32) {
            for (int z = -1024; z <= 1024; z += 32) {
                BlockPos c = new BlockPos(spawn.getX() + x, 64, spawn.getZ() + z);
                boolean corners = true;
                for (int[] o : new int[][] {{0, 0}, {150, 150}, {-150, 150}, {150, -150}, {-150, -150}}) {
                    corners &= level.getChunkSource().getChunkNow((c.getX() + o[0]) >> 4, (c.getZ() + o[1]) >> 4)
                            != null;
                }
                if (corners && StarterPatch.loadedShare(level, c) >= 0.999) {
                    out.add(c);
                }
            }
        }
        return out;
    }

    /**
     * Review 2, T1: the real search (no area predicate, no platform) centred
     * on a fully loaded area of the real terrain places a patch or marks an
     * existing one whenever the survey accepted a column; it never loads a
     * chunk. Own batch: it writes into the terrain.
     */
    @GameTest(template = FLOOR, batch = "wr152_real_search", timeoutTicks = 400)
    public static void realSearchOnLoadedTerrainPlacesOrMarks(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        List<BlockPos> centres = loadedCentres(level);
        helper.assertTrue(!centres.isEmpty(), "no fully loaded area of the ring size");
        BlockPos best = null;
        StarterPatch.Survey bestSurvey = null;
        for (BlockPos c : centres.subList(0, Math.min(16, centres.size()))) {
            StarterPatch.Survey sv = StarterPatch.survey(level, c);
            if (bestSurvey == null || sv.accepted() > bestSurvey.accepted()) {
                best = c;
                bestSurvey = sv;
            }
        }
        int loaded = level.getChunkSource().getLoadedChunksCount();
        StarterPatch.Placement p = StarterPatch.place(level, best);
        LOG.info("WR-152 real search at {} ({} loaded centres): {}; survey {}", best, centres.size(), p, bestSurvey);
        helper.assertTrue(level.getChunkSource().getLoadedChunksCount() == loaded, "the real search loaded chunks");
        helper.assertTrue(bestSurvey.unloaded() == 0, "loaded centre with unloaded columns: " + bestSurvey);
        if (bestSurvey.accepted() > 0) {
            helper.assertTrue(p.outcome().success(), "accepted columns but no patch: " + p + "; " + bestSurvey);
            helper.assertTrue(p.outcome() == Outcome.MARKED_EXISTING ? p.plants() >= 5
                    : p.plants() >= 10 && p.plants() <= 14, "plants " + p);
            List<List<BlockPos>> patches = StarterPatch.existingPatches(level, p.origin(), 0, 8);
            helper.assertTrue(!patches.isEmpty() && patches.get(0).stream().allMatch(q -> starter(level, q)),
                    "patch not starter: " + p);
        } else {
            helper.assertTrue(p.outcome() == Outcome.NOT_FOUND, "no accepted column but " + p);
        }
        helper.succeed();
    }

    /**
     * Review 2, T1: a failed search does not set the flag. Each NOT_FOUND
     * counts an attempt (kept through NBT), the third sets the flag; then the
     * step is ALREADY_DONE. A fresh Data, so the world flag is not touched.
     */
    @GameTest(template = FLOOR)
    public static void failedAttemptsCountAndTheThirdGivesUp(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        List<BlockPos> centres = loadedCentres(level);
        helper.assertTrue(!centres.isEmpty(), "no fully loaded area");
        BlockPos centre = centres.get(0);
        StarterPatch.Data[] data = {new StarterPatch.Data()};
        for (int i = 1; i <= 3; i++) {
            StarterPatch.Placement p = StarterPatch.attempt(level, () -> data[0], centre, q -> false,
                    StarterPatch.Trigger.START);
            helper.assertTrue(p.outcome() == Outcome.NOT_FOUND, "attempt " + i + ": " + p);
            helper.assertTrue(p.survey().columns() > 0 && p.survey().unloaded() == 0, "survey " + p.survey());
            helper.assertTrue(data[0].attempts() == i, "attempts " + data[0].attempts() + " after " + i);
            helper.assertTrue(data[0].done() == (i == 3), "done " + data[0].done() + " after attempt " + i);
            helper.assertTrue(data[0].isDirty(), "the counter is not saved");
            data[0] = data[0].reloaded();
            helper.assertTrue(data[0].attempts() == i && data[0].done() == (i == 3), "NBT lost the counter");
        }
        helper.assertTrue(StarterPatchRules.decide(ServerConfig.StarterPatch.TRUE, true, data[0].done(), 99)
                == Outcome.ALREADY_DONE, "not done after three failures");
        helper.succeed();
    }

    /**
     * Review 2, T1: a centre far outside the loaded area is NOT_LOADED, counts
     * one attempt, leaves the flag unset and loads no chunk.
     */
    @GameTest(template = FLOOR)
    public static void unloadedCentreIsNotLoaded(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        int loaded = level.getChunkSource().getLoadedChunksCount();
        BlockPos far = new BlockPos(1_000_000, 64, -1_000_000);
        StarterPatch.Data data = new StarterPatch.Data();
        StarterPatch.Placement p = StarterPatch.attempt(level, () -> data, far, q -> true, StarterPatch.Trigger.LOGIN);
        helper.assertTrue(level.getChunkSource().getLoadedChunksCount() == loaded, "chunks were loaded");
        helper.assertTrue(p.outcome() == Outcome.NOT_LOADED, "outcome " + p);
        helper.assertTrue(p.survey().unloaded() == p.survey().columns() && p.survey().columns() > 0,
                "survey " + p.survey());
        helper.assertTrue(data.attempts() == 1 && !data.done(), "attempts " + data.attempts());
        helper.assertTrue(StarterPatch.loadedShare(level, far) == 0, "far centre counted as loaded");
        helper.succeed();
    }

    /**
     * Review 2, T1: the login retry runs only for a world whose flag is not
     * set, once per server run, and only with the spawn ring loaded. In the
     * harness the start attempt often fails (the spawn moves after the spawn
     * area is prepared), so this is the real recovery path. Own batch: it
     * may place the starter patch of the world.
     */
    @GameTest(template = FLOOR, batch = "wr152_login_retry", timeoutTicks = 400)
    public static void loginRetryRunsOncePerServerRun(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Optional<StarterPatch.Data> before = StarterPatch.data(level.getServer());
        boolean pending = before.isPresent() && !before.get().done();
        int attemptsBefore = before.map(StarterPatch.Data::attempts).orElse(0);
        double share = StarterPatch.loadedShare(level, level.getSharedSpawnPos());
        Optional<StarterPatch.Placement> first = StarterPatch.retryOnLogin(level.getServer());
        Optional<StarterPatch.Placement> second = StarterPatch.retryOnLogin(level.getServer());
        LOG.info("WR-152 login retry: pending {}, spawn ring loaded {}, first {}, second {}", pending, share, first,
                second);
        helper.assertTrue(second.isEmpty(), "second login retry in one run: " + second);
        if (!pending) {
            helper.assertTrue(first.isEmpty(), "retry without a failed attempt: " + first);
        } else if (share >= StarterPatchRules.MIN_LOADED_SHARE) {
            helper.assertTrue(first.isPresent() && (first.get().outcome().success()
                    || first.get().outcome().failure()), "login retry did not search: " + first);
            StarterPatch.Data after = StarterPatch.data(level.getServer()).orElseThrow();
            helper.assertTrue(first.get().outcome().success() ? after.done()
                    : after.attempts() == attemptsBefore + 1, "bookkeeping after " + first);
        } else {
            helper.assertTrue(first.isEmpty(), "retried with the spawn ring unloaded");
        }
        helper.succeed();
    }

    /**
     * Review 2, T6: fallback 2 makes a 5x5 stone pad grass; when the patch
     * then does not fit (torches over every cell, no spot), the pad is stone
     * again and the torches stay.
     */
    @GameTest(template = FLOOR, batch = "wr152_sky_pad")
    public static void failedFallbackPadIsRestored(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        SkyPlatform sky = SkyPlatform.build(helper, 2);
        try {
            sky.fill(Blocks.STONE.defaultBlockState());
            for (int dx = -2; dx <= 2; dx++) {
                for (int dz = -2; dz <= 2; dz++) {
                    level.setBlock(sky.center.offset(dx, 1, dz), Blocks.TORCH.defaultBlockState(), 2);
                }
            }
            BlockPos centre = sky.center.offset(-28, 1, 0);
            BlockPos only = sky.center;
            StarterPatch.Placement p = StarterPatch.place(level, centre, q -> q.equals(only));
            helper.assertTrue(p.outcome().failure(), "outcome " + p);
            for (int dx = -2; dx <= 2; dx++) {
                for (int dz = -2; dz <= 2; dz++) {
                    helper.assertTrue(level.getBlockState(sky.center.offset(dx, 0, dz)).is(Blocks.STONE),
                            "pad not restored at " + dx + "," + dz + ": "
                                    + level.getBlockState(sky.center.offset(dx, 0, dz)));
                    helper.assertTrue(level.getBlockState(sky.center.offset(dx, 1, dz)).is(Blocks.TORCH),
                            "torch gone at " + dx + "," + dz);
                }
            }
            // the pad itself was made: the same call without the failure turns the 5x5 into grass
            List<java.util.Map.Entry<BlockPos, net.minecraft.world.level.block.state.BlockState>> changed =
                    StarterPatch.grassPad(level, sky.center);
            helper.assertTrue(changed.size() == 25 && level.getBlockState(sky.center).is(Blocks.GRASS_BLOCK),
                    "grassPad changed " + changed.size());
        } finally {
            sky.clear();
        }
        helper.succeed();
    }

    /**
     * The placement itself (search restricted to a sky platform, no patch
     * there yet): 10–14 starter plants in the ring 24–96, never rich, a nest
     * within 16 blocks (a lone birch: there are no trees).
     */
    @GameTest(template = FLOOR, batch = "wr152_sky_place")
    public static void starterPatchPlacement(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        SkyPlatform sky = SkyPlatform.build(helper, 16);
        try {
            BlockPos center = sky.center.offset(-28, 1, 0);
            StarterPatch.Placement p = StarterPatch.place(level, center,
                    q -> Math.abs(q.getX() - sky.center.getX()) <= 13 && Math.abs(q.getZ() - sky.center.getZ()) <= 13
                            && q.getY() >= SkyPlatform.Y);
            helper.assertTrue(p.outcome() == Outcome.PLACED || p.outcome() == Outcome.FALLBACK, "outcome " + p);
            double d = Math.sqrt(Math.pow(p.origin().getX() - center.getX(), 2)
                    + Math.pow(p.origin().getZ() - center.getZ(), 2));
            helper.assertTrue(d >= 24 && d <= 96, "patch at distance " + d);
            helper.assertTrue(p.plants() >= 10 && p.plants() <= 14, "plants " + p.plants());
            List<BlockPos> plants = StarterPatch.existingPatches(level, p.origin(), 0, 8).get(0);
            int[] byStage = new int[6];
            for (BlockPos plant : plants) {
                PlantBlockEntity be = (PlantBlockEntity) level.getBlockEntity(plant);
                helper.assertTrue(be.data().passport().starter() && !be.data().passport().rich(),
                        "passport " + be.data().passport());
                byStage[level.getBlockState(plant).getValue(
                        io.github.slugblue.wildroots.plant.FieldPlantBlock.STAGE)]++;
            }
            // WR-152: stages 0.45 mature / 0.40 flowering / 0.15 seeding (batch 4 coverage gap)
            int n = plants.size();
            helper.assertTrue(n == p.plants(), "plants found " + n + " vs placed " + p.plants());
            helper.assertTrue(byStage[4] == Math.round(n * 0.40f) && byStage[5] == Math.round(n * 0.15f)
                    && byStage[3] == n - byStage[4] - byStage[5], "stages " + java.util.Arrays.toString(byStage)
                    + " of " + n);
            boolean nest = false;
            for (BlockPos q : BlockPos.betweenClosed(p.origin().offset(-16, -1, -16), p.origin().offset(16, 12, 16))) {
                if (level.getBlockState(q).is(BlockTags.BEEHIVES)) {
                    nest = true;
                    break;
                }
            }
            helper.assertTrue(nest, "no nest within 16 of the starter patch");
        } finally {
            sky.clear();
        }
        helper.succeed();
    }

    /**
     * An existing patch of ≥ 5 plants in the ring 24–128: no new patch is
     * placed, the existing one is marked {@code starter}.
     */
    @GameTest(template = FLOOR, batch = "wr152_sky_existing")
    public static void existingPatchIsMarkedNotDoubled(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        SkyPlatform sky = SkyPlatform.build(helper, 14);
        try {
            BlockPos origin = sky.surface();
            WildPatchFeature.Result r = WildPatchGameTests.place(level, origin, WildPatchGameTests.carrotConfig(level),
                    WildPatchGameTests.TEST);
            helper.assertTrue(r.placed() && r.plants().size() >= 5, "test patch not placed");
            BlockPos center = origin.offset(-30, 0, 0);
            int before = count(StarterPatch.existingPatches(level, center, 24, 128));
            StarterPatch.Placement p = StarterPatch.place(level, center);
            helper.assertTrue(p.outcome() == Outcome.MARKED_EXISTING, "outcome " + p);
            int after = count(StarterPatch.existingPatches(level, center, 24, 128));
            helper.assertTrue(before == after, "a new patch was placed: " + before + " -> " + after);
            List<BlockPos> marked = StarterPatch.existingPatches(level, p.origin(), 0, 8).get(0);
            helper.assertTrue(marked.size() >= 5 && marked.stream().allMatch(q -> starter(level, q)),
                    "existing patch not marked starter");
        } finally {
            sky.clear();
        }
        helper.succeed();
    }

    /**
     * Batch 5 flake (seed-dependent, {@code realSearchOnLoadedTerrainPlacesOrMarks}):
     * an existing patch on the inner edge of the 24–128 ring was marked only
     * where its plants were 24 or more from spawn; the plants nearer spawn
     * stayed plain wild. The whole patch (same patch origin) is marked.
     */
    @GameTest(template = FLOOR, batch = "wr152_sky_existing_edge")
    public static void existingPatchOnTheInnerRingEdgeIsMarkedWhole(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        SkyPlatform sky = SkyPlatform.build(helper, 14);
        try {
            BlockPos origin = sky.surface();
            WildPatchFeature.Result r = WildPatchGameTests.place(level, origin, WildPatchGameTests.carrotConfig(level),
                    WildPatchGameTests.TEST);
            helper.assertTrue(r.placed() && r.plants().size() >= 5, "test patch not placed");
            // spawn so that the ring edge (24 = RING_MIN) runs through the patch: some plants nearer than 24,
            // at least 5 (MIN_EXISTING) farther; the patch shape depends on the world seed, so the offset is searched
            BlockPos center = null;
            String tried = "";
            for (int dx = -14; dx >= -40 && center == null; dx--) {
                BlockPos c = origin.offset(dx, 0, 0);
                long inner = r.plants().stream().filter(q -> q.distSqr(new BlockPos(c.getX(), q.getY(), c.getZ()))
                        < 24L * 24).count();
                long outer = r.plants().stream().filter(q -> q.distSqr(new BlockPos(c.getX(), q.getY(), c.getZ()))
                        <= 128L * 128).count() - inner;
                tried += " " + dx + ":" + inner + "/" + outer;
                if (inner > 0 && outer >= 5) {
                    center = c;
                }
            }
            helper.assertTrue(center != null, "no spawn makes the patch straddle the ring edge:" + tried);
            StarterPatch.Placement p = StarterPatch.place(level, center);
            helper.assertTrue(p.outcome() == Outcome.MARKED_EXISTING, "outcome " + p);
            List<BlockPos> unmarked = r.plants().stream().filter(q -> !starter(level, q)).toList();
            helper.assertTrue(unmarked.isEmpty(), unmarked.size() + " of " + r.plants().size()
                    + " plants of the marked patch are not starter: " + unmarked);
            helper.assertTrue(p.plants() == r.plants().size(), "plants " + p.plants() + " of " + r.plants().size());
        } finally {
            sky.clear();
        }
        helper.succeed();
    }

    /**
     * Review M2: near the edge of the loaded area the starter search must
     * not load a chunk. A platform two chunks inside the edge: its columns
     * are refused (the nest area would leave the loaded chunks), nothing is
     * placed, the platform is untouched and the loaded chunk count is the same.
     */
    @GameTest(template = FLOOR, batch = "wr152_sky_edge")
    public static void starterSearchNeverLoadsChunksAtTheEdge(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos start = helper.absolutePos(new BlockPos(8, 0, 8)).atY(SkyPlatform.Y);
        BlockPos edge = null;
        for (net.minecraft.core.Direction dir : net.minecraft.core.Direction.Plane.HORIZONTAL) {
            BlockPos p = start;
            for (int i = 0; i < 64 && edge == null; i++) {
                BlockPos next = p.relative(dir, 16);
                if (level.getChunkSource().getChunkNow(next.getX() >> 4, next.getZ() >> 4) == null) {
                    // p is in the last loaded chunk in this direction; go back to its inner side
                    edge = p.relative(dir, -8);
                }
                p = next;
            }
            if (edge != null) {
                break;
            }
        }
        helper.assertTrue(edge != null, "no edge of the loaded area found");
        SkyPlatform sky = SkyPlatform.at(level, edge, 5);
        try {
            int loaded = level.getChunkSource().getLoadedChunksCount();
            BlockPos center = sky.center.offset(-28, 1, 0);
            SkyPlatform s = sky;
            StarterPatch.Placement p = StarterPatch.place(level, center,
                    q -> Math.abs(q.getX() - s.center.getX()) <= 3 && Math.abs(q.getZ() - s.center.getZ()) <= 3
                            && q.getY() >= SkyPlatform.Y);
            helper.assertTrue(level.getChunkSource().getLoadedChunksCount() == loaded,
                    "loaded chunks " + loaded + " -> " + level.getChunkSource().getLoadedChunksCount());
            helper.assertTrue(p.outcome().failure() || p.outcome() == Outcome.MARKED_EXISTING,
                    "placed at the edge of the loaded area: " + p);
            helper.assertTrue(level.getBlockState(sky.center).is(Blocks.GRASS_BLOCK)
                    && level.getBlockState(sky.center.above()).isAir(), "platform changed");
        } finally {
            sky.clear();
        }
        helper.succeed();
    }

    static int count(List<List<BlockPos>> patches) {
        return patches.stream().mapToInt(List::size).sum();
    }

    /** Fallback 2 on snow: the 5×5 becomes podzol and the snow layer goes. */
    @GameTest(template = FLOOR, batch = "wr152_sky_snow")
    public static void snowPadBecomesPodzol(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        SkyPlatform sky = SkyPlatform.build(helper, 4);
        try {
            sky.fill(Blocks.DIRT.defaultBlockState());
            for (int dx = -4; dx <= 4; dx++) {
                for (int dz = -4; dz <= 4; dz++) {
                    level.setBlock(sky.center.offset(dx, 1, dz), Blocks.SNOW.defaultBlockState(), 2);
                }
            }
            StarterPatch.grassPad(level, sky.center);
            for (int dx = -2; dx <= 2; dx++) {
                for (int dz = -2; dz <= 2; dz++) {
                    helper.assertTrue(level.getBlockState(sky.center.offset(dx, 0, dz)).is(Blocks.PODZOL),
                            "not podzol at " + dx + "," + dz);
                    helper.assertTrue(level.getBlockState(sky.center.offset(dx, 1, dz)).isAir(), "snow left");
                }
            }
            helper.assertTrue(level.getBlockState(sky.center.offset(3, 1, 0)).is(Blocks.SNOW), "pad wider than 5×5");
        } finally {
            sky.clear();
        }
        helper.succeed();
    }
}
