package io.github.slugblue.wildroots.gametest;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A flat grass platform high above the test area (WR-150…153). GameTests
 * run underground (y = -60 of a normal world), but wild patches need the
 * open sky; the chunks above the test area are loaded, and nothing is
 * generated at y = {@value #Y}. Tests that build one run in their own batch,
 * because the platform is wider than the test template.
 */
final class SkyPlatform {

    static final int Y = 250;
    static final int HEIGHT = 16;

    final ServerLevel level;
    final BlockPos center;
    final int radius;

    private SkyPlatform(ServerLevel level, BlockPos center, int radius) {
        this.level = level;
        this.center = center;
        this.radius = radius;
    }

    /** Grass {@code (2r+1)²} at y {@value #Y} centred over the test area, air above. */
    static SkyPlatform build(GameTestHelper helper, int radius) {
        BlockPos c = helper.absolutePos(new BlockPos(8, 0, 8)).atY(Y);
        SkyPlatform p = new SkyPlatform(helper.getLevel(), c, radius);
        p.fill(Blocks.GRASS_BLOCK.defaultBlockState());
        return p;
    }

    /** The first air block above the centre (what the heightmap placement gives). */
    BlockPos surface() {
        return center.above();
    }

    void fill(BlockState floor) {
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                level.setBlock(center.offset(dx, 0, dz), floor, 2);
                for (int dy = 1; dy <= HEIGHT; dy++) {
                    BlockPos p = center.offset(dx, dy, dz);
                    if (!level.getBlockState(p).isAir()) {
                        level.setBlock(p, Blocks.AIR.defaultBlockState(), 2);
                    }
                }
            }
        }
    }

    /** Everything back to air. */
    void clear() {
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                for (int dy = HEIGHT; dy >= 0; dy--) {
                    BlockPos p = center.offset(dx, dy, dz);
                    if (!level.getBlockState(p).isAir()) {
                        level.setBlock(p, Blocks.AIR.defaultBlockState(), 2);
                    }
                }
            }
        }
    }
}
