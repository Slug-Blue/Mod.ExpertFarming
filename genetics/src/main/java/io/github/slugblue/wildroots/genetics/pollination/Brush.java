package io.github.slugblue.wildroots.genetics.pollination;

/** Use of the pollination brush on the mother (GDD §4.4). */
public enum Brush {
    /** No brush. */
    NONE,
    /** Pollen of another plant brushed on: {@code p_out} 0.90. */
    CROSS,
    /**
     * Forced selfing (brush on the same plant): 90 % selfed. The only way to
     * self a self-incompatible species at full rate (bud pollination, seed set 0.5).
     */
    SELF
}
