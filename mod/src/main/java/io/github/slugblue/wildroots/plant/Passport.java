package io.github.slugblue.wildroots.plant;

/**
 * Passport of origin of a planted plant (docs/05-architecture.md §6.1;
 * worldgen §3.3; GDD §9.5): where the plant comes from and, for wild and
 * village plants, everything needed to realise the genome lazily.
 *
 * @param origin       {@code wild}, {@code village} or {@code player}
 * @param patchOrigin  packed origin of the wild patch ({@code BlockPos#asLong})
 * @param category     patch category ({@code meadow}) of a wild plant, or {@code ""}
 * @param rich         rich wild patch
 * @param starter      starter patch
 * @param landrace     landrace id of a village plant, or {@code ""}
 * @param villagePos   packed village position
 * @param harvestCount harvests of the village cell (key of the next village plant)
 */
public record Passport(Origin origin, long patchOrigin, String category, boolean rich, boolean starter,
                       String landrace, long villagePos, int harvestCount) {

    /** Where a plant comes from. */
    public enum Origin {
        WILD, VILLAGE, PLAYER;

        static Origin of(int ordinal) {
            Origin[] all = values();
            return ordinal >= 0 && ordinal < all.length ? all[ordinal] : null;
        }
    }

    public static final Passport PLAYER = new Passport(Origin.PLAYER, 0, "", false, false, "", 0, 0);

    public Passport {
        category = category == null ? "" : category;
        landrace = landrace == null ? "" : landrace;
    }

    /** Passport of a wild plant of a patch. */
    public static Passport wild(long patchOrigin, String category, boolean rich, boolean starter) {
        return new Passport(Origin.WILD, patchOrigin, category, rich, starter, "", 0, 0);
    }
}
