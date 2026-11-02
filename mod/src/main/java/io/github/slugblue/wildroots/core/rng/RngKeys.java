package io.github.slugblue.wildroots.core.rng;

import io.github.slugblue.wildroots.genetics.random.Hash64;

/**
 * RNG keys of game events (docs/05-architecture.md §4.3, table "Ключи RNG";
 * ADR-0001 rev. 1; SEC-25). The mod layer computes them, the engine takes
 * them as plain {@code long}s and does not know where they come from.
 *
 * <p>Keys of planting, planting noise, father choice and lazy lots include
 * the world secret; the caller gets it from {@code core.safety.WorldSecret}.
 * Wild plants and villages use {@code worldSeed} so that a seed reproduces
 * the world. Positions are {@code BlockPos#asLong()}.
 */
public final class RngKeys {

    /** String part of the planting-noise key ({@code "noise"}). */
    static final long NOISE = Hash64.str("noise");

    private RngKeys() {
    }

    /** Seedling at planting: {@code Hash64(secret, lotId, remainingCount, packedPos)} (§21 item 1). */
    public static long offspring(long secret, long lotId, int remainingCount, long packedPos) {
        return Hash64.of(secret, lotId, remainingCount, packedPos);
    }

    /** ε at planting: {@code Hash64(secret, lotId, remainingCount, packedPos, "noise")}. */
    public static long noise(long secret, long lotId, int remainingCount, long packedPos) {
        return Hash64.of(secret, lotId, remainingCount, packedPos, NOISE);
    }

    /** Wild plant: {@code Hash64(worldSeed, patchOrigin, plantPos)} (worldgen §3.3). */
    public static long wildPlant(long worldSeed, long patchOrigin, long plantPos) {
        return Hash64.of(worldSeed, patchOrigin, plantPos);
    }

    /** Village plant: {@code Hash64(worldSeed, villagePos, landraceId, harvestCount)} (GDD §9.5). */
    public static long villagePlant(long worldSeed, long villagePos, String landraceId, int harvestCount) {
        return Hash64.of(worldSeed, villagePos, Hash64.str(landraceId), harvestCount);
    }

    /** Fathers at seed set: {@code Hash64(secret, plantUid, cycleIndex)}. */
    public static long fathers(long secret, long plantUid, int cycleIndex) {
        return Hash64.of(secret, plantUid, cycleIndex);
    }

    /** Realising a lazy lot (grass, chest, trade): {@code Hash64(secret, lotId)}. */
    public static long lazyLot(long secret, long lotId) {
        return Hash64.of(secret, lotId);
    }
}
