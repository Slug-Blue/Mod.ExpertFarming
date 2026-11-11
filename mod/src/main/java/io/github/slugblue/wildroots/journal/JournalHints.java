package io.github.slugblue.wildroots.journal;

import io.github.slugblue.wildroots.core.line.LineRegistry;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;

/**
 * Catalogue of the journal hints of narrative §4.5 with the line (or the
 * later feature) each belongs to. A hint of a line that is not enabled in
 * this build — the wheat, maize, cabbage, … lines, or a tool of a later
 * wave such as the brush or the genotype lab — never fires (WR-182). Core
 * hints ({@link #CORE}) belong to wave 1.
 *
 * <p>{@link #PER_LINE} hints fire once per breeding line and again no
 * sooner than {@value #REPEAT_GENERATIONS} generations later; every other
 * hint fires once per player.
 */
public final class JournalHints {

    /** Wave-1 content without a crop line. */
    public static final String CORE = "";
    /** Features of later waves that are not lines: never enabled by the line registry. */
    public static final String BRUSH = "feature:brush";
    public static final String LAB = "feature:genotype_lab";
    public static final String CLONES = "feature:clones";
    public static final String VILLAGE = "feature:village";
    /** Wave-1 hints without a trigger and text yet: off until they get both (review 1, m7). */
    public static final String PENDING = "feature:pending";

    public static final int REPEAT_GENERATIONS = 3;

    /** hint id → line ({@link #CORE} for wave 1 without a line). */
    static final Map<String, String> ALL = new LinkedHashMap<>();
    /** Hints counted per line, not per player (narrative §4.1). */
    public static final java.util.Set<String> PER_LINE = java.util.Set.of("stagnation", "missing_allele");

    static {
        // start and selection
        core("welcome", "plant_together", "lone_plant", "small_population", "select_quarter", "mother_plant",
                "left_on_stalk", "root_ripe");
        // traits one can see
        core("bitter");
        line("tomato", "toxic_tomato");
        line("carrot", "yellow_root", "orange_root", "purple_root");
        line("wheat", "shattering", "full_ear", "brush_for_wheat", "dormancy");
        line("maize", "teosinte_case", "single_stalk", "wind_pollen");
        line("cabbage", "cabbage_roads", "bud_pollination", "self_incompat");
        line("rice", "rice_water");
        // pollination and bees
        core("bees", "no_pollinators");
        line(BRUSH, "hand_pollination", "forced_self");
        // inbreeding and fresh blood
        core("inbreeding", "hybrid_vigor", "stagnation", "missing_allele", "second_biome", "climate", "bone_meal",
                "unattended");
        line(VILLAGE, "village_seeds", "rare_allele", "far_lands");
        // cultivars and tools
        core("looks_like", "first_landrace", "first_cultivar", "mutation", "carrier", "half_fixed");
        line(LAB, "genotype_lab");
        // trade-offs and quality
        line(PENDING, "tradeoff", "limit", "grades", "bitter_grade", "graded_by_hand");
        line("wheat", "bread_grade");
        line("cane", "rough_paper", "no_sugar", "cane_tropics");
        // clones, grafts, perennials
        line(CLONES, "clone", "clone_of_clone", "perennial");
        line("potato", "potato_berries", "long_stolons");
        line("cacao", "cacao_hand");
        line("raspberry", "thornless");
        line("apple", "apple_seedlings", "apple_pips", "seedling_cultivar", "graft", "graft_failed");
        // squash, watermelon, beet
        line("squash", "stone_gourd", "empty_flowers", "carve_small", "carve_first");
        line("watermelon", "red_flesh");
        line("beet", "beet_bolting", "beet_roads");
        // village, range, breeder
        line(VILLAGE, "village_field", "feral_planting", "breeder_met", "rare_find_sold");
        core("seed_pouch");
    }

    private static void core(String... ids) {
        line(CORE, ids);
    }

    private static void line(String line, String... ids) {
        for (String id : ids) {
            ALL.put(id, line);
        }
    }

    /** Which line enables a line id (tests replace it). */
    private static volatile Predicate<String> lineEnabled = l -> LineRegistry.get().isEnabled(l);

    private JournalHints() {
    }

    /** Replaces the line check (JUnit without a frozen registry) and returns the previous one. */
    static Predicate<String> setLineCheck(Predicate<String> p) {
        Predicate<String> old = lineEnabled;
        lineEnabled = p;
        return old;
    }

    /** The line of a hint, if the hint exists. */
    public static Optional<String> line(String hintId) {
        return Optional.ofNullable(ALL.get(hintId));
    }

    /** Whether the hint {@code hint.<id>} (or a per-line {@code hint.<id>.<line>}) may fire in this build. */
    public static boolean enabled(String entryId) {
        if (!entryId.startsWith("hint.")) {
            return true;
        }
        String rest = entryId.substring("hint.".length());
        String base = rest.contains(".") ? rest.substring(0, rest.indexOf('.')) : rest;
        if (rest.contains(".") && !PER_LINE.contains(base)) {
            return false;
        }
        String line = ALL.get(base);
        if (line == null) {
            return false;
        }
        return line.equals(CORE) || (!line.startsWith("feature:") && lineEnabled.test(line));
    }
}
