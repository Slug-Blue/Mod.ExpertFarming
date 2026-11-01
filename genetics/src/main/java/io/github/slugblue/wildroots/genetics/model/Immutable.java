package io.github.slugblue.wildroots.genetics.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;

/**
 * Defensive copies with a deterministic iteration order. {@code Map.copyOf}
 * is not used: its iteration order changes from run to run.
 */
public final class Immutable {

    private Immutable() {
    }

    /** Unmodifiable sorted copy; {@code null} gives an empty map; null keys or values are dropped. */
    public static <K extends Comparable<K>, V> SortedMap<K, V> sorted(Map<K, V> m) {
        TreeMap<K, V> t = new TreeMap<>();
        if (m != null) {
            for (Map.Entry<K, V> e : m.entrySet()) {
                if (e.getKey() != null && e.getValue() != null) {
                    t.put(e.getKey(), e.getValue());
                }
            }
        }
        return Collections.unmodifiableSortedMap(t);
    }

    /** Unmodifiable copy keeping order; {@code null} gives an empty list; null elements are dropped. */
    public static <T> List<T> list(List<T> l) {
        if (l == null) {
            return List.of();
        }
        List<T> out = new ArrayList<>(l.size());
        for (T t : l) {
            if (t != null) {
                out.add(t);
            }
        }
        return Collections.unmodifiableList(out);
    }
}
