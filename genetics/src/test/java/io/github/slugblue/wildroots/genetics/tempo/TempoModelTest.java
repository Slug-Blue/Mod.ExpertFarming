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
 * (landrace 3, cultivar 10). The fast run (50 repeats) is part of
 * {@code :genetics:test}; the full run (500) is {@code ./gradlew :genetics:tempoFull}.
 */
class TempoModelTest {

    /** GDD §12.4, carrot row «Морковь (порт WR-112, v0.5)»: landrace 3, cultivar median 10. */
    static final double LANDRACE_MEDIAN = 3;
    static final double CULTIVAR_MEDIAN = 10;

    static TempoModel.Params carrot() {
        return carrot(Carrot.SPECIES.pollination().bees().orElseThrow().outcross());
    }

    /** Carrot with a given outcrossing share of the bed (0.95 with bees, 0.60 without). */
    static TempoModel.Params carrot(double pOut) {
        VillageTemplate village = VillageTemplate.fromLandrace(Carrot.SPECIES, Carrot.EASTERN,
                Map.of(Carrot.Y2, 0.30), Map.of());
        return new TempoModel.Params(Carrot.SPECIES, Carrot.EASTERN, Carrot.CARROT, village, Carrot.Y2,
                24, 6, 3, 4, 30,
                pOut, 0.9,
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
                + TempoModel.median(r[1]) + " (" + String.format(java.util.Locale.ROOT, "%.2f",
                lineHours(TempoModel.median(r[1]))) + " h), p75 " + percentile(r[1], 0.75) + " ("
                + String.format(java.util.Locale.ROOT, "%.2f", lineHours(percentile(r[1], 0.75)))
                + " h), failures " + fails + "/" + r[1].length + ", cycle "
                + String.format(java.util.Locale.ROOT, "%.1f", cycleMinutes()) + " min";
    }

    static double percentile(int[] xs, double q) {
        int[] s = Arrays.stream(xs).map(x -> x < 0 ? Integer.MAX_VALUE : x).sorted().toArray();
        return s[(int) Math.min(s.length - 1, Math.floor(q * s.length))];
    }

    /**
     * Mean generation length, minutes (GDD §12.1): {@code (to_harvest + seed_stage)/m + 2.5}
     * averaged over wild (m 0.88) and advanced (m 1.1) plants.
     */
    static double cycleMinutes() {
        double t = Carrot.SPECIES.timing().toHarvestMin() + Carrot.SPECIES.timing().seedStageMin();
        return (t / 0.88 + t / 1.1) / 2 + 2.5;
    }

    /** Line length in hours (GDD §12.3): {@code (g − 1)·cycle·1.3 + 65 min}. */
    static double lineHours(double generations) {
        return ((generations - 1) * cycleMinutes() * 1.3 + 65) / 60;
    }

    static void assertCorridor(int[][] r, boolean full) {
        double l = TempoModel.median(r[0]);
        double c = TempoModel.median(r[1]);
        assertTrue(Math.abs(l - LANDRACE_MEDIAN) <= 1, "landrace median " + l);
        assertTrue(Math.abs(c - CULTIVAR_MEDIAN) <= 1, "cultivar median " + c);
        assertTrue(lineHours(c) >= 5.0, "line " + lineHours(c) + " h < 5.0");
        if (full) {
            assertTrue(lineHours(percentile(r[1], 0.75)) <= 8.0, "p75 " + lineHours(percentile(r[1], 0.75)) + " h");
            long fails = Arrays.stream(r[1]).filter(x -> x < 0).count();
            assertTrue(fails <= 0.05 * r[1].length, "failures " + fails);
        }
    }

    @Test
    void carrotFastRunWithinGddCorridor() {
        int[][] r = runs(carrot(), 50, 112);
        System.out.println("[WR-112] " + report("carrot, 50 repeats", r));
        assertCorridor(r, false);
    }

    @Test
    @Tag("tempo-full")
    void carrotFullRunWithinGddCorridor() {
        int[][] r = runs(carrot(), 500, 1120);
        System.out.println("[WR-112] " + report("carrot, 500 repeats", r));
        assertCorridor(r, true);
    }

    /** Control: the same bed without bees (p_out 0.60, GDD §4.2). Reported, not asserted. */
    @Test
    @Tag("tempo-full")
    void carrotWithoutBeesControl() {
        int[][] r = runs(carrot(0.60), 500, 1121);
        System.out.println("[WR-112] " + report("carrot without bees, 500 repeats", r));
        assertTrue(r[1].length == 500);
    }
}
