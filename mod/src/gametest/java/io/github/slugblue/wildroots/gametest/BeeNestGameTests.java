package io.github.slugblue.wildroots.gametest;

import io.github.slugblue.wildroots.WildRoots;
import io.github.slugblue.wildroots.worldgen.BeeNestConfig;
import io.github.slugblue.wildroots.worldgen.BeeNests;
import io.github.slugblue.wildroots.worldgen.WildPatchConfig;
import io.github.slugblue.wildroots.worldgen.WildPatchFeature;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.valueproviders.UniformInt;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BeehiveBlockEntity;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/** WR-151: bee nests at wild patches (worldgen §7, §8.8; GDD §4.3). */
@GameTestHolder(WildRoots.MOD_ID)
@PrefixGameTestTemplate(false)
public final class BeeNestGameTests {

    static final String FLOOR = SmokeGameTests.FLOOR_3X3X3;
    static final ResourceLocation LONE_BIRCH = new ResourceLocation("wildroots", "lone_birch_bees");
    static final WildPatchFeature.Options STARTER = new WildPatchFeature.Options(true, Optional.empty(),
            Optional.empty(), "meadow", 0);

    private BeeNestGameTests() {
    }

    /** The carrot patch config with a nest of chance 1.0 and {@code fallbacks}. */
    static WildPatchConfig withNest(ServerLevel level, List<String> fallbacks) {
        return withNest(level, fallbacks, 6, 16);
    }

    static WildPatchConfig withNest(ServerLevel level, List<String> fallbacks, int min, int max) {
        var lone = level.registryAccess().registryOrThrow(Registries.CONFIGURED_FEATURE)
                .getHolderOrThrow(ResourceKey.create(Registries.CONFIGURED_FEATURE, LONE_BIRCH));
        BeeNestConfig n = new BeeNestConfig(1.0f, 1.0f, min, max, UniformInt.of(2, 3), fallbacks,
                List.of("starter", "rich"), Optional.of(lone));
        return WildPatchGameTests.carrotConfig(level).withBeeNest(Optional.of(n));
    }

    static double horizontal(BlockPos a, BlockPos b) {
        double dx = a.getX() - b.getX();
        double dz = a.getZ() - b.getZ();
        return Math.sqrt(dx * dx + dz * dz);
    }

    static void assertNest(GameTestHelper helper, ServerLevel level, BlockPos pos, BlockPos origin) {
        helper.assertTrue(level.getBlockState(pos).is(Blocks.BEE_NEST), "no bee_nest at " + pos);
        double d = horizontal(pos, origin);
        helper.assertTrue(d >= 5 && d <= 16, "nest at distance " + d);
        BeehiveBlockEntity hive = (BeehiveBlockEntity) level.getBlockEntity(pos);
        helper.assertTrue(hive.getOccupantCount() >= 2 && hive.getOccupantCount() <= 3,
                "bees in the nest " + hive.getOccupantCount());
    }

    /** A tree in the ring: the nest hangs on its trunk, 6–16 blocks from the patch, with 2–3 bees. */
    @GameTest(template = FLOOR, batch = "wr151_sky_tree")
    public static void treeInTheRingGetsTheNest(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        SkyPlatform sky = SkyPlatform.build(helper, 18);
        try {
            BlockPos origin = sky.surface();
            List<BlockPos> trunks = new java.util.ArrayList<>();
            for (int k = 0; k < 8; k++) {
                double a = k * Math.PI / 4;
                BlockPos trunk = origin.offset((int) Math.round(9 * Math.cos(a)), 0, (int) Math.round(9 * Math.sin(a)));
                trunks.add(trunk);
                tree(level, trunk);
            }
            WildPatchFeature.Result r = WildPatchGameTests.place(level, origin,
                    withNest(level, List.of("tree"), 8, 10), WildPatchGameTests.TEST);
            helper.assertTrue(r.placed(), "patch not placed");
            helper.assertTrue(r.nest().kind() == BeeNests.Kind.TREE, "nest " + r.nest());
            assertNest(helper, level, r.nest().pos(), origin);
            BlockPos nest = r.nest().pos();
            helper.assertTrue(trunks.stream().anyMatch(t -> horizontal(nest, t) <= 1.01),
                    "nest not on a trunk: " + nest);
        } finally {
            sky.clear();
        }
        helper.succeed();
    }

    /**
     * Review M1: the patch origin on the last column of its chunk and trees
     * 16 blocks away on the far side. Every nest stays within 16 blocks and
     * inside the 3×3 chunks around the origin chunk (the write region of a
     * decoration step), and a refused write is never reported as a nest.
     */
    @GameTest(template = FLOOR, batch = "wr151_sky_edge")
    public static void nestStaysInTheWriteRegionAtAChunkEdge(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        SkyPlatform sky = SkyPlatform.build(helper, 26);
        try {
            BlockPos s0 = sky.surface();
            int shift = ((15 - (s0.getX() & 15) + 8) & 15) - 8;
            BlockPos origin = s0.offset(shift, 0, 0);
            helper.assertTrue((origin.getX() & 15) == 15, "origin not on the chunk edge");
            for (int dz = -4; dz <= 4; dz += 4) {
                tree(level, origin.offset(16, 0, dz));
            }
            WildPatchFeature.Result r = WildPatchGameTests.place(level, origin,
                    withNest(level, List.of("tree"), 14, 16), WildPatchGameTests.TEST);
            helper.assertTrue(r.placed(), "patch not placed");
            for (BlockPos q : BlockPos.betweenClosed(origin.offset(-24, -2, -24), origin.offset(24, 10, 24))) {
                if (level.getBlockState(q).is(Blocks.BEE_NEST)) {
                    helper.assertTrue(io.github.slugblue.wildroots.worldgen.WriteRegion.nestSpot(origin.getX(),
                            origin.getZ(), q.getX(), q.getZ()), "nest outside the write region at " + q);
                }
            }
            if (r.nest().kind() == BeeNests.Kind.TREE) {
                assertNest(helper, level, r.nest().pos(), origin);
            } else {
                helper.assertTrue(r.nest().kind() == BeeNests.Kind.NONE, "nest " + r.nest());
            }
        } finally {
            sky.clear();
        }
        helper.succeed();
    }

    /** An oak: 5 logs and a 5×5×3 cap of persistent leaves. */
    static void tree(ServerLevel level, BlockPos trunk) {
        for (int y = 0; y < 5; y++) {
            level.setBlock(trunk.above(y), Blocks.OAK_LOG.defaultBlockState(), 2);
        }
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                for (int y = 4; y <= 6; y++) {
                    BlockPos l = trunk.offset(dx, y, dz);
                    if (level.getBlockState(l).isAir()) {
                        level.setBlock(l, Blocks.OAK_LEAVES.defaultBlockState()
                                .setValue(net.minecraft.world.level.block.LeavesBlock.PERSISTENT, true), 2);
                    }
                }
            }
        }
    }

    /** No tree near a starter (or rich) patch: a lone birch with a nest, 6–12 blocks away. */
    @GameTest(template = FLOOR, batch = "wr151_sky_lone")
    public static void starterWithoutTreesGetsALoneBirch(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        SkyPlatform sky = SkyPlatform.build(helper, 18);
        try {
            BlockPos origin = sky.surface();
            WildPatchFeature.Result r = WildPatchGameTests.place(level, origin,
                    withNest(level, List.of("tree", "lone_tree")), STARTER);
            helper.assertTrue(r.placed(), "patch not placed");
            helper.assertTrue(r.nest().kind() == BeeNests.Kind.LONE_TREE, "nest " + r.nest());
            BlockPos tree = r.nest().pos();
            double d = horizontal(tree, origin);
            helper.assertTrue(d >= 5 && d <= 13, "lone tree at " + d);
            helper.assertTrue(level.getBlockState(tree).is(Blocks.BIRCH_LOG), "no birch at " + tree);
            BlockPos nest = null;
            for (BlockPos p : BlockPos.betweenClosed(tree.offset(-2, 0, -2), tree.offset(2, 8, 2))) {
                if (level.getBlockState(p).is(BlockTags.BEEHIVES)) {
                    nest = p.immutable();
                }
            }
            helper.assertTrue(nest != null && horizontal(nest, origin) <= 16, "lone birch has no nest within 16");
        } finally {
            sky.clear();
        }
        helper.succeed();
    }

    /** An ordinary patch never gets a lone tree; without a tree there is no nest (GDD §4.3). */
    @GameTest(template = FLOOR, batch = "wr151_sky_plain")
    public static void ordinaryPatchWithoutTreesHasNoNest(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        SkyPlatform sky = SkyPlatform.build(helper, 18);
        try {
            WildPatchFeature.Result r = WildPatchGameTests.place(level, sky.surface(),
                    withNest(level, List.of("tree", "lone_tree")), WildPatchGameTests.TEST);
            helper.assertTrue(r.placed(), "patch not placed");
            helper.assertTrue(r.nest().kind() == BeeNests.Kind.NONE, "nest " + r.nest());
        } finally {
            sky.clear();
        }
        helper.succeed();
    }

    /** A nest or hive already within 16 blocks counts: no second nest. */
    @GameTest(template = FLOOR, batch = "wr151_sky_existing")
    public static void existingHiveCounts(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        SkyPlatform sky = SkyPlatform.build(helper, 18);
        try {
            BlockPos origin = sky.surface();
            BlockPos hive = origin.offset(-7, 0, 3);
            level.setBlock(hive, Blocks.BEEHIVE.defaultBlockState(), 2);
            WildPatchFeature.Result r = WildPatchGameTests.place(level, origin,
                    withNest(level, List.of("tree", "lone_tree")), STARTER);
            helper.assertTrue(r.nest().kind() == BeeNests.Kind.EXISTING && hive.equals(r.nest().pos()),
                    "nest " + r.nest());
        } finally {
            sky.clear();
        }
        helper.succeed();
    }

    /** The carrot line ships the nest block: chance 0.6, rich 1.0, cascade tree → lone tree. */
    @GameTest(template = FLOOR)
    public static void carrotNestConfig(GameTestHelper helper) {
        BeeNestConfig n = WildPatchGameTests.carrotConfig(helper.getLevel()).beeNest().orElseThrow();
        helper.assertTrue(n.chance() == 0.6f && n.richChance() == 1.0f && n.minDistance() == 6
                && n.maxDistance() == 16 && n.fallbacks().equals(List.of("tree", "lone_tree"))
                && n.loneTreeFeature().isPresent(), "carrot nest " + n);
        helper.succeed();
    }
}
