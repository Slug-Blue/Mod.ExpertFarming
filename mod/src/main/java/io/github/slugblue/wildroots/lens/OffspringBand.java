package io.github.slugblue.wildroots.lens;

import io.github.slugblue.wildroots.genetics.api.FatherPick;
import io.github.slugblue.wildroots.genetics.api.LotRecord;
import io.github.slugblue.wildroots.genetics.api.QuantTrait;
import io.github.slugblue.wildroots.genetics.api.SpeciesDef;
import io.github.slugblue.wildroots.genetics.model.Genome;
import io.github.slugblue.wildroots.genetics.model.Locus;
import java.util.List;

/**
 * The offspring band of the lens (playtest 2, item 6; owner decision
 * 2026-11-15): how far the seeds of a lot are expected to spread around
 * the mother's value, one standard deviation per quantitative trait.
 * Analytic and deterministic, no random draws; the server computes it at
 * inspection and stores only the steps it reaches ({@link LensRow}).
 *
 * <p>A child's {@code G} of a trait with {@code L} loci is
 * {@code Σ (m + f) / (4L)}: one allele {@code m} from the mother's gamete
 * and one {@code f} from the pollen at every locus. Loci are taken as
 * independent (linkage is left out). At a locus with mother alleles
 * {@code a, b} the mother's allele has variance {@code (a − b)² / 4}; the
 * pollen allele comes from the mother (selfing, probability {@code 1 − pOut})
 * or from a lot father by weight, and its variance is that of the mixture.
 * So a heterozygous locus under selfing gives {@code (a − b)²/2 / (4L)²},
 * that is (allele effect)² · ½. The phenotype adds the planting noise
 * {@code σ_env} of the species (read from its definition) and, for traits
 * with {@code vigor_affected}, scales {@code G} by the mother's vigor:
 * {@code Var P = V²·Var G + σ_env²}. Pleiotropy and the resource budget are
 * left out: the band is a guide for the player, not a prediction.
 */
public final class OffspringBand {

    private OffspringBand() {
    }

    /**
     * Child variance of {@code G} at one locus.
     *
     * @param a        mother allele 1 (0–2)
     * @param b        mother allele 2
     * @param fathers  alleles {@code {c, d}} of the lot fathers at this locus
     * @param weights  father weights (same order; negative counts as 0)
     * @param pOut     probability of an outside father
     */
    static double locusVariance(int a, int b, int[][] fathers, double[] weights, double pOut) {
        double motherVar = (a - b) * (a - b) / 4.0;
        double total = 0;
        for (int i = 0; i < fathers.length; i++) {
            total += Math.max(0, weights[i]);
        }
        double out = fathers.length == 0 || !(total > 0) ? 0 : Math.max(0, Math.min(1, pOut));
        // pollen: a mixture of the mother (selfing) and the fathers by weight
        double mean = (1 - out) * (a + b) / 2.0;
        double square = (1 - out) * (a * a + b * b) / 2.0;
        for (int i = 0; i < fathers.length && out > 0; i++) {
            double w = out * Math.max(0, weights[i]) / total;
            int c = fathers[i][0];
            int d = fathers[i][1];
            mean += w * (c + d) / 2.0;
            square += w * (c * c + d * d) / 2.0;
        }
        double pollenVar = Math.max(0, square - mean * mean);
        return motherVar + pollenVar;
    }

    /**
     * Expected child standard deviation of {@code P} of one trait.
     *
     * @param mother   mother alleles per locus, {@code [L][2]}
     * @param fathers  per father, alleles per locus {@code [L][2]}
     * @param weights  father weights
     * @param pOut     probability of an outside father
     * @param sigmaEnv planting noise σ of the trait
     * @param scale    factor on {@code G} (the vigor for {@code vigor_affected}, else 1)
     */
    public static double childSd(int[][] mother, List<int[][]> fathers, double[] weights, double pOut,
                                 double sigmaEnv, double scale) {
        int loci = mother.length;
        double varG = 0;
        for (int l = 0; l < loci; l++) {
            int[][] f = new int[fathers.size()][];
            for (int i = 0; i < f.length; i++) {
                f[i] = fathers.get(i)[l];
            }
            varG += locusVariance(mother[l][0], mother[l][1], f, weights, pOut);
        }
        varG = loci == 0 ? 0 : varG / (16.0 * loci * loci);
        double s = Double.isFinite(scale) ? scale : 1;
        double env = Double.isFinite(sigmaEnv) ? sigmaEnv : 0;
        return Math.sqrt(s * s * varG + env * env);
    }

    /** The noise only: a planted mother root keeps its own genome, so its band is σ_env. */
    public static double noiseOnly(double sigmaEnv) {
        return Double.isFinite(sigmaEnv) ? Math.abs(sigmaEnv) : 0;
    }

    /** Lowest and highest step 1–5 of {@code p ± sd}. */
    public static int[] steps(double p, double sd) {
        double v = Double.isFinite(p) ? Math.max(0, Math.min(1, p)) : 0;
        double s = Double.isFinite(sd) ? Math.abs(sd) : 0;
        return new int[] {LensScale.step(v - s), LensScale.step(v + s)};
    }

    /** Alleles of {@code g} in section {@code s}: {@code [L][2]}. */
    static int[][] alleles(Genome g, Locus s) {
        int[][] out = new int[s.loci()][2];
        for (int l = 0; l < s.loci(); l++) {
            out[l][0] = g.allele(s, l, 0);
            out[l][1] = g.allele(s, l, 1);
        }
        return out;
    }

    /**
     * Child standard deviation of every quantitative trait of a lot, in the
     * order of {@code def.quantitative()}.
     *
     * @param vigor the mother's vigor {@code V}
     */
    public static double[] ofLot(LotRecord lot, SpeciesDef def, double vigor) {
        Genome m = lot.mother();
        List<FatherPick> picks = lot.fathers().stream()
                .filter(f -> f.genome().layout().equals(m.layout())).toList(); // others are never mixed in
        double[] weights = new double[picks.size()];
        for (int i = 0; i < weights.length; i++) {
            weights[i] = picks.get(i).weight();
        }
        List<QuantTrait> traits = def.quantitative();
        double[] out = new double[traits.size()];
        for (int i = 0; i < out.length; i++) {
            QuantTrait q = traits.get(i);
            Locus s = m.layout().section(q.id());
            if (s == null) {
                out[i] = noiseOnly(q.sigmaEnv());
                continue;
            }
            List<int[][]> fathers = picks.stream().map(f -> alleles(f.genome(), s)).toList();
            out[i] = childSd(alleles(m, s), fathers, weights, lot.pOut(), q.sigmaEnv(),
                    q.vigorAffected() ? vigor : 1);
        }
        return out;
    }

    /** σ_env of every quantitative trait, in the order of {@code def.quantitative()}. */
    public static double[] noiseOnly(SpeciesDef def) {
        List<QuantTrait> traits = def.quantitative();
        double[] out = new double[traits.size()];
        for (int i = 0; i < out.length; i++) {
            out[i] = noiseOnly(traits.get(i).sigmaEnv());
        }
        return out;
    }
}
