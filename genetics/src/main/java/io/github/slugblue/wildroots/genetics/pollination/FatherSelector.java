package io.github.slugblue.wildroots.genetics.pollination;

import io.github.slugblue.wildroots.genetics.api.Defaults;
import io.github.slugblue.wildroots.genetics.api.FatherPick;
import io.github.slugblue.wildroots.genetics.api.SpeciesDef;
import io.github.slugblue.wildroots.genetics.model.Genome;
import io.github.slugblue.wildroots.genetics.random.SplitMix64;
import java.util.ArrayList;
import java.util.List;

/**
 * Fathers of a seed lot, collected once at seed set (GDD §4.1, ADR-0008).
 *
 * <ol>
 *   <li>Neighbours within {@code radius} (horizontal Euclidean) and ±2 in Y are
 *   reservoir-sampled: at most 64 are examined, whatever the input size.</li>
 *   <li>Neighbours sharing an S-allele with the mother are rejected.</li>
 *   <li>Four draws with replacement by weight {@code 1/(1+d²)}; repeats merge,
 *   so there are at most 4 fathers and a father's weight is its draw count.</li>
 *   <li>Up to 4 compatible donors (bees, brush) follow with their own weights.</li>
 * </ol>
 * The RNG key is {@code Hash64(secret, plantUid, cycleIndex)} (§4.3).
 */
public final class FatherSelector {

    /** Fathers and the numbers the lot needs for {@code p_out}. */
    public record Selection(List<FatherPick> fathers, int examined, int compatibleNeighbours) {

        public Selection {
            fathers = List.copyOf(fathers);
        }
    }

    private FatherSelector() {
    }

    public static Selection choose(SpeciesDef s, Genome mother, List<Candidate> nearby, List<Donor> donors,
                                   long rngKey) {
        SplitMix64 rng = new SplitMix64(rngKey);
        int radius = s.pollination().radius();
        long r2 = (long) radius * radius;
        Candidate[] reservoir = new Candidate[Defaults.MAX_CANDIDATES];
        int seen = 0;
        for (Candidate c : nearby) {
            if (c == null || c.horizontalDistanceSq() > r2 || Math.abs(c.dy()) > Defaults.POLLINATION_DY
                    || !c.genome().layout().equals(mother.layout())) {
                continue;
            }
            if (seen < reservoir.length) {
                reservoir[seen] = c;
            } else {
                int j = (int) (Math.floorMod(rng.nextLong(), (long) seen + 1));
                if (j < reservoir.length) {
                    reservoir[j] = c;
                }
            }
            seen++;
        }
        int examined = Math.min(seen, reservoir.length);
        List<Candidate> ok = new ArrayList<>();
        for (int i = 0; i < examined; i++) {
            if (SelfIncompatibility.compatible(s, mother, reservoir[i].genome())) {
                ok.add(reservoir[i]);
            }
        }
        List<FatherPick> fathers = new ArrayList<>();
        if (!ok.isEmpty()) {
            double[] w = new double[ok.size()];
            for (int i = 0; i < w.length; i++) {
                w[i] = 1.0 / (1.0 + ok.get(i).horizontalDistanceSq());
            }
            int[] draws = new int[ok.size()];
            for (int k = 0; k < Defaults.MAX_FATHERS; k++) {
                draws[rng.pick(w)]++;
            }
            for (int i = 0; i < draws.length; i++) {
                if (draws[i] > 0) {
                    fathers.add(new FatherPick(ok.get(i).genome(), draws[i], false));
                }
            }
        }
        int donorCount = 0;
        int from = Math.max(0, donors.size() - Defaults.MAX_DONORS);
        for (Donor d : donors.subList(from, donors.size())) {
            if (d != null && d.genome().layout().equals(mother.layout())
                    && SelfIncompatibility.compatible(s, mother, d.genome()) && donorCount < Defaults.MAX_DONORS) {
                fathers.add(new FatherPick(d.genome(), Math.max(0, d.weight()), true));
                donorCount++;
            }
        }
        return new Selection(fathers, examined, ok.size());
    }
}
