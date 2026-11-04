package io.github.slugblue.wildroots.worldgen;

/**
 * Temperature window test of {@code wildroots:species_climate} (worldgen
 * §2.2, §4.2; S11) as plain arithmetic: above Y = 80 vanilla temperature
 * carries noise of ±0.01 and frozen biomes take noise into the base, so
 * there the edges get a slack of {@value #EDGE_SLACK}.
 */
public final class ClimateWindow {

    public static final double EDGE_SLACK = 0.01;
    public static final int NOISE_ABOVE_Y = 80;

    private ClimateWindow() {
    }

    /** Whether temperature {@code t} fits {@code [min, max]} ± tolerance (± slack when {@code noisy}). */
    public static boolean fits(double t, double min, double max, double tolerance, boolean noisy) {
        double slack = tolerance + (noisy ? EDGE_SLACK : 0);
        return t >= min - slack && t <= max + slack;
    }
}
