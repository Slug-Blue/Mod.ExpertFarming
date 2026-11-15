package io.github.slugblue.wildroots.lens;

import io.github.slugblue.wildroots.genetics.api.Defaults;
import io.github.slugblue.wildroots.genetics.api.Environment;
import io.github.slugblue.wildroots.genetics.api.Phenotype;
import io.github.slugblue.wildroots.genetics.api.QuantTrait;
import io.github.slugblue.wildroots.genetics.api.SpeciesDef;
import io.github.slugblue.wildroots.genetics.model.TraitId;
import io.github.slugblue.wildroots.genetics.api.Traits;
import java.util.ArrayList;
import java.util.List;

/**
 * The environment modifiers of a plant that the lens window lists
 * (playtest 2, item 6): the same terms of {@code E} that
 * {@code PhenotypeCalculator} adds to {@code vigor_affected} traits (GDD
 * §3.5), as flags. Cold / hot: the cell's temperature is outside the
 * species' window widened by {@code 0.6·P} of the tolerance; dry: the
 * farmland is not moist; dark: light below 9; compost: a bonus. Pure (JUnit).
 */
public final class EnvModifiers {

    public static final int COLD = 1;
    public static final int HOT = 1 << 1;
    public static final int DRY = 1 << 2;
    public static final int DARK = 1 << 3;
    public static final int COMPOST = 1 << 4;
    /** Every known bit. */
    public static final int ALL = COLD | HOT | DRY | DARK | COMPOST;

    /** Lang key of each bit, in bit order. */
    static final String[] KEYS = {"cold", "hot", "dry", "dark", "compost"};
    public static final String KEY_PREFIX = "wildroots.window.lens.env.";

    private EnvModifiers() {
    }

    /** The modifiers active for a plant with phenotype {@code p} in {@code env}. */
    public static int of(SpeciesDef def, Environment env, Phenotype p) {
        double lo = def.tempMin() - Defaults.TOLERANCE_WIDENING * tolerance(def, p, Traits.COLD_TOLERANCE);
        double hi = def.tempMax() + Defaults.TOLERANCE_WIDENING * tolerance(def, p, Traits.HEAT_TOLERANCE);
        double t = env.temperature();
        int bits = 0;
        if (t < lo) {
            bits |= COLD;
        } else if (t > hi) {
            bits |= HOT;
        }
        // dry farmland costs 0.05 · (1 − P_drought): a fully drought-hardy plant loses nothing
        if (!env.moisturized() && tolerance(def, p, Traits.DROUGHT_TOLERANCE) < 1) {
            bits |= DRY;
        }
        if (env.light() < Defaults.ENV_DARK_LIGHT) {
            bits |= DARK;
        }
        if (env.compostBonus() > 0) {
            bits |= COMPOST;
        }
        return bits;
    }

    /** {@code P} of a tolerance as the calculator sees it when it computes {@code E}: 0 if vigor-affected or absent. */
    static double tolerance(SpeciesDef def, Phenotype p, TraitId t) {
        for (QuantTrait q : def.quantitative()) {
            if (q.id().equals(t)) {
                return q.vigorAffected() ? 0 : p.pOr(t, 0);
            }
        }
        return 0;
    }

    /** Lang keys of the set bits, in bit order; unknown bits are ignored. */
    public static List<String> keys(int bits) {
        List<String> out = new ArrayList<>();
        for (int i = 0; i < KEYS.length; i++) {
            if ((bits & (1 << i)) != 0) {
                out.add(KEY_PREFIX + KEYS[i]);
            }
        }
        return out;
    }

    /** Whether the bit lowers the plant (all but compost). */
    public static boolean negative(String key) {
        return !key.equals(KEY_PREFIX + "compost");
    }
}
