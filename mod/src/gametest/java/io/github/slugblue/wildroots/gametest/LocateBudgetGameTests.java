package io.github.slugblue.wildroots.gametest;

import io.github.slugblue.wildroots.WildRoots;
import io.github.slugblue.wildroots.genetics.api.SpeciesDef;
import io.github.slugblue.wildroots.worldgen.ChunkBand;
import io.github.slugblue.wildroots.worldgen.ChunkBandPlacement;
import io.github.slugblue.wildroots.worldgen.LocatePatchCommand;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BiomeTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.biome.Biome;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * WR-154, review 2 T3: {@code /wildroots locate_patch} is the Euclidean
 * nearest band chunk, its worst case is bounded by the sample budget, and
 * the radius arguments are validated at their edges.
 */
@GameTestHolder(WildRoots.MOD_ID)
@PrefixGameTestTemplate(false)
public final class LocateBudgetGameTests {

    static final String FLOOR = SmokeGameTests.FLOOR_3X3X3;
    static final String CARROT = Batch4BoundaryGameTests.CARROT;
    static final Logger LOG = LoggerFactory.getLogger(LocateBudgetGameTests.class);

    private LocateBudgetGameTests() {
    }

    static SpeciesDef carrot() {
        return Batch4BoundaryGameTests.carrot();
    }

    /**
     * The search is the Euclidean nearest band chunk: with a tag every
     * overworld biome is in and a wide tolerance (the biome and climate never
     * refuse), the result equals the nearest band chunk found by brute force
     * over every chunk of the radius. Pure noise: no chunk is loaded.
     */
    @GameTest(template = FLOOR, timeoutTicks = 400)
    public static void locateFindsTheBruteForceNearest(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        int loaded = level.getChunkSource().getLoadedChunksCount();
        TagKey<Biome> all = BiomeTags.IS_OVERWORLD;
        long seed = level.getSeed();
        int radius = 1024;
        for (BlockPos from : List.of(level.getSharedSpawnPos(), new BlockPos(-3_001, 64, 7_777),
                new BlockPos(15, 64, -1))) {
            List<LocatePatchCommand.Band> bands = List.of(new LocatePatchCommand.Band(
                    new ChunkBandPlacement(0.0, 0.036), 2.0));
            LocatePatchCommand.Search s = LocatePatchCommand.search(level, from, carrot(), bands, radius, all);
            int best = Integer.MAX_VALUE;
            for (int cx = (from.getX() - radius) >> 4; cx <= (from.getX() + radius) >> 4; cx++) {
                for (int cz = (from.getZ() - radius) >> 4; cz <= (from.getZ() + radius) >> 4; cz++) {
                    if (!ChunkBand.candidate(seed, cx, cz, 0.0, 0.036)) {
                        continue;
                    }
                    int d = (int) Math.round(Math.sqrt(Math.pow((cx << 4) + 8 - from.getX(), 2)
                            + Math.pow((cz << 4) + 8 - from.getZ(), 2)));
                    if (d <= radius) {
                        best = Math.min(best, d);
                    }
                }
            }
            LOG.info("WR-154 brute force from {}: search {} brute {}", from, s, best);
            helper.assertTrue(best != Integer.MAX_VALUE, "no band chunk within 1024 by brute force");
            helper.assertTrue(s.found().isPresent() && s.found().get().distance() == best,
                    "search " + s + " vs brute force " + best);
            helper.assertTrue(ChunkBand.candidate(seed, s.found().get().chunkX(), s.found().get().chunkZ(), 0.0, 0.036),
                    "not a band chunk");
            helper.assertFalse(s.overBudget(), "budget hit on a 1024 search");
        }
        // every chunk a candidate: the answer is a chunk around the start, at most a chunk diagonal away
        LocatePatchCommand.Search near = LocatePatchCommand.search(level, new BlockPos(100, 64, 100), carrot(),
                List.of(new LocatePatchCommand.Band(new ChunkBandPlacement(0.0, 1.0), 2.0)), 16, all);
        helper.assertTrue(near.found().isPresent() && near.found().get().distance() <= 12, "near " + near);
        helper.assertTrue(level.getChunkSource().getLoadedChunksCount() == loaded, "the search loaded chunks");
        helper.succeed();
    }

    /**
     * Review 2, T3: the 6400-block not-found worst case (a tag no biome is
     * in) is bounded: the cheap biome pre-filter refuses every band hit, so
     * no surface height is computed, and the samples stay within the budget.
     * The time is logged for the report.
     */
    @GameTest(template = FLOOR, batch = "wr154_locate_budget", timeoutTicks = 2000)
    public static void locateWorstCaseIsBounded(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        TagKey<Biome> none = TagKey.create(Registries.BIOME, new ResourceLocation("wildroots", "spawns/no_biome_at_all"));
        List<LocatePatchCommand.Band> bands = LocatePatchCommand.bands(level, CARROT);
        long t0 = System.nanoTime();
        LocatePatchCommand.Search s = LocatePatchCommand.search(level, level.getSharedSpawnPos(), carrot(), bands,
                LocatePatchCommand.MAX_RADIUS, none);
        long ms = (System.nanoTime() - t0) / 1_000_000;
        LOG.info("WR-154 T3 after: 6400 not found {} ms ({})", ms, s);
        helper.assertTrue(s.found().isEmpty(), "found in an empty tag");
        helper.assertTrue(s.heights() == 0, "surface heights computed for refused biomes: " + s.heights());
        helper.assertTrue(s.samples() <= LocatePatchCommand.SAMPLE_BUDGET, "samples " + s.samples());
        helper.assertTrue(s.bandHits() > 0, "no band hits");
        // every chunk a band chunk: the walk stops on the sample budget and says how far it came
        LocatePatchCommand.Search over = LocatePatchCommand.search(level, level.getSharedSpawnPos(), carrot(),
                List.of(new LocatePatchCommand.Band(new ChunkBandPlacement(0.0, 1.0), 0.0)),
                LocatePatchCommand.MAX_RADIUS, none);
        LOG.info("WR-154 T3 over budget: {}", over);
        helper.assertTrue(over.overBudget() && over.found().isEmpty(), "not over budget: " + over);
        helper.assertTrue(over.samples() == LocatePatchCommand.SAMPLE_BUDGET, "samples " + over.samples());
        helper.assertTrue(over.searched() < LocatePatchCommand.MAX_RADIUS, "searched " + over.searched());
        // the real carrot tag at the maximum radius ends within the budget as well
        long t1 = System.nanoTime();
        LocatePatchCommand.Search real = LocatePatchCommand.search(level, new BlockPos(5_000_000, 64, 5_000_000),
                carrot(), bands, LocatePatchCommand.MAX_RADIUS,
                TagKey.create(Registries.BIOME, new ResourceLocation("wildroots", "spawns/wild_carrot")));
        LOG.info("WR-154 T3 after: 6400 real tag far away {} ms ({})", (System.nanoTime() - t1) / 1_000_000, real);
        helper.assertTrue(real.heights() <= LocatePatchCommand.HEIGHT_BUDGET, "heights " + real.heights());
        helper.succeed();
    }

    /**
     * Command arguments: radius 0 and 15 and 6401 are refused by the parser
     * (no search, a message); 16 and 6400 run and answer; op level 4 may run
     * it; an unknown species gets a message.
     */
    @GameTest(template = FLOOR, batch = "wr154_locate_args", timeoutTicks = 2000)
    public static void locateRadiusBoundaries(GameTestHelper helper) {
        LocatePatchGameTests.Recorder rec = new LocatePatchGameTests.Recorder();
        var src = LocatePatchGameTests.source(helper, rec, 4);
        for (int bad : new int[] {0, 15, 6401, -1}) {
            rec.lines.clear();
            helper.assertTrue(LocatePatchGameTests.run(helper, src, "wildroots locate_patch " + CARROT + " " + bad) == 0,
                    "radius " + bad + " ran");
            helper.assertFalse(rec.lines.isEmpty(), "no message for radius " + bad);
        }
        for (int ok : new int[] {16, 6400}) {
            rec.lines.clear();
            int r = LocatePatchGameTests.run(helper, src, "wildroots locate_patch " + CARROT + " " + ok);
            LOG.info("WR-154 radius {} -> {} {}", ok, r, rec.lines);
            helper.assertTrue(rec.lines.size() == 1, "radius " + ok + ": " + rec.lines);
            String line = rec.lines.get(0);
            helper.assertTrue(r > 0 ? line.contains("[") : !line.isEmpty(), "radius " + ok + ": " + line);
        }
        rec.lines.clear();
        helper.assertTrue(LocatePatchGameTests.run(helper, src, "wildroots locate_patch minecraft:carrots") == 0,
                "a vanilla block id is not a species");
        helper.assertTrue(rec.lines.stream().anyMatch(l -> l.contains("minecraft:carrots")), "no message " + rec.lines);
        helper.succeed();
    }
}
