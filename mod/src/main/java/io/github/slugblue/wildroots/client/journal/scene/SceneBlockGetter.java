package io.github.slugblue.wildroots.client.journal.scene;

import net.minecraft.client.renderer.BiomeColors;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ColorResolver;
import net.minecraft.world.level.FoliageColor;
import net.minecraft.world.level.GrassColor;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LightChunk;
import net.minecraft.world.level.chunk.LightChunkGetter;
import net.minecraft.world.level.lighting.LevelLightEngine;
import net.minecraft.world.level.material.FluidState;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * The read-only "world" of a scene (journal v2 §5.8): an array of block
 * states the size of the structure and nothing else. No {@code Level},
 * no chunks, no ticks, no entities, no block entities
 * ({@link #getBlockEntity} is {@code null}); light is full and constant,
 * tint is the plains colours, shade is the overworld's. Outside the
 * structure everything is air.
 */
@OnlyIn(Dist.CLIENT)
final class SceneBlockGetter implements BlockAndTintGetter {

    /** Plains (temperature 0.8, downfall 0.4). */
    private static final double TEMPERATURE = 0.8;
    private static final double DOWNFALL = 0.4;
    private static final int WATER = 0x3F76E4;

    private static LevelLightEngine noLight;

    private final int sx;
    private final int sy;
    private final int sz;
    private final BlockState[] states;

    SceneBlockGetter(int sx, int sy, int sz, BlockState[] states) {
        this.sx = sx;
        this.sy = sy;
        this.sz = sz;
        this.states = states;
    }

    int sizeX() {
        return sx;
    }

    int sizeY() {
        return sy;
    }

    int sizeZ() {
        return sz;
    }

    @Override
    public BlockState getBlockState(BlockPos pos) {
        int x = pos.getX();
        int y = pos.getY();
        int z = pos.getZ();
        if (x < 0 || y < 0 || z < 0 || x >= sx || y >= sy || z >= sz) {
            return Blocks.AIR.defaultBlockState();
        }
        return states[(y * sz + z) * sx + x];
    }

    @Override
    public FluidState getFluidState(BlockPos pos) {
        return getBlockState(pos).getFluidState();
    }

    @Override
    public BlockEntity getBlockEntity(BlockPos pos) {
        return null;
    }

    @Override
    public int getHeight() {
        return sy;
    }

    @Override
    public int getMinBuildHeight() {
        return 0;
    }

    @Override
    public float getShade(Direction direction, boolean shade) {
        if (!shade) {
            return 1.0f;
        }
        return switch (direction) {
            case DOWN -> 0.5f;
            case UP -> 1.0f;
            case NORTH, SOUTH -> 0.8f;
            case WEST, EAST -> 0.6f;
        };
    }

    @Override
    public int getBrightness(LightLayer layer, BlockPos pos) {
        return 15;
    }

    @Override
    public int getRawBrightness(BlockPos pos, int darkening) {
        return 15;
    }

    @Override
    public boolean canSeeSky(BlockPos pos) {
        return true;
    }

    /** An engine with neither block nor sky light; only reached by code that bypasses the overrides above. */
    @Override
    public LevelLightEngine getLightEngine() {
        if (noLight == null) {
            noLight = new LevelLightEngine(new LightChunkGetter() {
                @Override
                public LightChunk getChunkForLighting(int x, int z) {
                    return null;
                }

                @Override
                public BlockGetter getLevel() {
                    return net.minecraft.world.level.EmptyBlockGetter.INSTANCE;
                }
            }, false, false);
        }
        return noLight;
    }

    @Override
    public int getBlockTint(BlockPos pos, ColorResolver resolver) {
        if (resolver == BiomeColors.FOLIAGE_COLOR_RESOLVER) {
            return FoliageColor.get(TEMPERATURE, DOWNFALL);
        }
        if (resolver == BiomeColors.WATER_COLOR_RESOLVER) {
            return WATER;
        }
        return GrassColor.get(TEMPERATURE, DOWNFALL);
    }
}
