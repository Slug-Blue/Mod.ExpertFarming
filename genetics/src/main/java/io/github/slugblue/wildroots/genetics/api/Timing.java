package io.github.slugblue.wildroots.genetics.api;

/** Stage timing in minutes (GDD §3.9 {@code timing}, §12.1). */
public record Timing(double toHarvestMin, double seedStageMin) {
}
