package io.github.slugblue.wildroots.pollination;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Small integer per species id for the keys of {@link BloomIndex}
 * (docs/05-architecture.md §10.2: {@code speciesIndex → packed BlockPos}).
 * The numbers live only in memory and are never saved, so their order does
 * not matter; an id keeps its number for the life of the process.
 */
public final class SpeciesIndex {

    private static final Map<String, Integer> IDS = new HashMap<>();
    private static final List<String> NAMES = new ArrayList<>();

    private SpeciesIndex() {
    }

    /** Number of {@code species}, assigned on first use. */
    public static synchronized int of(String species) {
        Integer i = IDS.get(species);
        if (i != null) {
            return i;
        }
        int next = NAMES.size();
        NAMES.add(species);
        IDS.put(species, next);
        return next;
    }

    /** Species id of number {@code index}, if assigned. */
    public static synchronized Optional<String> name(int index) {
        return index >= 0 && index < NAMES.size() ? Optional.of(NAMES.get(index)) : Optional.empty();
    }
}
