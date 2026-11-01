package io.github.slugblue.wildroots.genetics.validation;

import java.util.List;

/**
 * Numeric limits of definitions, SEC-28 (`09-security.md` §1.6.6). The
 * engine validator and the Codec layer of {@code :mod} use the same numbers;
 * {@code core.safety} re-exports this class instead of copying it
 * (security Δ8). Every number must be finite: {@code NaN} and
 * {@code ±Infinity} are errors even if the JSON reader let them through.
 */
public final class DataLimits {

    private DataLimits() {
    }

    /** Species, cultivars and templates per load: {@code DefinitionValidator#validateSet}. */
    public static final int MAX_SPECIES = 512;
    public static final int MAX_CULTIVARS = 4096;
    public static final int MAX_CULTIVARS_PER_SPECIES = 64;
    /** Checked by the quality-role loader of {@code :mod} (WR-125): roles are not engine records. */
    public static final int MAX_QUALITY_ROLES = 1024;
    public static final int MAX_VILLAGE_TEMPLATES = 64;
    public static final int MAX_QUANT_TRAITS = 32;
    public static final int MAX_QUAL_TRAITS = 16;
    public static final int MIN_LOCI = 1;
    public static final int MAX_LOCI = 16;
    public static final int MAX_LOAD_LOCI = 16;
    public static final int MIN_S_ALLELES = 2;
    public static final int MAX_S_ALLELES = 16;
    public static final int MAX_LAYOUT_BITS = 512;
    public static final int MAX_CANONICAL_LENGTH = 4096;
    public static final int MAX_TRADEOFFS = 64;
    public static final double MAX_TRADEOFF_C = 10;
    public static final double MAX_BUDGET_WEIGHT = 10;
    public static final double MAX_BUDGET_LIMIT = 32;
    public static final double MAX_MUTATION = 0.05;
    public static final double FREQ_SUM_TOLERANCE = 1e-6;
    public static final int MAX_RADIUS = 16;
    public static final int MAX_CARRY_RANGE = 64;
    public static final double MIN_STAGE_MIN = 0.1;
    public static final double MAX_STAGE_MIN = 1440;
    /** Dormancy {@code U(a, b)}: not part of {@code SpeciesDef}; checked by the species loader (WR-125). */
    public static final double MAX_DORMANCY_MIN = 60;
    public static final int MAX_REQUIREMENTS = 32;
    public static final int MIN_PRIORITY = -1000;
    public static final int MAX_PRIORITY = 1000;
    public static final double MAX_HYSTERESIS = 0.5;
    public static final int MAX_QUALITY_AXES = 16;
    /** {@code food_reference}, recipe outputs, colours, display traits, translation keys: item and
     * client data of {@code :mod}, checked by its loaders (WR-125); the engine never sees them. */
    public static final int MAX_FOOD_NUTRITION = 20;
    public static final double MAX_FOOD_SATURATION = 2;
    public static final int MAX_OUTPUT_PER_GRADE = 64;
    /** Enforced by {@code ProcessingQuality} (extra slots ignored) and rejected by the recipe loader (WR-125). */
    public static final int MAX_BOOSTERS = 8;
    public static final int MAX_COLOR = 0xFFFFFF;
    public static final int MAX_DISPLAY_TRAITS = 16;
    public static final int MAX_TRANSLATION_KEY = 128;
    public static final int MAX_ID_LENGTH = 128;
    /** Depth of reference resolution (SEC-29). */
    public static final int MAX_REFERENCE_DEPTH = 16;
    /** Absolute value bound of the temperature window (vanilla biomes are −0.7…2.0). */
    public static final double MAX_ABS_TEMPERATURE = 4;
    /** σ_env bound: ε is clipped to ±0.5 anyway. */
    public static final double MAX_SIGMA_ENV = 0.5;

    /** Checks finiteness and {@code [lo, hi]}; adds an ERROR and returns false otherwise. */
    public static boolean range(double v, double lo, double hi, String path, List<Problem> sink) {
        if (!Double.isFinite(v)) {
            sink.add(Problem.error(path, "not a finite number: " + v));
            return false;
        }
        if (v < lo || v > hi) {
            sink.add(Problem.error(path, v + " outside [" + lo + ", " + hi + "]"));
            return false;
        }
        return true;
    }

    /**
     * For loaders: a JSON number that must become an {@code int}. Non-finite
     * or fractional values give an ERROR and {@code fallback}.
     */
    public static int toInt(double v, int fallback, String path, List<Problem> sink) {
        if (!Double.isFinite(v)) {
            sink.add(Problem.error(path, "not a finite number: " + v));
            return fallback;
        }
        if (v != Math.rint(v) || v < Integer.MIN_VALUE || v > Integer.MAX_VALUE) {
            sink.add(Problem.error(path, "integer expected: " + v));
            return fallback;
        }
        return (int) v;
    }
}
