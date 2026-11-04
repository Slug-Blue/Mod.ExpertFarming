package io.github.slugblue.wildroots.core.rng;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import io.github.slugblue.wildroots.genetics.random.Hash64;
import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** WR-123: RNG keys follow the table of architecture §4.3; worldgen and villages do not take the secret. */
class RngKeysTest {

    static final long SECRET = 0x1234_5678_9ABC_DEF0L;
    static final long OTHER_SECRET = SECRET + 1;
    static final long LOT = -42L;
    static final long POS = 0x0000_0040_0000_0010L;

    @Test
    void keysAreTheTableFormulas() {
        assertEquals(Hash64.of(SECRET, LOT, 7, POS), RngKeys.offspring(SECRET, LOT, 7, POS));
        assertEquals(Hash64.of(SECRET, LOT, 7, POS, Hash64.fnv1a64("noise")), RngKeys.noise(SECRET, LOT, 7, POS));
        assertEquals(Hash64.of(99L, 5L, POS), RngKeys.wildPlant(99L, 5L, POS));
        assertEquals(Hash64.of(99L, POS, Hash64.fnv1a64("wildroots:eastern_carrot"), 3),
                RngKeys.villagePlant(99L, POS, "wildroots:eastern_carrot", 3));
        assertEquals(Hash64.of(SECRET, 77L, 2), RngKeys.fathers(SECRET, 77L, 2));
        assertEquals(Hash64.of(SECRET, LOT), RngKeys.lazyLot(SECRET, LOT));
        assertEquals(Hash64.of(SECRET, 24_000L, 5L), LotIds.derive(SECRET, 24_000L, 5L));
    }

    @Test
    void secretChangesEverySecretKey() {
        assertNotEquals(RngKeys.offspring(SECRET, LOT, 7, POS), RngKeys.offspring(OTHER_SECRET, LOT, 7, POS));
        assertNotEquals(RngKeys.noise(SECRET, LOT, 7, POS), RngKeys.noise(OTHER_SECRET, LOT, 7, POS));
        assertNotEquals(RngKeys.fathers(SECRET, 77L, 2), RngKeys.fathers(OTHER_SECRET, 77L, 2));
        assertNotEquals(RngKeys.lazyLot(SECRET, LOT), RngKeys.lazyLot(OTHER_SECRET, LOT));
    }

    @Test
    void eventsWithSameInputsGetDifferentKeys() {
        Set<Long> keys = new HashSet<>();
        keys.add(RngKeys.offspring(SECRET, LOT, 7, POS));
        keys.add(RngKeys.noise(SECRET, LOT, 7, POS));
        keys.add(RngKeys.lazyLot(SECRET, LOT));
        keys.add(RngKeys.offspring(SECRET, LOT, 6, POS)); // next seed of the same lot
        assertEquals(4, keys.size());
    }

    /** Worldgen and village keys are on worldSeed only: a seed reproduces the world. */
    @Test
    void worldgenAndVillageKeysHaveNoSecretParameter() throws NoSuchMethodException {
        Method wild = RngKeys.class.getMethod("wildPlant", long.class, long.class, long.class);
        Method village = RngKeys.class.getMethod("villagePlant", long.class, long.class, String.class, int.class);
        // the first argument is the world seed; changing only it changes the key
        assertNotEquals(RngKeys.wildPlant(1L, 5L, POS), RngKeys.wildPlant(2L, 5L, POS));
        assertEquals(3, wild.getParameterCount());
        assertEquals(4, village.getParameterCount());
    }

    /** WR-150: worldgen keys (patch seed, chunk band) take the world seed only, never the secret (§4.3). */
    @Test
    void worldgenPatchKeysTakeOnlyTheWorldSeed() throws NoSuchMethodException {
        assertEquals(Hash64.of(99L, POS), RngKeys.patchSeed(99L, POS));
        assertEquals(Hash64.of(99L, 3, -4, Hash64.fnv1a64("wildroots_patch")), RngKeys.chunkBand(99L, 3, -4));
        assertEquals(2, RngKeys.class.getMethod("patchSeed", long.class, long.class).getParameterCount());
        assertEquals(3, RngKeys.class.getMethod("chunkBand", long.class, int.class, int.class).getParameterCount());
        assertNotEquals(RngKeys.chunkBand(1L, 0, 0), RngKeys.chunkBand(2L, 0, 0));
        assertNotEquals(RngKeys.chunkBand(1L, 0, 1), RngKeys.chunkBand(1L, 1, 0));
    }

    @Test
    void lotIdsAreDistinctWithinOneTick() {
        Set<Long> ids = new HashSet<>();
        for (int i = 0; i < 10_000; i++) {
            ids.add(LotIds.next(SECRET, 100L));
        }
        assertEquals(10_000, ids.size());
    }
}
