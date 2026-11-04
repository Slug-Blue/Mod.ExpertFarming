package io.github.slugblue.wildroots.worldgen;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.slugblue.wildroots.core.registry.ModPlacementModifiers;
import io.github.slugblue.wildroots.core.safety.EntryPoint;
import io.github.slugblue.wildroots.core.safety.Guarded;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.placement.PlacementContext;
import net.minecraft.world.level.levelgen.placement.PlacementFilter;
import net.minecraft.world.level.levelgen.placement.PlacementModifierType;

/**
 * {@code wildroots:chunk_band} (worldgen §4.2): one number per chunk,
 * {@code u = hash(worldSeed, chunkX, chunkZ, "wildroots_patch") ∈ [0, 1)},
 * and every species owns a disjoint interval {@code [from, to)} of it. So a
 * chunk gets at most one wild patch whatever the set of species, the width
 * of an interval is the chance, and candidate chunks are known without
 * generating anything ({@code /wildroots locate_patch}). The key takes the
 * world seed only, never the world secret (architecture §4.3).
 */
public final class ChunkBandPlacement extends PlacementFilter {

    public static final Codec<ChunkBandPlacement> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.doubleRange(0, 1).fieldOf("from").forGetter(ChunkBandPlacement::from),
            Codec.doubleRange(0, 1).fieldOf("to").forGetter(ChunkBandPlacement::to)
    ).apply(i, ChunkBandPlacement::new));

    private final double from;
    private final double to;

    public ChunkBandPlacement(double from, double to) {
        this.from = from;
        this.to = to;
    }

    public double from() {
        return from;
    }

    public double to() {
        return to;
    }

    /** Whether chunk {@code (chunkX, chunkZ)} is a candidate of this band. */
    public boolean candidate(long worldSeed, int chunkX, int chunkZ) {
        return ChunkBand.candidate(worldSeed, chunkX, chunkZ, from, to);
    }

    @Override
    @EntryPoint
    protected boolean shouldPlace(PlacementContext context, RandomSource random, BlockPos pos) {
        return Guarded.call("chunk_band", pos, () -> candidate(context.getLevel().getSeed(), pos.getX() >> 4,
                pos.getZ() >> 4), false);
    }

    @Override
    public PlacementModifierType<?> type() {
        return ModPlacementModifiers.CHUNK_BAND.get();
    }
}
