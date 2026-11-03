package io.github.slugblue.wildroots.data;

import io.github.slugblue.wildroots.genetics.api.CultivarDef;
import io.github.slugblue.wildroots.genetics.api.Genetics;
import io.github.slugblue.wildroots.genetics.api.GeneticsService;
import io.github.slugblue.wildroots.genetics.api.SpeciesDef;
import io.github.slugblue.wildroots.genetics.codec.LayoutDictionary;
import io.github.slugblue.wildroots.genetics.model.GenomeLayout;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.resources.ResourceLocation;

/**
 * Immutable result of one data load (docs/05-architecture.md §8.2 item 6;
 * ADR-0005): valid species and cultivars with their mod data, the counts of
 * the load and an engine facade over exactly these definitions. Server logic
 * reads one whole snapshot; {@link Definitions} swaps it atomically at the
 * end of {@code apply}, so there is never a half-loaded state.
 */
public final class DefinitionsSnapshot {

    /** A loaded species: engine definition, mod data, source file. */
    public record SpeciesEntry(SpeciesDef def, SpeciesInfo info, ResourceLocation file) {
    }

    /** A loaded cultivar: engine definition, mod data, source file. */
    public record CultivarEntry(CultivarDef def, CultivarInfo info, ResourceLocation file) {
    }

    /** Counts of the load (for the summary line and the operator message). */
    public record Stats(int speciesFiles, int speciesSkipped, int cultivarFiles, int cultivarsSkipped,
                        int errors, int warnings) {

        public static final Stats NONE = new Stats(0, 0, 0, 0, 0, 0);

        /** {@code Wild Roots: species N (k skipped), cultivars M (j skipped)}. */
        public String summary() {
            return "Wild Roots: species " + (speciesFiles - speciesSkipped) + " (" + speciesSkipped
                    + " skipped), cultivars " + (cultivarFiles - cultivarsSkipped) + " (" + cultivarsSkipped
                    + " skipped)";
        }

        public boolean hadProblems() {
            return errors > 0 || warnings > 0;
        }
    }

    public static final DefinitionsSnapshot EMPTY = new DefinitionsSnapshot(Map.of(), Map.of(), Stats.NONE);

    /** Snapshot whose layout dictionary holds just the current layouts. */
    public DefinitionsSnapshot(Map<String, SpeciesEntry> species, Map<String, CultivarEntry> cultivars, Stats stats) {
        this(species, cultivars, stats, currentLayouts(species));
    }

    private static LayoutDictionary currentLayouts(Map<String, SpeciesEntry> species) {
        LayoutDictionary d = new LayoutDictionary();
        species.values().forEach(s -> d.register(s.def().id(), GenomeLayout.of(s.def())));
        return d;
    }

    private final Map<String, SpeciesEntry> species;
    private final Map<String, CultivarEntry> cultivars;
    private final Map<String, List<CultivarDef>> profiles;
    private final Stats stats;
    private final Genetics genetics;
    private final LayoutDictionary dictionary;
    private final Layouts layoutsView;

    /**
     * @param dictionary layout dictionary of the engine: jar and data-pack history plus the current layouts
     *                   (the layouts saved in the world join it with the mod part of WR-111)
     */
    public DefinitionsSnapshot(Map<String, SpeciesEntry> species, Map<String, CultivarEntry> cultivars, Stats stats,
                               LayoutDictionary dictionary) {
        this.species = Collections.unmodifiableMap(new LinkedHashMap<>(species));
        this.cultivars = Collections.unmodifiableMap(new LinkedHashMap<>(cultivars));
        Map<String, List<CultivarDef>> byS = new LinkedHashMap<>();
        cultivars.values().forEach(c -> byS.computeIfAbsent(c.def().species(), k -> new java.util.ArrayList<>())
                .add(c.def()));
        byS.replaceAll((k, v) -> List.copyOf(v));
        this.profiles = Collections.unmodifiableMap(byS);
        this.stats = stats;
        this.dictionary = dictionary;
        this.layoutsView = new Layouts(dictionary);
        this.genetics = new Genetics(id -> {
            SpeciesEntry e = this.species.get(id);
            return e == null ? null : e.def();
        }, dictionary);
    }

    public Map<String, SpeciesEntry> species() {
        return species;
    }

    public Map<String, CultivarEntry> cultivars() {
        return cultivars;
    }

    public Optional<SpeciesEntry> species(String id) {
        return Optional.ofNullable(species.get(id));
    }

    public Optional<CultivarEntry> cultivar(String id) {
        return Optional.ofNullable(cultivars.get(id));
    }

    /** Cultivar and landrace profiles of a species, for classification. */
    public List<CultivarDef> profiles(String speciesId) {
        return profiles.getOrDefault(speciesId, List.of());
    }

    public Stats stats() {
        return stats;
    }

    /** Read-only view of a layout dictionary: lookups and entries, no {@code register}. */
    public static final class Layouts {
        private final LayoutDictionary dictionary;

        private Layouts(LayoutDictionary dictionary) {
            this.dictionary = dictionary;
        }

        public Optional<GenomeLayout> lookup(String species, long lh) {
            return dictionary.lookup(species, lh);
        }

        /** All entries, unmodifiable. */
        public List<io.github.slugblue.wildroots.genetics.codec.LayoutDescriptor> entries() {
            return List.copyOf(dictionary.entries());
        }
    }

    /** Layouts known to this snapshot, without copying; the view cannot change them. */
    public Layouts layouts() {
        return layoutsView;
    }

    /** Engine facade over the definitions of this snapshot. */
    public GeneticsService genetics() {
        return genetics;
    }
}
