package io.github.slugblue.wildroots.data;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonParser;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import io.github.slugblue.wildroots.data.codec.PathCodecs;
import io.github.slugblue.wildroots.genetics.api.CultivarDef;
import io.github.slugblue.wildroots.genetics.api.SpeciesDef;
import io.github.slugblue.wildroots.genetics.testutil.DefJson;
import io.github.slugblue.wildroots.genetics.testutil.MiniJson;
import io.github.slugblue.wildroots.genetics.validation.DefinitionValidator;
import io.github.slugblue.wildroots.genetics.validation.Problem;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * WR-125 (review note of batch 1): the production Codec loader and the
 * engine's test mapping {@code DefJson} read the same fixtures of
 * {@code :genetics} ({@code species/*.json}, {@code cultivars/*.json},
 * {@code invalid/*.json}) and must agree: equal engine records, the same
 * WARN paths, and the same ERROR paths after validation.
 */
class SchemaParityTest {

    /** Production path for one species file: codec errors, defaults, then the engine validator. */
    record Loaded<D>(D def, List<Problem> problems, List<Problem> mapping) {
    }

    static Loaded<SpeciesDef> loadSpecies(String resource) {
        String text = MiniJson.resourceText(resource);
        DataResult<SpeciesSchema.Raw> r = SpeciesSchema.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(text));
        List<Problem> p = new ArrayList<>();
        if (r.error().isPresent()) {
            PathCodecs.errors(r.error().get().message()).forEach(e -> p.add(Problem.error(e.path(), e.message())));
            return new Loaded<>(null, p, List.of());
        }
        SpeciesSchema.Mapped m = SpeciesSchema.toDefinition(r.result().orElseThrow());
        p.addAll(m.problems());
        p.addAll(DefinitionValidator.validate(m.def()));
        return new Loaded<>(m.def(), p, m.problems());
    }

    static Loaded<CultivarDef> loadCultivar(String resource, SpeciesDef species) {
        String text = MiniJson.resourceText(resource);
        DataResult<CultivarSchema.Raw> r = CultivarSchema.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(text));
        List<Problem> p = new ArrayList<>();
        if (r.error().isPresent()) {
            PathCodecs.errors(r.error().get().message()).forEach(e -> p.add(Problem.error(e.path(), e.message())));
            return new Loaded<>(null, p, List.of());
        }
        CultivarSchema.Mapped m = CultivarSchema.toDefinition(r.result().orElseThrow());
        p.addAll(m.problems());
        p.addAll(DefinitionValidator.validate(m.def(), species));
        return new Loaded<>(m.def(), p, m.problems());
    }

    static List<String> fixtures(String folder) throws IOException, URISyntaxException {
        Path dir = Path.of(SchemaParityTest.class.getResource("/" + folder).toURI());
        try (Stream<Path> s = Files.list(dir)) {
            return s.map(p -> "/" + folder + "/" + p.getFileName()).filter(n -> n.endsWith(".json")).sorted().toList();
        }
    }

    static Set<String> paths(List<Problem> problems, Problem.Severity severity) {
        return problems.stream().filter(p -> p.severity() == severity).map(Problem::path)
                .collect(Collectors.toCollection(TreeSet::new));
    }

    @TestFactory
    Stream<DynamicTest> speciesFixturesGiveTheSameRecords() throws Exception {
        List<String> files = fixtures("species");
        assertTrue(files.size() >= 9, "fixtures of :genetics found: " + files);
        return files.stream().map(f -> DynamicTest.dynamicTest(f, () -> {
            List<Problem> expected = new ArrayList<>();
            SpeciesDef engine = DefJson.species(MiniJson.resource(f), expected);
            Loaded<SpeciesDef> prod = loadSpecies(f);
            assertEquals(engine, prod.def(), f);
            assertEquals(paths(expected, Problem.Severity.WARN), paths(prod.mapping(), Problem.Severity.WARN),
                    f + ": default warnings differ");
            assertEquals(paths(expected, Problem.Severity.ERROR), paths(prod.mapping(), Problem.Severity.ERROR), f);
        }));
    }

    @TestFactory
    Stream<DynamicTest> cultivarFixturesGiveTheSameRecords() throws Exception {
        SpeciesDef carrot = DefJson.species("/species/wild_carrot.json");
        return fixtures("cultivars").stream().map(f -> DynamicTest.dynamicTest(f, () -> {
            List<Problem> expected = new ArrayList<>();
            CultivarDef engine = DefJson.cultivar(MiniJson.resource(f), expected);
            Loaded<CultivarDef> prod = loadCultivar(f, carrot);
            assertEquals(engine, prod.def(), f);
            assertFalse(DefinitionValidator.hasErrors(prod.problems()), () -> f + ": " + prod.problems());
            assertEquals(paths(expected, Problem.Severity.WARN), paths(prod.mapping(), Problem.Severity.WARN), f);
        }));
    }

    /** Same table as the engine's DefinitionValidatorTest#fixtureGivesExpectedProblem. */
    @ParameterizedTest(name = "{0}")
    @CsvSource(delimiter = '|', value = {
        "loci_above_16           | quantitative.yield.loci                      | ERROR",
        "loci_below_1            | quantitative.flavor.loci                     | ERROR",
        "freq_sum_not_one        | wild_allele_freq.background                  | ERROR",
        "undeclared_trait        | tradeoffs[0].against                         | ERROR",
        "negative_c              | tradeoffs[1].c                               | ERROR",
        "theta_outside_0_1       | tradeoffs[2].threshold                       | ERROR",
        "elite_loci_exceed_loci  | wild_allele_freq.elite_loci.cold_tolerance   | ERROR",
        "s_locus_17_alleles      | qualitative.self_incompat.alleles            | ERROR",
        "s_locus_1_allele        | qualitative.self_incompat.alleles            | ERROR",
        "quality_axes_sum        | quality_axes                                 | WARN",
        "layout_above_512_bits   | layout                                       | ERROR"})
    void invalidFixturesGiveTheSameProblem(String file, String path, Problem.Severity severity) {
        String f = "/invalid/" + file + ".json";
        Loaded<SpeciesDef> prod = loadSpecies(f);
        assertTrue(prod.problems().stream().anyMatch(p -> p.severity() == severity && p.path().equals(path)),
                () -> "expected " + severity + " at " + path + ", got " + prod.problems());
        List<Problem> engine = new ArrayList<>();
        SpeciesDef def = DefJson.species(MiniJson.resource(f), engine);
        engine.addAll(DefinitionValidator.validate(def));
        assertEquals(paths(engine, Problem.Severity.ERROR), paths(prod.problems(), Problem.Severity.ERROR), f);
    }

    @Test
    void cultivarOnUndeclaredTraitIsTheSameError() {
        SpeciesDef carrot = DefJson.species("/species/wild_carrot.json");
        Loaded<CultivarDef> prod = loadCultivar("/invalid/cultivar_undeclared_trait.json", carrot);
        assertTrue(paths(prod.problems(), Problem.Severity.ERROR).contains("requires.quantitative.sugar"),
                prod.problems()::toString);
    }

    @Test
    void wrongTypeIsAnErrorWithItsPath() {
        String json = MiniJson.resourceText("/species/wild_carrot.json")
                .replace("\"yield\":             { \"loci\": 6", "\"yield\":             { \"loci\": \"six\"");
        assertTrue(json.contains("\"six\""));
        DataResult<SpeciesSchema.Raw> r = SpeciesSchema.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(json));
        List<PathCodecs.PathError> errors = PathCodecs.errors(r.error().orElseThrow().message());
        assertEquals(1, errors.size(), errors::toString);
        assertEquals("$.quantitative.yield.loci", errors.get(0).jsonPath());
        assertTrue(errors.get(0).message().contains("six"), errors.get(0).message());
    }
}
