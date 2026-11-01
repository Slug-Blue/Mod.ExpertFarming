package io.github.slugblue.wildroots.genetics.codec;

import io.github.slugblue.wildroots.genetics.model.GenomeLayout;
import io.github.slugblue.wildroots.genetics.validation.Problem;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

/**
 * Layout dictionary indexed by {@code (species, lh)} that keeps the canonical
 * string (ADR-0002 rev. 2, S10). If an {@code lh} is already taken by another
 * string of the same species, registration fails with an {@code ERROR}: the
 * species definition is rejected instead of silently decoding alleles with a
 * foreign layout. Not thread safe; the mod layer fills it on load.
 */
public final class LayoutDictionary {

    private final Map<String, Map<Long, String>> entries = new TreeMap<>();

    /** Registers the layout of a species; empty on success, ERROR on a hash collision. */
    public Optional<Problem> register(String species, GenomeLayout layout) {
        return register(new LayoutDescriptor(species, layout.hash(), layout.canonical()));
    }

    /** Registers a stored entry (jar history or world data). */
    public Optional<Problem> register(LayoutDescriptor d) {
        Map<Long, String> bySpecies = entries.computeIfAbsent(d.species(), k -> new TreeMap<>());
        String prev = bySpecies.get(d.lh());
        if (prev != null && !prev.equals(d.canonical())) {
            return Optional.of(Problem.error("layout", "layoutHash " + Long.toHexString(d.lh()) + " of "
                    + d.species() + " already belongs to '" + prev + "', not '" + d.canonical()
                    + "': species definition rejected"));
        }
        bySpecies.put(d.lh(), d.canonical());
        return Optional.empty();
    }

    /** Layout stored under {@code (species, lh)}, if any and parseable. */
    public Optional<GenomeLayout> lookup(String species, long lh) {
        Map<Long, String> bySpecies = entries.get(species);
        String c = bySpecies == null ? null : bySpecies.get(lh);
        if (c == null) {
            return Optional.empty();
        }
        return GenomeLayout.parse(c).filter(l -> l.hash() == lh);
    }

    /** All entries in a stable order (for saving). */
    public List<LayoutDescriptor> entries() {
        List<LayoutDescriptor> out = new ArrayList<>();
        for (Map.Entry<String, Map<Long, String>> e : entries.entrySet()) {
            for (Map.Entry<Long, String> l : e.getValue().entrySet()) {
                out.add(new LayoutDescriptor(e.getKey(), l.getKey(), l.getValue()));
            }
        }
        return out;
    }
}
