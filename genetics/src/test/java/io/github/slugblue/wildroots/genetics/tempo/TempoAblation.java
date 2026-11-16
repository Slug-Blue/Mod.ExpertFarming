package io.github.slugblue.wildroots.genetics.tempo;

import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.slugblue.wildroots.genetics.api.CultivarDef;
import io.github.slugblue.wildroots.genetics.population.VillageTemplate;
import io.github.slugblue.wildroots.genetics.random.Hash64;
import io.github.slugblue.wildroots.genetics.testutil.Carrot;
import io.github.slugblue.wildroots.genetics.testutil.DefJson;
import io.github.slugblue.wildroots.genetics.testutil.MiniJson;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * WR-705 harness (S1 review M2): the tempo model with many repeats on common seeds, for variants of the carrot
 * cultivar (the shipped one has no {@code color_r} requirement since the owner decision of 2026-11-16), reported as the censored mean of the generation of the first {@code carrot} (failures count as
 * {@value #CENSOR}), P(cultivar by generation g) for g = 8, 10, 12, 15 and the failures with a 95% interval.
 * Medians of whole generations are not used (the old median sat on the boundary). Run:
 * {@code WILDROOTS_TEMPO_REPEATS=4000 ./gradlew :genetics:tempoFull}. Reported, not asserted.
 */
class TempoAblation {

    static final int CENSOR = 31;
    static final int[] GENS = {8, 10, 12, 15};

    /** The carrot cultivar as loaded, with {@code color_r: wild} ADDED to its qualitative requirements (the S1 rule). */
    @SuppressWarnings("unchecked")
    static CultivarDef carrotWithR() {
        Map<String, Object> json = (Map<String, Object>) MiniJson.resource("/cultivars/carrot.json");
        ((Map<String, Object>) ((Map<String, Object>) json.get("requires")).get("qualitative")).put("color_r", "wild");
        return DefJson.cultivar(json, new ArrayList<>());
    }

    static TempoModel.Params params(CultivarDef carrot) {
        VillageTemplate village = VillageTemplate.fromLandrace(Carrot.SPECIES, Carrot.EASTERN, Map.of(Carrot.Y2, 0.30),
                Map.of());
        return new TempoModel.Params(Carrot.SPECIES, Carrot.EASTERN, carrot, village, Carrot.Y2, 24, 6, 3, 4, 30,
                Carrot.SPECIES.pollination().bees().orElseThrow().outcross(), 0.9, 0.15, 0.075, 5, 0.05, 0.25);
    }

    static String row(String name, TempoModel.Params p, int repeats, long seed) {
        TempoModel m = new TempoModel(p);
        double sum = 0;
        int fails = 0;
        int[] by = new int[GENS.length];
        for (int i = 0; i < repeats; i++) {
            int g = m.run(Hash64.of(seed, i)).cultivarGen();
            sum += g < 0 ? CENSOR : g;
            fails += g < 0 ? 1 : 0;
            for (int k = 0; k < GENS.length; k++) {
                by[k] += g >= 0 && g <= GENS[k] ? 1 : 0;
            }
        }
        double pf = (double) fails / repeats;
        double ci = 1.96 * Math.sqrt(pf * (1 - pf) / repeats);
        StringBuilder sb = new StringBuilder(String.format(Locale.ROOT, "%-34s n=%d mean %.2f failures %d (%.1f%% +-%.1f)",
                name, repeats, sum / repeats, fails, 100 * pf, 100 * ci));
        for (int k = 0; k < GENS.length; k++) {
            sb.append(String.format(Locale.ROOT, " P(<=%d) %.3f", GENS[k], (double) by[k] / repeats));
        }
        return sb.toString();
    }

    @Test
    @Tag("tempo-full")
    void carrotRequirementAblation() {
        int repeats = Integer.parseInt(System.getenv().getOrDefault("WILDROOTS_TEMPO_REPEATS", "4000"));
        assertTrue(repeats >= 1);
        long seed = 9001;
        List<String> rows = List.of(
                row("carrot requires color_r: wild", params(carrotWithR()), repeats, seed),
                row("no color_r requirement (shipped)", params(Carrot.CARROT), repeats, seed));
        rows.forEach(r -> System.out.println("[WR-705] " + r));
    }
}
