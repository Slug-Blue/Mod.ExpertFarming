package io.github.slugblue.wildroots.genetics.random;

/**
 * Standard normal deviates by Box–Muller on {@link SplitMix64}
 * (ADR-0001, docs/05-architecture.md §4.3).
 *
 * <p>Uses {@link StrictMath}: unlike {@code Math.log/sin/cos}, its results are
 * bit-identical on every JVM, so a seed gives the same plant everywhere.
 * The second deviate of each pair is cached.
 */
public final class Gaussian {

    private final SplitMix64 rng;
    private double spare;
    private boolean hasSpare;

    public Gaussian(SplitMix64 rng) {
        this.rng = rng;
    }

    /** Next N(0, 1) deviate. */
    public double next() {
        if (hasSpare) {
            hasSpare = false;
            return spare;
        }
        double u1 = 1.0 - rng.nextDouble(); // (0, 1]: log is finite
        double u2 = rng.nextDouble();
        double r = StrictMath.sqrt(-2.0 * StrictMath.log(u1));
        double theta = 2.0 * StrictMath.PI * u2;
        spare = r * StrictMath.sin(theta);
        hasSpare = true;
        return r * StrictMath.cos(theta);
    }

    /** Next N(mean, sigma²) deviate. */
    public double next(double mean, double sigma) {
        return mean + sigma * next();
    }
}
