package io.github.slugblue.wildroots.genetics.api;

import java.util.Optional;

/**
 * Pollination block of a species (GDD §3.9, §4.2).
 *
 * @param mode                {@code insect}, {@code wind}, {@code self} (display only)
 * @param selfCompatible      {@code false} for S-locus species
 * @param radius              pollination radius, blocks (0–16)
 * @param outcross            base outcrossing without bees
 * @param bees                bee pollination, empty if bees do not pollinate
 * @param seedSetNoPollinator seed set without a pollinator
 */
public record Pollination(String mode, boolean selfCompatible, int radius, double outcross,
                          Optional<Bees> bees, double seedSetNoPollinator) {

    public Pollination {
        bees = bees == null ? Optional.empty() : bees;
    }
}
