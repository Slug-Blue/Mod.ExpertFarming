package io.github.slugblue.wildroots.lens;

import java.util.Map;
import java.util.Set;

/**
 * The five-step word scale of the hand lens (narrative §9.2; GDD §10, tier
 * 1): {@code < 0.20 | 0.20–0.35 | 0.35–0.55 | 0.55–0.75 | ≥ 0.75}. A value on
 * a boundary belongs to the upper step (0.35 is step 3). Pure (JUnit).
 */
public final class LensScale {

    /** Lower bounds of steps 2–5. */
    public static final double[] BOUNDS = {0.20, 0.35, 0.55, 0.75};

    /** The scales of narrative §9.2. */
    public static final Set<String> SCALES = Set.of("size", "yield", "growth", "flavor", "sweetness", "tenderness",
            "tolerance", "expression");

    /** Default scale of a quantitative trait (narrative §9.2 table, "Scale" column). */
    static final Map<String, String> DEFAULT_SCALE = Map.ofEntries(
            Map.entry("fruit_size", "size"), Map.entry("yield", "yield"), Map.entry("growth_speed", "growth"),
            Map.entry("flavor", "flavor"), Map.entry("sugar", "tolerance"), Map.entry("cold_tolerance", "tolerance"),
            Map.entry("heat_tolerance", "tolerance"), Map.entry("drought_tolerance", "tolerance"),
            Map.entry("leaf_size", "size"), Map.entry("terminal_bud", "expression"),
            Map.entry("inflorescence", "expression"), Map.entry("stem", "expression"),
            Map.entry("lateral_buds", "expression"), Map.entry("apical_dominance", "expression"));

    private LensScale() {
    }

    /** Step 1–5 of {@code p}; NaN and values below 0 are step 1. */
    public static int step(double p) {
        int s = 1;
        for (double b : BOUNDS) {
            if (p >= b) {
                s++;
            }
        }
        return s;
    }

    /**
     * Scale of {@code trait} for a species: its {@code display_traits} value
     * when that names a scale ({@code flavor: sweetness} for the carrot),
     * else the default of the trait, else {@code tolerance}.
     */
    public static String scale(String trait, Map<String, String> displayTraits) {
        String d = displayTraits.get(trait);
        if (d != null && SCALES.contains(d)) {
            return d;
        }
        return DEFAULT_SCALE.getOrDefault(trait, "tolerance");
    }

    /**
     * Name key of {@code trait}: the species override
     * {@code wildroots.tooltip.trait_name.<trait>.<species>} when the
     * species' {@code display_traits} renames the trait (a value that is
     * not a scale, {@code fruit_size: root_size}), else the common name.
     */
    public static String nameKey(String trait, String speciesPath, Map<String, String> displayTraits) {
        String d = displayTraits.get(trait);
        String base = "wildroots.tooltip.trait_name." + trait;
        return d != null && !SCALES.contains(d) ? base + "." + speciesPath : base;
    }

    /** Word key of a step on a scale: {@code wildroots.tooltip.scale.size.4}. */
    public static String wordKey(String scale, int step) {
        return "wildroots.tooltip.scale." + scale + "." + Math.max(1, Math.min(5, step));
    }
}
