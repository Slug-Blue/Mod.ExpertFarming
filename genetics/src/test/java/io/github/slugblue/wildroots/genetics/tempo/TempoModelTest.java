package io.github.slugblue.wildroots.genetics.tempo;

import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.slugblue.wildroots.genetics.population.VillageTemplate;
import io.github.slugblue.wildroots.genetics.random.Hash64;
import io.github.slugblue.wildroots.genetics.testutil.Carrot;
import java.util.Arrays;
import java.util.Map;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * WR-112: the canonical tempo model (GDD §12.5) on the real engine. Carrot,
 * N 24, k 6, 3 patches: the median generation of the first landrace and of
 * the first cultivar must lie within the GDD §12.4 corridor ± 1 generation
 * (landrace 3, cultivar 11). The fast run (50 repeats) is part of
 * {@code :genetics:test}; the full run (500) is {@code ./gradlew :genetics:tempoFull}.
 */
class TempoModelTest {

    /** GDD §12.4, carrot row. */
    static final double LANDRACE_MEDIAN = 3;
    static final double CULTIVAR_MEDIAN = 11;

    static TempoModel.Params carrot() {
        VillageTemplate village = VillageTemplate.fromLandrace(Carrot.SPECIES, Carrot.EASTERN,
                Map.of(Carrot.Y2, 0.30), Map.of());
        return new TempoModel.Params(Carrot.SPECIES, Carrot.EASTERN, Carrot.CARROT, village, Carrot.Y2,
                24, 6, 3, 4, 30,
                Carrot.SPECIES.pollination().bees().orElseThrow().outcross(), 0.9,
                0.15, 0.075, 5, 0.05, 0.25);
    }

    static int[][] runs(TempoModel.Params params, int repeats, long seed) {
        TempoModel m = new TempoModel(params);
        int[] landrace = new int[repeats];
        int[] cultivar = new int[repeats];
        for (int i = 0; i < repeats; i++) {
            TempoModel.Run r = m.run(Hash64.of(seed, i));
            landrace[i] = r.landraceGen();
            cultivar[i] = r.cultivarGen();
        }
        return new int[][] {landrace, cultivar};
    }

    static String report(String name, int[][] r) {
        int fails = (int) Arrays.stream(r[1]).filter(x -> x < 0).count();
        return name + ": landrace median " + TempoModel.median(r[0]) + ", cultivar median "
                + TempoModel.median(r[1]) + ", p75 " + percentile(r[1], 0.75) + ", failures " + fails + "/"
                + r[1].length;
    }

    static double percentile(int[] xs, double q) {
        int[] s = Arrays.stream(xs).map(x -> x < 0 ? Integer.MAX_VALUE : x).sorted().toArray();
        return s[(int) Math.min(s.length - 1, Math.floor(q * s.length))];
    }

    static void assertCorridor(int[][] r) {
        double l = TempoModel.median(r[0]);
        double c = TempoModel.median(r[1]);
        assertTrue(Math.abs(l - LANDRACE_MEDIAN) <= 1, "landrace median " + l);
        assertTrue(Math.abs(c - CULTIVAR_MEDIAN) <= 1, "cultivar median " + c);
    }

    @Test
    void carrotFastRunWithinGddCorridor() {
        int[][] r = runs(carrot(), 50, 112);
        System.out.println("[WR-112] " + report("carrot, 50 repeats", r));
        assertCorridor(r);
    }

    @Test
    @Tag("tempo-full")
    void carrotFullRunWithinGddCorridor() {
        int[][] r = runs(carrot(), 500, 1120);
        System.out.println("[WR-112] " + report("carrot, 500 repeats", r));
        assertCorridor(r);
    }
}
