package io.github.slugblue.wildroots.genetics.api;

/** Threshold of a cultivar profile on {@code G_eff}: {@code min ≤ G_eff ≤ max} (GDD §6.3). */
public record Range(double min, double max) {

    public static Range atLeast(double min) {
        return new Range(min, 1.0);
    }
}
