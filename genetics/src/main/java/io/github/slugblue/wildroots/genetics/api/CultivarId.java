package io.github.slugblue.wildroots.genetics.api;

import java.util.Objects;

/** Cultivar or landrace id ({@code wildroots:carrot}). */
public record CultivarId(String value) implements Comparable<CultivarId> {

    public CultivarId {
        Objects.requireNonNull(value, "value");
    }

    public static CultivarId of(String value) {
        return new CultivarId(value);
    }

    @Override
    public int compareTo(CultivarId o) {
        return value.compareTo(o.value);
    }

    @Override
    public String toString() {
        return value;
    }
}
