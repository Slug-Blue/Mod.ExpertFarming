package io.github.slugblue.wildroots.data;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.slugblue.wildroots.core.Ids;
import io.github.slugblue.wildroots.genetics.api.CultivarDef;
import io.github.slugblue.wildroots.genetics.api.QualReq;
import io.github.slugblue.wildroots.genetics.api.Range;
import io.github.slugblue.wildroots.genetics.api.SpeciesDef;
import io.github.slugblue.wildroots.genetics.codec.LayoutDescriptor;
import io.github.slugblue.wildroots.genetics.model.GenomeLayout;
import io.github.slugblue.wildroots.genetics.model.TraitId;
import io.github.slugblue.wildroots.genetics.testutil.DefJson;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.IoSupplier;
import org.junit.jupiter.api.Test;

/**
 * WR-129: the shipped carrot line data (line pack {@code lines/carrot} and
 * the layout history in the core data) load without problems and match GDD
 * §3.9, §4.2, §7.3 and economy §2.2.
 */
class CarrotLineDataTest {

    static final String PACK = "/resourcepacks/lines/carrot/data/wildroots/";

    static IoSupplier<InputStream> resource(String path) {
        return () -> CarrotLineDataTest.class.getResourceAsStream(path);
    }

    static DefinitionLoader.LoadResult load() {
        Map<ResourceLocation, IoSupplier<InputStream>> species = Map.of(
                Ids.mod("crop_species/wild_carrot.json"), resource(PACK + "crop_species/wild_carrot.json"));
        Map<ResourceLocation, IoSupplier<InputStream>> cultivars = Map.of(
                Ids.mod("cultivars/carrot.json"), resource(PACK + "cultivars/carrot.json"),
                Ids.mod("cultivars/eastern_carrot.json"), resource(PACK + "cultivars/eastern_carrot.json"));
        Map<ResourceLocation, IoSupplier<InputStream>> layouts = Map.of(
                Ids.mod("genome_layouts/wild_carrot.json"), resource("/data/wildroots/genome_layouts/wild_carrot.json"));
        return DefinitionLoader.assemble(DefinitionLoader.parseSpecies(species), DefinitionLoader.parseLayouts(layouts),
                DefinitionLoader.parseCultivars(cultivars));
    }

    @Test
    void loadsWithoutAnyProblem() {
        DefinitionLoader.LoadResult r = load();
        assertEquals(List.of(), r.lines());
        assertEquals("Wild Roots: species 1 (0 skipped), cultivars 2 (0 skipped)", r.summary());
    }

    /** The species is the GDD §3.9 JSON (the engine fixture), with rich_elite spelled out at its default. */
    @Test
    void speciesIsTheGddDefinition() {
        SpeciesDef shipped = load().snapshot().species("wildroots:wild_carrot").orElseThrow().def();
        assertEquals(DefJson.species("/species/wild_carrot.json"), shipped);
        // GDD §4.2 row wild_carrot
        assertEquals(2, shipped.pollination().radius());
        assertEquals(0.60, shipped.pollination().outcross());
        assertEquals(0.95, shipped.pollination().bees().orElseThrow().outcross());
        assertEquals(16, shipped.pollination().bees().orElseThrow().carryRange());
        assertEquals(0.5, shipped.pollination().seedSetNoPollinator());
        assertEquals(0.25, shipped.load().freq());
        assertEquals(0.1, shipped.wildF());
        assertEquals(0.2, shipped.tempMin());
        assertEquals(1.0, shipped.tempMax());
        assertEquals("field", load().snapshot().species("wildroots:wild_carrot").orElseThrow().info().archetype());
    }

    @Test
    void cultivarsHaveTheGddThresholdsAndEconomyFood() {
        DefinitionsSnapshot s = load().snapshot();
        CultivarDef eastern = s.cultivar("wildroots:eastern_carrot").orElseThrow().def();
        assertEquals(1, eastern.tier());
        assertEquals(Range.atLeast(0.40), eastern.quantitative().get(TraitId.of("fruit_size")));
        assertEquals(Range.atLeast(0.30), eastern.quantitative().get(TraitId.of("flavor")));
        assertEquals(2, eastern.anyOf().size());
        CultivarDef carrot = s.cultivar("wildroots:carrot").orElseThrow().def();
        assertEquals(2, carrot.tier());
        assertEquals(Range.atLeast(0.75), carrot.quantitative().get(TraitId.of("fruit_size")));
        assertEquals(Range.atLeast(0.60), carrot.quantitative().get(TraitId.of("flavor")));
        assertEquals(QualReq.WILD, carrot.qualitative().get(TraitId.of("color_p")));
        assertEquals(DefJson.cultivar("/cultivars/carrot.json"), carrot);
        for (String id : List.of("wildroots:carrot", "wildroots:eastern_carrot")) {
            CultivarInfo info = s.cultivar(id).orElseThrow().info();
            assertEquals(new CultivarInfo.FoodReference(3, 0.6), info.foodReference().orElseThrow(), id);
            assertEquals(1.0, info.foodMult(), id);
            assertEquals(Map.of(TraitId.of("fruit_size"), 0.5, TraitId.of("flavor"), 0.5),
                    s.cultivar(id).orElseThrow().def().qualityAxes(), id);
            assertEquals(id.replace("wildroots:", "wildroots.cultivar.") + ".note", info.helpKey());
        }
    }

    /** The layout history starts with the layout of the shipped species. */
    @Test
    void layoutHistoryHoldsTheCurrentLayout() {
        DefinitionsSnapshot s = load().snapshot();
        GenomeLayout current = GenomeLayout.of(s.species("wildroots:wild_carrot").orElseThrow().def());
        List<LayoutDescriptor> history = s.layouts().entries().stream()
                .filter(d -> d.species().equals("wildroots:wild_carrot")).toList();
        assertEquals(1, history.size());
        assertEquals(new LayoutDescriptor("wildroots:wild_carrot", current.hash(), current.canonical()), history.get(0));
        assertTrue(s.layouts().lookup("wildroots:wild_carrot", -3539622408563365149L).isPresent());
    }
}
