package io.github.slugblue.wildroots.genetics.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.slugblue.wildroots.genetics.codec.DecodeResult;
import io.github.slugblue.wildroots.genetics.codec.GenomeCodec;
import io.github.slugblue.wildroots.genetics.codec.GenomeRecord;
import io.github.slugblue.wildroots.genetics.codec.LayoutDictionary;
import io.github.slugblue.wildroots.genetics.model.Genome;
import io.github.slugblue.wildroots.genetics.model.GenomeLayout;
import io.github.slugblue.wildroots.genetics.phenotype.NoiseVector;
import io.github.slugblue.wildroots.genetics.pollination.Brush;
import io.github.slugblue.wildroots.genetics.pollination.Candidate;
import io.github.slugblue.wildroots.genetics.pollination.FatherSelector;
import io.github.slugblue.wildroots.genetics.population.VillageTemplate;
import io.github.slugblue.wildroots.genetics.quality.GradedInput;
import io.github.slugblue.wildroots.genetics.random.Hash64;
import io.github.slugblue.wildroots.genetics.random.SplitMix64;
import io.github.slugblue.wildroots.genetics.testutil.Carrot;
import io.github.slugblue.wildroots.genetics.testutil.DefJson;
import io.github.slugblue.wildroots.genetics.testutil.MiniJson;
import io.github.slugblue.wildroots.genetics.testutil.RandomGenomes;
import io.github.slugblue.wildroots.genetics.validation.Problem;
import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/** WR-113: the GeneticsService facade and Defaults (architecture §4.1–4.2, GDD §14). */
class GeneticsServiceTest {

    static final SpeciesDef S = Carrot.SPECIES;
    static final GeneticsService G = Genetics.of(List.of(S));

    @Test
    void facadeGivesTheSameResultsAsTheEngine() {
        long seed = 99;
        PatchPassport patch = new PatchPassport(S.id(), "meadow", false, 1L, true, seed);
        GenomeLayout l = G.layout(S).orElseThrow();
        assertEquals(142, l.totalBits());
        Genome mother = G.wildPlant(S, patch, Hash64.of(seed, 1L, 2L)).orElseThrow();
        assertEquals(io.github.slugblue.wildroots.genetics.population.WildPopulation.wildPlant(S, l, patch,
                Hash64.of(seed, 1L, 2L)), mother);
        List<Candidate> near = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            near.add(new Candidate(G.wildPlant(S, patch, Hash64.of(seed, 1L, 10L + i)).orElseThrow(), i % 3 - 1, 0, 1));
        }
        FatherSelector.Selection sel = G.chooseFathers(S, mother, near, List.of(), 7).orElseThrow();
        assertEquals(FatherSelector.choose(S, mother, near, List.of(), 7), sel);
        assertEquals(6, sel.compatibleNeighbours());
        double pOut = G.pOut(S, sel, true, Brush.NONE);
        assertEquals(0.60, pOut, 1e-12, "a bee visit without a donor does not raise p_out");
        LotRecord lot = new LotRecord(mother, sel.fathers(), pOut);
        Genome child = G.offspring(lot, 11).orElseThrow();
        assertEquals(io.github.slugblue.wildroots.genetics.breeding.Offspring.of(lot, S.mutation(), 11), child);
        NoiseVector eps = G.rollNoise(S, 12).orElseThrow();
        Phenotype p = G.phenotype(child, S, Environment.neutral(0.6), eps).orElseThrow();
        assertEquals(io.github.slugblue.wildroots.genetics.phenotype.PhenotypeCalculator.compute(
                new io.github.slugblue.wildroots.genetics.phenotype.CompiledSpecies(S), child,
                Environment.neutral(0.6), eps), p);
        double seeds = G.expectedSeeds(S, p, sel, true, Brush.NONE);
        assertEquals(3 + 9 * p.p(Carrot.YIELD), seeds, 1e-12);
        assertEquals((3 + 9 * p.p(Carrot.YIELD)) * 0.5, G.expectedSeeds(S, p, sel, false, Brush.NONE), 1e-12);
        int grade = G.grade(p, Carrot.CARROT);
        assertEquals(io.github.slugblue.wildroots.genetics.quality.Grade.of(
                0.5 * p.p(Carrot.FRUIT) + 0.5 * p.p(Carrot.FLAVOR), p.bitter()), grade);
        assertEquals(grade, G.grade(p, S), "wild harvest uses the line's axes, here the same as the cultivar's");
        Classification c = G.classify(child, S, Carrot.PROFILES, Optional.empty()).orElseThrow();
        assertEquals(c.cultivar(), G.classifyClone(c).cultivar());
        assertEquals(child, G.clone(child));
        assertEquals(child, G.decode(G.encode(child), S).genome());
        assertEquals(Optional.of(child.layout()), ((DecodeResult.Valid) G.decode(G.encode(child), S)).storedLayout());
        assertEquals(3, G.process(List.of(new GradedInput(3, 2, 1, false)), List.of()).grade());
        VillageTemplate t = VillageTemplate.fromLandrace(S, Carrot.EASTERN, Map.of(Carrot.Y2, 0.3), Map.of());
        Genome village = G.villagePlant(S, t, 5).orElseThrow();
        assertEquals(Optional.of(Carrot.EASTERN.id()),
                G.classify(village, S, Carrot.PROFILES, Optional.empty()).orElseThrow().cultivar());
        assertEquals(List.of(), G.validate(S));
        assertEquals(List.of(), G.validate(Carrot.CARROT, S));
    }

    @Test
    void invalidOrUnknownSpeciesAndForeignGenomesGiveEmptyResultsNotExceptions() {
        SpeciesDef broken = DefJson.species(MiniJson.resource("/invalid/loci_above_16.json"), new ArrayList<>());
        Genetics g = Genetics.of(List.of(S, broken));
        PatchPassport patch = new PatchPassport(broken.id(), "meadow", false, 1L, false, 1);
        assertTrue(g.layout(broken).isEmpty());
        assertTrue(g.wildPlant(broken, patch, 1).isEmpty());
        assertTrue(g.rollNoise(broken, 1).isEmpty());
        Genome carrot = RandomGenomes.carrot(new SplitMix64(1));
        assertTrue(g.phenotype(carrot, broken, Environment.neutral(0.5), NoiseVector.zero(7)).isEmpty());
        assertTrue(g.classify(carrot, broken, Carrot.PROFILES, Optional.empty()).isEmpty());
        assertTrue(g.chooseFathers(broken, carrot, List.of(), List.of(), 1).isEmpty());
        assertInstanceOf(DecodeResult.Inert.class, g.decode(g.encode(carrot), broken));
        assertInstanceOf(DecodeResult.Inert.class, g.decode(g.encode(carrot), null));
        assertEquals(0.0, g.pOut(broken, new FatherSelector.Selection(List.of(), 0, 1, 0), false, Brush.NONE));
        // unknown species of a lot
        Genetics none = new Genetics(id -> null, new LayoutDictionary());
        assertTrue(none.offspring(new LotRecord(carrot, List.of(), 0), 1).isEmpty());
        // a genome in another layout of the right species: empty instead of AIOOBE
        SpeciesDef y8 = DefJson.species("/species/wild_carrot_yield8.json");
        assertTrue(g.phenotype(carrot, y8, Environment.neutral(0.5), NoiseVector.zero(7)).isEmpty());
        assertTrue(g.offspring(y8, new LotRecord(carrot, List.of(), 0), 1).isEmpty());
    }

    @Test
    void cacheRevalidatesANewDefinitionObject() {
        Map<String, SpeciesDef> snapshot = new java.util.HashMap<>(Map.of(S.id(), S));
        Genetics g = new Genetics(snapshot::get, new LayoutDictionary());
        assertTrue(g.layout(S).isPresent());
        SpeciesDef broken = DefJson.species(MiniJson.resource("/invalid/loci_above_16.json"), new ArrayList<>());
        assertEquals(S.id(), broken.id());
        assertTrue(g.layout(broken).isEmpty(), "same id, new object after /reload: validated again");
        assertTrue(g.layout(S).isPresent());
    }

    @Test
    void decodeMigratesThroughTheDictionaryAndIsInertOnUnknownSpecies() {
        SpeciesDef y8 = DefJson.species("/species/wild_carrot_yield8.json");
        LayoutDictionary d = new LayoutDictionary();
        d.register(S.id(), GenomeLayout.of(S));
        d.register(y8.id(), GenomeLayout.of(y8));
        Genetics after = new Genetics(id -> y8, d);
        GenomeRecord old = GenomeCodec.encode(RandomGenomes.carrot(new SplitMix64(1)));
        DecodeResult r = after.decode(old, y8);
        assertInstanceOf(DecodeResult.Valid.class, r);
        assertTrue(((DecodeResult.Valid) r).migrated());
        assertEquals(GenomeLayout.of(y8), r.genome().layout());
        assertInstanceOf(DecodeResult.Inert.class, after.decode(old, null));
    }

    // ---------------------------------------------------------------- Defaults

    @Test
    void defaultSubstitutionReturnsWarnProblem() {
        Defaults.Resolved<Double> r = Defaults.or(null, Defaults.BUDGET_LIMIT, "budget.limit");
        assertEquals(2.6, r.value());
        assertTrue(r.defaulted());
        assertEquals(Problem.Severity.WARN, r.warning().severity());
        assertEquals("budget.limit", r.warning().path());
        Defaults.Resolved<Double> given = Defaults.or(3.0, Defaults.BUDGET_LIMIT, "budget.limit");
        assertEquals(3.0, given.value());
        assertNull(given.warning());
    }

    @Test
    void loaderGetsWarnWhenLimitIsMissing() {
        @SuppressWarnings("unchecked")
        Map<String, Object> json = (Map<String, Object>) MiniJson.parse(MiniJson.resourceText("/species/wild_carrot.json"));
        @SuppressWarnings("unchecked")
        Map<String, Object> budget = (Map<String, Object>) json.get("budget");
        budget.remove("limit");
        List<Problem> problems = new ArrayList<>();
        SpeciesDef s = DefJson.species(json, problems);
        assertEquals(2.6, s.budget().limit());
        assertTrue(problems.stream().anyMatch(p -> p.severity() == Problem.Severity.WARN
                && p.path().equals("budget.limit")), problems::toString);
        assertTrue(problems.stream().noneMatch(Problem::isError));
    }

    /**
     * Default values live only in {@code Defaults}: their literals do not
     * appear in other production classes ({@code DataLimits} holds limits,
     * not defaults, and is exempt).
     */
    @Test
    void defaultLiteralsOnlyInDefaults() throws IOException {
        Pattern literal = Pattern.compile("(?<![\\w.])(2\\.6|0\\.004|0\\.001|0\\.35|0\\.55|0\\.75|0\\.60?|0\\.30?"
                + "|0\\.40?|0\\.45|0\\.15|0\\.25|0\\.90?|0\\.07|0\\.05)(?![\\w.])");
        List<String> hits = new ArrayList<>();
        try (Stream<Path> files = Files.walk(Paths.get("src", "main", "java"))) {
            for (Path f : (Iterable<Path>) files.filter(x -> x.toString().endsWith(".java"))::iterator) {
                String name = f.getFileName().toString();
                if (name.equals("Defaults.java") || name.equals("DataLimits.java")) {
                    continue;
                }
                List<String> lines = Files.readAllLines(f, StandardCharsets.UTF_8);
                for (int i = 0; i < lines.size(); i++) {
                    String code = lines.get(i).replaceAll("//.*$", "");
                    if (code.trim().startsWith("*") || code.trim().startsWith("/*")) {
                        continue;
                    }
                    Matcher m = literal.matcher(code);
                    if (m.find()) {
                        hits.add(f.getFileName() + ":" + (i + 1) + ": " + lines.get(i).trim());
                    }
                }
            }
        }
        assertTrue(hits.isEmpty(), "default values outside Defaults:\n" + String.join("\n", hits));
    }
}
