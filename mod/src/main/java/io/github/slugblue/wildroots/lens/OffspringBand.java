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
 * and one {@code f} from the pollen at every locus. A gamete draws its loci
 * independently ({@code Meiosis}); the pollen of one seed comes from one
 * source for all loci ({@link #sumVariance}). A heterozygous locus under
 * selfing gives {@code (a − b)²/2 / (4L)²}, that is (allele effect)² · ½. The phenotype adds the planting noise
 * {@code σ_env} of the species (read from its definition) and, for traits
 * with {@code vigor_affected}, scales {@code G} by the mother's vigor:
 * {@code Var P = V²·Var G + σ_env²}. Pleiotropy and the resource budget are
 * left out: the band is a guide for the player, not a prediction.
 */
public final class OffspringBand {

    private OffspringBand() {
    }

    /**
     * Variance of the allele sum {@code Σ (m + f)} of one trait in a child
     * (before the division by {@code (4L)²}).
     *
     * <p>The mother's gamete draws each locus on its own
     * ({@code Meiosis.gameteInto}): {@code Σ (a − b)² / 4}. The pollen comes
     * from one source for the whole seed ({@code Offspring.chooseFather}):
     * the mother herself (selfing, weight {@code 1 − pOut}) or one lot father
     * by weight. So its sum is a mixture over sources {@code j} with weight
     * {@code π_j}, mean {@code μ_j = Σ (c + d) / 2} and inner variance
     * {@code v_j = Σ (c − d)² / 4}:
     * {@code Var = Σ π_j (v_j + μ_j²) − (Σ π_j μ_j)²}. The choice of the
     * father is shared by every locus, which this keeps (review 1, M1).
     *
     * @param mother  mother alleles per locus, {@code [L][2]}
     * @param fathers per father, alleles per locus {@code [L][2]}
     * @param weights father weights (same order; negative counts as 0)
     * @param pOut    probability of an outside father
     */
    static double sumVariance(int[][] mother, List<int[][]> fathers, double[] weights, double pOut) {
        double total = 0;
        for (int i = 0; i < fathers.size(); i++) {
            total += Math.max(0, weights[i]);
        }
        double out = fathers.isEmpty() || !(total > 0) ? 0 : Math.max(0, Math.min(1, pOut));
        double motherVar = innerVariance(mother);
        double selfMean = mean(mother);
        double first = (1 - out) * selfMean;
        double second = (1 - out) * (motherVar + selfMean * selfMean);
        for (int i = 0; i < fathers.size() && out > 0; i++) {
            double w = out * Math.max(0, weights[i]) / total;
            double mu = mean(fathers.get(i));
            first += w * mu;
            second += w * (innerVariance(fathers.get(i)) + mu * mu);
        }
        double pollenVar = Math.max(0, second - first * first);
        return motherVar + pollenVar;
    }

    /** {@code Σ (a + b) / 2}: the mean allele sum of one gamete. */
    static double mean(int[][] alleles) {
        double m = 0;
        for (int[] l : alleles) {
            m += (l[0] + l[1]) / 2.0;
        }
        return m;
    }

    /** {@code Σ (a − b)² / 4}: the variance of one gamete's allele sum. */
    static double innerVariance(int[][] alleles) {
        double v = 0;
        for (int[] l : alleles) {
            v += (l[0] - l[1]) * (l[0] - l[1]) / 4.0;
        }
        return v;
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
        double varG = loci == 0 ? 0 : sumVariance(mother, fathers, weights, pOut) / (16.0 * loci * loci);
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
        List<FatherPick> picks = lot.fathers();
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
            // a father of another layout is never mixed in: a seed drawn to it is selfed (Offspring.of)
            int[][] own = alleles(m, s);
            List<int[][]> fathers = picks.stream()
                    .map(f -> f.genome().layout().equals(m.layout()) ? alleles(f.genome(), s) : own).toList();
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
