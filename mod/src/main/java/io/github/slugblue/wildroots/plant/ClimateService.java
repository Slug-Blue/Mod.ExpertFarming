package io.github.slugblue.wildroots.plant;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.biome.Biome;

/**
 * Temperature of a cell (docs/05-architecture.md §11.8; GDD §3.5; S11): one
 * function for growth ({@code ServerLevel}) and worldgen
 * ({@code PlacementContext#getLevel()}), both {@link LevelReader}s.
 *
 * <p>It is {@code Biome#getHeightAdjustedTemperature} (SRG {@code m_47528_},
 * opened by the access transformer): the value vanilla caches in
 * {@code Biome#getTemperature} and uses for snow and ice. Above Y = 80 it is
 * {@code base − 0.00125·(y − 80)} plus noise of ±0.01; frozen biomes also
 * take noise into the base. No reflection anywhere (SEC-43).
 */
public final class ClimateService {

    private ClimateService() {
    }

    public static float temperature(LevelReader level, BlockPos pos) {
        return temperature(level.getBiome(pos), pos);
    }

    public static float temperature(Holder<Biome> biome, BlockPos pos) {
        return biome.value().getHeightAdjustedTemperature(pos);
    }
}
