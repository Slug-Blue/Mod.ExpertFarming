package io.github.slugblue.wildroots.genetics.phenotype;

import io.github.slugblue.wildroots.genetics.api.Defaults;
import io.github.slugblue.wildroots.genetics.api.Environment;
import io.github.slugblue.wildroots.genetics.api.Phenotype;
import io.github.slugblue.wildroots.genetics.api.QualTrait;
import io.github.slugblue.wildroots.genetics.model.Genome;
import io.github.slugblue.wildroots.genetics.random.Gaussian;
import io.github.slugblue.wildroots.genetics.random.SplitMix64;

/**
 * Phenotype {@code P = clamp(G_eff·V + E + ε, 0, 1)} (GDD §3.1, §3.5, §3.10).
 *
 * <p>Order: {@code G} of every trait → layer 1 (pairs, from the original
 * {@code G}) → layer 2 (budget) → {@code P} of traits without
 * {@code vigor_affected}, which take neither {@code V} nor {@code E} →
 * {@code E} from those {@code P} (tolerances widen the window) →
 * {@code P} of traits with {@code vigor_affected}.
 */
public final class PhenotypeCalculator {

    private PhenotypeCalculator() {
    }

    /** ε at planting: N(0, σ_env) per trait, clipped and quantised (GDD §3.1). */
    public static NoiseVector rollNoise(CompiledSpecies cs, long rngKey) {
        Gaussian gauss = new Gaussian(new SplitMix64(rngKey));
        byte[] b = new byte[cs.quant.length];
        for (int i = 0; i < b.length; i++) {
            b[i] = NoiseVector.quantise(gauss.next(0, cs.sigma[i]));
        }
        return new NoiseVector(b);
    }

    /** {@code G_eff} without environment and noise (classification, GDD §6.2). */
    public static double[] gEff(CompiledSpecies cs, Genome g) {
        double[] gv = genotypic(cs, g);
        double[] pair = Pleiotropy.apply(gv, cs.pairTarget, cs.pairAgainst, cs.pairC, cs.pairTheta);
        return ResourceBudget.apply(pair, cs.budgetWeight, cs.budgetLimit);
    }

    static double[] genotypic(CompiledSpecies cs, Genome g) {
        double[] gv = new double[cs.quant.length];
        for (int i = 0; i < gv.length; i++) {
            gv[i] = GenotypicValue.of(g, cs.quant[i]);
        }
        return gv;
    }

    public static Phenotype compute(CompiledSpecies cs, Genome g, Environment env, NoiseVector noise) {
        double[] gv = genotypic(cs, g);
        double[] pair = Pleiotropy.apply(gv, cs.pairTarget, cs.pairAgainst, cs.pairC, cs.pairTheta);
        double[] eff = ResourceBudget.apply(pair, cs.budgetWeight, cs.budgetLimit);
        double v = Vigor.of(g, cs.layout.load(), cs.def.load().step());
        int n = eff.length;
        double[] p = new double[n];
        for (int i = 0; i < n; i++) {
            if (!cs.vigorAffected[i]) {
                p[i] = clamp01(eff[i] + noise.eps(i));
            }
        }
        double e = environment(cs, env, p);
        for (int i = 0; i < n; i++) {
            if (cs.vigorAffected[i]) {
                p[i] = clamp01(eff[i] * v + e + noise.eps(i));
            }
        }
        boolean[] dom = new boolean[cs.qual.length];
        for (int i = 0; i < dom.length; i++) {
            QualTrait q = cs.qualDefs[i];
            dom[i] = q.alleles() == 2
                    && q.domesticPhenotype(g.allele(cs.qual[i], 0, 0), g.allele(cs.qual[i], 0, 1));
        }
        boolean bitter = cs.bitterIndex >= 0 && !dom[cs.bitterIndex];
        return new Phenotype(cs.quantIds, gv, eff, p, v, cs.qualIds, dom, bitter);
    }

    /**
     * {@code E} for {@code vigor_affected} traits (GDD §3.5): temperature out of
     * the window widened by {@code 0.6·P} of the tolerances, −0.10 per 0.1 down
     * to −0.30; dry farmland {@code −0.05·(1 − P_drought)}; light below 9
     * −0.05; compost up to +0.05.
     */
    static double environment(CompiledSpecies cs, Environment env, double[] p) {
        double lo = cs.def.tempMin() - Defaults.TOLERANCE_WIDENING * (cs.coldIndex >= 0 ? p[cs.coldIndex] : 0);
        double hi = cs.def.tempMax() + Defaults.TOLERANCE_WIDENING * (cs.heatIndex >= 0 ? p[cs.heatIndex] : 0);
        double t = env.temperature();
        double out = t < lo ? lo - t : t > hi ? t - hi : 0;
        double e = -Math.min(Defaults.ENV_TEMP_CAP, Defaults.ENV_TEMP_SLOPE * out);
        if (!env.moisturized()) {
            e -= Defaults.ENV_DRY * (1 - (cs.droughtIndex >= 0 ? p[cs.droughtIndex] : 0));
        }
        if (env.light() < Defaults.ENV_DARK_LIGHT) {
            e -= Defaults.ENV_DARK;
        }
        e += Math.max(0, Math.min(Defaults.ENV_COMPOST_CAP, env.compostBonus()));
        return Double.isFinite(e) ? e : 0;
    }

    static double clamp01(double x) {
        return x > 1 ? 1 : x >= 0 ? x : 0; // NaN → 0
    }
}
