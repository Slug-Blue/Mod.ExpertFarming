package io.github.slugblue.wildroots.genetics.random;

import java.nio.charset.StandardCharsets;

/**
 * Deterministic 64-bit hashes of the engine (ADR-0001, docs/05-architecture.md
 * §4.3): {@link #of(long...)} mixes numeric key parts with the SplitMix64
 * finaliser, {@link #fnv1a64(String)} hashes ids as FNV-1a 64 over UTF-8.
 * {@code String.hashCode} is never used.
 *
 * <p>Golden values: {@code src/test/resources/golden/hash64.txt}.
 */
public final class Hash64 {

    /** Start value of {@link #of(long...)} (first 64 bits of the fraction of π). */
    public static final long SEED = 0x243F6A8885A308D3L;

    private static final long FNV_OFFSET = 0xcbf29ce484222325L;
    private static final long FNV_PRIME = 0x100000001b3L;

    private Hash64() {
    }

    /** SplitMix64 finaliser (variant 13 of Stafford's mix). Bijective on {@code long}. */
    public static long mix(long z) {
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }

    /**
     * Order-sensitive hash of key parts: {@code h = SEED; h = mix((h ^ p) + γ)}
     * for every part. String parts go in as {@link #fnv1a64(String)}.
     */
    public static long of(long... parts) {
        long h = SEED;
        for (long p : parts) {
            h = mix((h ^ p) + SplitMix64.GOLDEN_GAMMA);
        }
        return h;
    }

    /** FNV-1a 64 over the UTF-8 bytes of {@code s}. */
    public static long fnv1a64(String s) {
        long h = FNV_OFFSET;
        for (byte b : s.getBytes(StandardCharsets.UTF_8)) {
            h ^= b & 0xFF;
            h *= FNV_PRIME;
        }
        return h;
    }

    /** Shorthand for {@link #fnv1a64(String)} when a string is a key part. */
    public static long str(String s) {
        return fnv1a64(s);
    }
}
