package io.github.slugblue.wildroots.item;

/**
 * Bit flags of a seed lot ({@code fl}) and of the ancestor lot of a plant
 * (docs/05-architecture.md §6.1; GDD §5.3).
 */
public final class LotFlags {

    /** "From the wild": inherited along the mother line (GDD §5.3). */
    public static final int FROM_WILD = 1;
    /** Village origin. */
    public static final int VILLAGE = 2;
    /** Brush pollination. */
    public static final int BRUSH = 4;
    /** Bee pollination. */
    public static final int BEES = 8;
    /** Inspected with the hand lens (tier 1). */
    public static final int INSPECTED = 16;
    /** Analysed (tier 2). */
    public static final int ANALYZED = 32;
    /** Flags a plant passes to its seeds: only the mother line (GDD §5.3). */
    public static final int INHERITED = FROM_WILD | VILLAGE;

    private LotFlags() {
    }
}
