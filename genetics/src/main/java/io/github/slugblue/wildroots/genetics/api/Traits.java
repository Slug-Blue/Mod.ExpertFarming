package io.github.slugblue.wildroots.genetics.api;

import io.github.slugblue.wildroots.genetics.model.TraitId;

/** Trait ids with a fixed role in the rules (GDD §3.3, §3.6). */
public final class Traits {

    public static final TraitId YIELD = TraitId.of("yield");
    public static final TraitId FRUIT_SIZE = TraitId.of("fruit_size");
    public static final TraitId GROWTH_SPEED = TraitId.of("growth_speed");
    public static final TraitId FLAVOR = TraitId.of("flavor");
    public static final TraitId COLD_TOLERANCE = TraitId.of("cold_tolerance");
    public static final TraitId HEAT_TOLERANCE = TraitId.of("heat_tolerance");
    public static final TraitId DROUGHT_TOLERANCE = TraitId.of("drought_tolerance");
    /** Wild phenotype = bitter harvest, grade ≤ 2 (GDD §3.6, §6.4). */
    public static final TraitId BITTERNESS = TraitId.of("bitterness");
    /** Wild phenotype = seeds shatter, 35 % reach the lot (GDD §3.6, §4.1). */
    public static final TraitId SHATTERING = TraitId.of("shattering");

    private Traits() {
    }
}
