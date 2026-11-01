package io.github.slugblue.wildroots.genetics.random;

/**
 * SplitMix64 (Steele, Lea, Flood 2014): the only random generator of the
 * engine (ADR-0001, docs/05-architecture.md §4.3).
 *
 * <p>The state is one {@code long}; the sequence for a seed is fixed by the
 * golden file {@code src/test/resources/golden/splitmix64.txt}. Not thread
 * safe: every caller derives its own instance from a key
 * ({@link Hash64#of(long...)}).
 */
public final class SplitMix64 {

    /** Golden-ratio increment of the state (Weyl sequence). */
    public static final long GOLDEN_GAMMA = 0x9E3779B97F4A7C15L;

    private static final double DOUBLE_UNIT = 0x1.0p-53;

    private long state;

    public SplitMix64(long seed) {
        this.state = seed;
    }

    /** Next 64 random bits. */
    public long nextLong() {
        state += GOLDEN_GAMMA;
        return Hash64.mix(state);
    }

    /** Uniform double in {@code [0, 1)} with 53 random bits. */
    public double nextDouble() {
        return (nextLong() >>> 11) * DOUBLE_UNIT;
    }

    /** Uniform int in {@code [0, bound)}, without modulo bias (Lemire with rejection). */
    public int nextInt(int bound) {
        if (bound <= 0) {
            throw new IllegalArgumentException("bound must be positive: " + bound);
        }
        long m = (nextLong() >>> 32) * bound;
        long low = m & 0xFFFFFFFFL;
        if (low < bound) {
            long threshold = (0x1_0000_0000L - bound) % bound;
            while (low < threshold) {
                m = (nextLong() >>> 32) * bound;
                low = m & 0xFFFFFFFFL;
            }
        }
        return (int) (m >>> 32);
    }

    /** One fair random bit. */
    public boolean nextBoolean() {
        return nextLong() < 0;
    }

    /** {@code true} with probability {@code p} (p ≤ 0 never, p ≥ 1 always). */
    public boolean chance(double p) {
        return nextDouble() < p;
    }

    /** Index drawn from a discrete distribution (weights ≥ 0, sum &gt; 0); last index on rounding. */
    public int pick(double[] weights) {
        double total = 0;
        for (double w : weights) {
            total += w;
        }
        double u = nextDouble() * total;
        for (int i = 0; i < weights.length; i++) {
            u -= weights[i];
            if (u < 0) {
                return i;
            }
        }
        return weights.length - 1;
    }

    /** Current state (for tests and for forking a stream). */
    public long state() {
        return state;
    }
}
