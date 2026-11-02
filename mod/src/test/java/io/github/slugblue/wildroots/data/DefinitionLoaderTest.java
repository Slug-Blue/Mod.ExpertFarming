package io.github.slugblue.wildroots.data;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.slugblue.wildroots.core.Ids;
import io.github.slugblue.wildroots.genetics.testutil.MiniJson;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.IoSupplier;
import org.junit.jupiter.api.Test;

/** WR-125: the loader skips broken records with path lines, takes dependents along, writes the summary. */
class DefinitionLoaderTest {

    /** The engine's carrot fixture with {@code rich_elite} spelled out, so it loads without a default warning. */
    static final String CARROT = MiniJson.resourceText("/species/wild_carrot.json").replace(
            "\"elite\": [0.30, 0.40, 0.30],", "\"elite\": [0.30, 0.40, 0.30], \"rich_elite\": [0.15, 0.40, 0.45],");
    static final String CARROT_CULTIVAR = MiniJson.resourceText("/cultivars/carrot.json");
    static final String EASTERN = MiniJson.resourceText("/cultivars/eastern_carrot.json");

    static IoSupplier<InputStream> text(String s) {
        return () -> new ByteArrayInputStream(s.getBytes(StandardCharsets.UTF_8));
    }

    static ResourceLocation species(String name) {
        return Ids.mod("crop_species/" + name + ".json");
    }

    static ResourceLocation cultivar(String name) {
        return Ids.mod("cultivars/" + name + ".json");
    }

    static String withId(String json, String from, String to) {
        return json.replace("\"" + from + "\"", "\"" + to + "\"");
    }

    @Test
    void carrotLoadsWithoutProblems() {
        assertTrue(CARROT.contains("rich_elite"));
        DefinitionLoader.LoadResult r = DefinitionLoader.load(Map.of(species("wild_carrot"), text(CARROT)),
                Map.of(cultivar("carrot"), text(CARROT_CULTIVAR), cultivar("eastern_carrot"), text(EASTERN)));
        assertEquals(List.of(), r.lines());
        assertEquals("Wild Roots: species 1 (0 skipped), cultivars 2 (0 skipped)", r.summary());
        DefinitionsSnapshot s = r.snapshot();
        assertTrue(s.species("wildroots:wild_carrot").isPresent());
        assertEquals(2, s.profiles("wildroots:wild_carrot").size());
        assertEquals("wildroots.species.wild_carrot", s.species("wildroots:wild_carrot").get().info().translationKey());
        assertTrue(s.genetics().layout(s.species("wildroots:wild_carrot").get().def()).isPresent());
        CultivarInfo carrot = s.cultivar("wildroots:carrot").get().info();
        assertEquals(new CultivarInfo.FoodReference(3, 0.6), carrot.foodReference().orElseThrow());
        assertEquals(Ids.mod("carrot"), carrot.produceItem().orElseThrow());
    }

    @Test
    void typeErrorSkipsTheSpeciesAndItsCultivars() {
        String bad = withId(CARROT, "wildroots:wild_carrot", "wildroots:bad_carrot")
                .replace("\"yield\":             { \"loci\": 6", "\"yield\":             { \"loci\": \"six\"");
        String dependent = withId(withId(EASTERN, "wildroots:eastern_carrot", "wildroots:bad_landrace"),
                "wildroots:wild_carrot", "wildroots:bad_carrot");
        Map<ResourceLocation, IoSupplier<InputStream>> sp = new LinkedHashMap<>();
        sp.put(species("wild_carrot"), text(CARROT));
        sp.put(species("bad_carrot"), text(bad));
        Map<ResourceLocation, IoSupplier<InputStream>> cv = new LinkedHashMap<>();
        cv.put(cultivar("carrot"), text(CARROT_CULTIVAR));
        cv.put(cultivar("bad_landrace"), text(dependent));
        DefinitionLoader.LoadResult r = DefinitionLoader.load(sp, cv);

        List<String> lines = r.lines().stream().map(DefinitionLoader.LogLine::text).toList();
        assertTrue(lines.stream().anyMatch(l -> l.startsWith(
                "wildroots:crop_species/bad_carrot.json: $.quantitative.yield.loci: ") && l.contains("six")), lines::toString);
        assertTrue(lines.contains("wildroots:cultivars/bad_landrace.json: skipped: depends on rejected species wildroots:bad_carrot"),
                lines::toString);
        assertEquals(2, lines.size(), lines::toString);
        assertEquals("Wild Roots: species 1 (1 skipped), cultivars 1 (1 skipped)", r.summary());
        assertFalse(r.snapshot().species("wildroots:bad_carrot").isPresent());
        assertTrue(r.snapshot().species("wildroots:wild_carrot").isPresent());
    }

    @Test
    void semanticErrorGivesAPathFromTheValidator() {
        String bad = withId(CARROT, "wildroots:wild_carrot", "wildroots:wide").replace("\"loci\": 6", "\"loci\": 17");
        DefinitionLoader.LoadResult r = DefinitionLoader.load(Map.of(species("wide"), text(bad)), Map.of());
        List<String> lines = r.lines().stream().map(DefinitionLoader.LogLine::text).toList();
        assertTrue(lines.stream().anyMatch(l -> l.startsWith("wildroots:crop_species/wide.json: $.quantitative.yield.loci: ")),
                lines::toString);
        assertEquals("Wild Roots: species 0 (1 skipped), cultivars 0 (0 skipped)", r.summary());
    }

    @Test
    void duplicateIdKeepsTheFirstFile() {
        DefinitionLoader.LoadResult r = DefinitionLoader.load(
                Map.of(species("a"), text(CARROT), species("b"), text(CARROT)), Map.of());
        assertEquals(1, r.snapshot().species().size());
        assertEquals(1, r.lines().size());
        assertTrue(r.lines().get(0).text().startsWith("wildroots:crop_species/b.json: $.id: duplicate species id"),
                r.lines()::toString);
    }

    @Test
    void cultivarOfAnUnknownSpeciesIsSkipped() {
        DefinitionLoader.LoadResult r = DefinitionLoader.load(Map.of(), Map.of(cultivar("carrot"), text(CARROT_CULTIVAR)));
        assertEquals(List.of("wildroots:cultivars/carrot.json: skipped: unknown species wildroots:wild_carrot"),
                r.lines().stream().map(DefinitionLoader.LogLine::text).toList());
    }

    @Test
    void missingDefaultsAreWarnings() {
        String noLoad = CARROT.replace("\"load\": { \"loci\": 8, \"freq\": 0.25, \"step\": 0.90 },", "");
        DefinitionLoader.LoadResult r = DefinitionLoader.load(Map.of(species("wild_carrot"), text(noLoad)), Map.of());
        assertTrue(r.snapshot().species("wildroots:wild_carrot").isPresent());
        assertEquals(List.of(new DefinitionLoader.LogLine(false,
                        "wildroots:crop_species/wild_carrot.json: $.load: missing, default LoadDef[loci=8, freq=0.25, step=0.9] applied")),
                r.lines());
    }
}
