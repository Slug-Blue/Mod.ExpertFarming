package io.github.slugblue.wildroots.genetics.population;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import io.github.slugblue.wildroots.genetics.api.CultivarDef;
import io.github.slugblue.wildroots.genetics.api.SpeciesDef;
import io.github.slugblue.wildroots.genetics.classify.CultivarClassifier;
import io.github.slugblue.wildroots.genetics.model.Genome;
import io.github.slugblue.wildroots.genetics.phenotype.CompiledSpecies;
import io.github.slugblue.wildroots.genetics.random.Hash64;
import io.github.slugblue.wildroots.genetics.testutil.DefJson;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

/**
 * GDD §9.5: a village plant is its landrace — for every MVP line present in
 * the data ({@code src/test/resources/cultivars}, tier-1 profiles; wave 1 has
 * the carrot only). The landrace profile is the only one offered, so the
 * check is "the plant passes the landrace profile by G_eff and genotype".
 */
class VillageLandraceAllLinesTest {

    @TestFactory
    Stream<DynamicTest> everyVillagePlantIsTheLandrace() throws IOException {
        List<CultivarDef> landraces = new ArrayList<>();
        try (Stream<Path> files = Files.list(Paths.get("src", "test", "resources", "cultivars"))) {
            for (Path f : (Iterable<Path>) files.sorted()::iterator) {
                CultivarDef c = DefJson.cultivar("/cultivars/" + f.getFileName());
                if (c.tier() == 1) {
                    landraces.add(c);
                }
            }
        }
        assertFalse(landraces.isEmpty());
        return landraces.stream().map(c -> DynamicTest.dynamicTest(c.id().value(), () -> {
            SpeciesDef s = DefJson.species("/species/" + c.species().substring(c.species().indexOf(':') + 1) + ".json");
            CompiledSpecies cs = new CompiledSpecies(s);
            VillageTemplate t = VillageTemplate.fromLandrace(s, c, Map.of(), Map.of());
            for (int i = 0; i < 2000; i++) {
                Genome g = t.plant(cs, Hash64.of(9, i));
                assertEquals(Optional.of(c.id()), CultivarClassifier.classify(cs, g, List.of(c), Optional.empty())
                        .cultivar(), "plant " + i);
            }
        }));
    }
}
