package io.github.slugblue.wildroots.gametest;

import io.github.slugblue.wildroots.WildRoots;
import io.github.slugblue.wildroots.plant.PlantBlockEntity;
import io.github.slugblue.wildroots.worldgen.StarterPatch;
import io.github.slugblue.wildroots.worldgen.StarterPatchRules.Outcome;
import io.github.slugblue.wildroots.worldgen.WildPatchFeature;
import java.util.List;
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
     * On server start the starter step ran once. The GameTest world is kept
     * between runs, so after the first run it is an existing world: then no
     * data file and no patch (the log says so); on a new world the flag is
     * set. Either way the outcome matches the world data.
     */
    @GameTest(template = FLOOR)
    public static void starterStepRanOnStart(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        StarterPatch.Placement p = StarterPatch.last().orElseThrow(() -> new IllegalStateException("did not run"));
        org.slf4j.LoggerFactory.getLogger(StarterPatchGameTests.class).info("WR-152 starter on start: {}", p);
        boolean done = StarterPatch.data(level.getServer()).map(StarterPatch.Data::done).orElse(false);
        if (p.outcome() == Outcome.EXISTING_WORLD) {
            helper.assertTrue(StarterPatch.data(level.getServer()).isEmpty(), "existing world got the data file");
        } else {
            helper.assertTrue(done, "starter_patch_done not set after " + p);
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
            for (BlockPos plant : plants) {
                PlantBlockEntity be = (PlantBlockEntity) level.getBlockEntity(plant);
                helper.assertTrue(be.data().passport().starter() && !be.data().passport().rich(),
                        "passport " + be.data().passport());
            }
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
            helper.assertTrue(p.outcome() == Outcome.NOT_FOUND || p.outcome() == Outcome.MARKED_EXISTING,
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
