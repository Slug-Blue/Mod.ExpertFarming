package io.github.slugblue.wildroots.genetics.model;

import java.util.Objects;

/**
 * Trait id as written in the species JSON ({@code yield}, {@code color_y1}),
 * GDD §3.3, §3.6. Ordered by the string, which is the canonical layout order
 * inside a section (docs/05-architecture.md §5.1).
 */
public record TraitId(String value) implements Comparable<TraitId> {

    /** Id of the load section (GDD §3.7); it has no JSON trait of its own. */
    public static final TraitId LOAD = new TraitId("load");

    public TraitId {
        Objects.requireNonNull(value, "value");
    }

    public static TraitId of(String value) {
        return new TraitId(value);
    }

    @Override
    public int compareTo(TraitId o) {
        return value.compareTo(o.value);
    }

    @Override
    public String toString() {
        return value;
    }
}
