package io.github.slugblue.wildroots.genetics.api;

import io.github.slugblue.wildroots.genetics.model.TraitId;
import io.github.slugblue.wildroots.genetics.validation.Problem;
import java.util.List;
import java.util.Map;

/**
 * The only place for default values and design constants of the engine
 * (docs/05-architecture.md §4.2, GDD §14). Everything a definition can set
 * comes from {@link SpeciesDef}/{@link CultivarDef}; when the JSON omits a
 * value, the loader takes it from here through {@link #or} and logs the
 * returned {@code WARN} problem. Numbers marked {@code [PLACEHOLDER]} in the
 * GDD live here so that tuning touches one file.
 */
public final class Defaults {

    private Defaults() {
    }

    // ------------------------------------------------------------ species

    /** Budget limit (GDD §3.10) {@code [PLACEHOLDER]}. */
    public static final double BUDGET_LIMIT = 2.6;
    /** Budget weights (GDD §3.10). */
    public static final Map<TraitId, Double> BUDGET_WEIGHTS = Map.of(
            TraitId.of("yield"), 1.0, TraitId.of("fruit_size"), 1.0, TraitId.of("growth_speed"), 0.75,
            TraitId.of("cold_tolerance"), 0.5, TraitId.of("heat_tolerance"), 0.5,
            TraitId.of("drought_tolerance"), 0.5);
    /** Background wild frequencies of alleles 0/1/2 (GDD §3.2). */
    public static final AlleleFreq BACKGROUND_FREQ = new AlleleFreq(0.60, 0.35, 0.05);
    /** Elite-locus frequencies (GDD §3.2). */
    public static final AlleleFreq ELITE_FREQ = new AlleleFreq(0.30, 0.40, 0.30);
    /** Elite-locus frequencies of a rich patch (worldgen §5.1) {@code [PLACEHOLDER]}. */
    public static final AlleleFreq RICH_ELITE_FREQ = new AlleleFreq(0.15, 0.40, 0.45);
    /** Extra elite loci of a rich patch (worldgen §5.1). */
    public static final Map<TraitId, Integer> RICH_EXTRA_LOCI = Map.of(TraitId.of("yield"), 1,
            TraitId.of("fruit_size"), 1);
    /** Load loci, frequency of {@code d}, step (GDD §3.7). */
    public static final LoadDef LOAD = new LoadDef(8, 0.25, 0.90);
    /** Mutation rates (GDD §3.8). */
    public static final MutationRates MUTATION = new MutationRates(0.004, 0.001);
    /** Loci of a species-specific quantitative trait (GDD §3.4). */
    public static final int EXTRA_TRAIT_LOCI = 4;
    /** σ_env of a species-specific quantitative trait (GDD §3.4). */
    public static final double EXTRA_TRAIT_SIGMA = 0.06;
    /** S-alleles present in one wild patch (GDD §3.6). */
    public static final int S_ALLELES_PER_PATCH = 6;
    /** Seed set without a pollinator when the JSON omits it. */
    public static final double SEED_SET_NO_POLLINATOR = 1.0;

    /** Common quantitative traits of GDD §3.3: loci, σ_env, vigor_affected. */
    public static final List<QuantTrait> COMMON_TRAITS = List.of(
            new QuantTrait(TraitId.of("yield"), 6, 0.07, true),
            new QuantTrait(TraitId.of("fruit_size"), 6, 0.07, true),
            new QuantTrait(TraitId.of("growth_speed"), 4, 0.05, true),
            new QuantTrait(TraitId.of("flavor"), 4, 0.06, false),
            new QuantTrait(TraitId.of("cold_tolerance"), 3, 0.04, false),
            new QuantTrait(TraitId.of("heat_tolerance"), 3, 0.04, false),
            new QuantTrait(TraitId.of("drought_tolerance"), 3, 0.04, false));

    /** Defaults of a quantitative trait by id: the GDD §3.3 row, or the §3.4 extra-trait values. */
    public static QuantTrait quantTrait(TraitId id) {
        for (QuantTrait q : COMMON_TRAITS) {
            if (q.id().equals(id)) {
                return q;
            }
        }
        return new QuantTrait(id, EXTRA_TRAIT_LOCI, EXTRA_TRAIT_SIGMA, false);
    }

    // ------------------------------------------------------------ cultivars

    /** Hysteresis of cultivar status (GDD §6.2). */
    public static final double HYSTERESIS = 0.05;

    // ------------------------------------------------------------ formulas (design constants)

    /** Grade bounds of {@code Q}: 1 below 0.35, 2 below 0.55, 3 below 0.75, else 4 (GDD §6.4). */
    public static final double GRADE_2_AT = 0.35;
    public static final double GRADE_3_AT = 0.55;
    public static final double GRADE_4_AT = 0.75;
    /** Highest grade of a bitter harvest (GDD §6.4). */
    public static final int BITTER_MAX_GRADE = 2;
    /** Grade cap by the lowest tier of main ingredients: wild 1, landrace 3, cultivar 4 (GDD §6.6). */
    public static int processingCap(int tier) {
        return tier <= 0 ? 1 : tier == 1 ? 3 : 4;
    }
    /** Highest grade overall. */
    public static final int MAX_GRADE = 4;
    /** Planting noise ε is clipped to ±0.5 and stored in steps of 1/256 (docs/05-architecture.md §5.2). */
    public static final double NOISE_CLIP = 0.5;
    /** Environment: penalty per unit outside the temperature window (−0.10 per 0.1), cap (GDD §3.5). */
    public static final double ENV_TEMP_SLOPE = 1.0;
    public static final double ENV_TEMP_CAP = 0.30;
    /** Window widening by tolerance: {@code 0.6·P} (GDD §3.3). */
    public static final double TOLERANCE_WIDENING = 0.6;
    /** Dry farmland: {@code −0.05·(1 − P_drought)} (GDD §3.5). */
    public static final double ENV_DRY = 0.05;
    /** Light below 9: −0.05 (GDD §3.5). */
    public static final int ENV_DARK_LIGHT = 9;
    public static final double ENV_DARK = 0.05;
    /** Compost: up to +0.05 (GDD §3.5). */
    public static final double ENV_COMPOST_CAP = 0.05;
    /** Seeds: {@code (3 + 9·P_yield)·seed_set·(shatters ? 0.35 : 1)} (GDD §4.1). */
    public static final double SEEDS_BASE = 3;
    public static final double SEEDS_PER_YIELD = 9;
    public static final double SHATTER_KEEP = 0.35;
    /** Pollination limits (GDD §4.1, SEC-30). */
    public static final int MAX_CANDIDATES = 64;
    public static final int MAX_FATHERS = 4;
    public static final int MAX_DONORS = 4;
    /** {@code p_out = base·min(1, n/3)} (GDD §4.1). */
    public static final int OUTCROSS_FULL_AT = 3;
    /** Brush outcrossing (GDD §4.4). */
    public static final double BRUSH_OUTCROSS = 0.90;
    /** Vertical reach of neighbour pollination, ±2 blocks (GDD §4.1). */
    public static final int POLLINATION_DY = 2;
    /** Village landrace (GDD §9.5): G = landrace threshold + 0.05, wild_F 0.3, load q 0.15. */
    public static final double VILLAGE_MARGIN = 0.05;
    public static final double VILLAGE_WILD_F = 0.3;
    public static final double VILLAGE_LOAD_FREQ = 0.15;

    // ------------------------------------------------------------ resolution with a warning

    /** A value taken from JSON or substituted by a default, with the warning to log. */
    public record Resolved<T>(T value, Problem warning) {

        public boolean defaulted() {
            return warning != null;
        }
    }

    /**
     * Returns {@code value} when present; otherwise {@code fallback} and a
     * {@code WARN} problem at {@code path} for the log (GDD §14).
     */
    public static <T> Resolved<T> or(T value, T fallback, String path) {
        if (value != null) {
            return new Resolved<>(value, null);
        }
        return new Resolved<>(fallback, Problem.warn(path, "missing, default " + fallback + " applied"));
    }

    /** Same as {@link #or} and appends the warning, if any, to {@code sink}. */
    public static <T> T or(T value, T fallback, String path, List<Problem> sink) {
        Resolved<T> r = or(value, fallback, path);
        if (r.warning() != null) {
            sink.add(r.warning());
        }
        return r.value();
    }
}
