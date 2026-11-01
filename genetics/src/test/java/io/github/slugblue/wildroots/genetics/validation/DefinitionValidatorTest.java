package io.github.slugblue.wildroots.genetics.validation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.slugblue.wildroots.genetics.api.CultivarDef;
import io.github.slugblue.wildroots.genetics.api.SpeciesDef;
import io.github.slugblue.wildroots.genetics.testutil.Carrot;
import io.github.slugblue.wildroots.genetics.testutil.DefJson;
import io.github.slugblue.wildroots.genetics.testutil.MiniJson;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** WR-110: definition validation and DataLimits (architecture §4.4; SEC-28, SEC-29). */
class DefinitionValidatorTest {

    static List<Problem> speciesProblems(Object json) {
        List<Problem> p = new ArrayList<>();
        SpeciesDef s = DefJson.species(json, p);
        p.addAll(DefinitionValidator.validate(s));
        return p;
    }

    static boolean has(List<Problem> problems, Problem.Severity sev, String path) {
        return problems.stream().anyMatch(p -> p.severity() == sev && p.path().equals(path));
    }

    @Test
    void carrotIsValid() {
        assertEquals(List.of(), DefinitionValidator.validate(Carrot.SPECIES));
        assertEquals(List.of(), DefinitionValidator.validate(Carrot.CARROT, Carrot.SPECIES));
        assertEquals(List.of(), DefinitionValidator.validate(Carrot.EASTERN, Carrot.SPECIES));
    }

    /** One fixture file per error type of architecture §4.4. */
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
    void fixtureGivesExpectedProblem(String file, String path, Problem.Severity severity) {
        List<Problem> p = speciesProblems(MiniJson.resource("/invalid/" + file + ".json"));
        assertTrue(has(p, severity, path), () -> "expected " + severity + " at " + path + ", got " + p);
        if (severity == Problem.Severity.WARN) {
            assertFalse(DefinitionValidator.hasErrors(p), p::toString);
        }
    }

    @Test
    void cultivarRequirementOnUndeclaredTraitIsAnError() {
        List<Problem> p = new ArrayList<>();
        CultivarDef c = DefJson.cultivar(MiniJson.resource("/invalid/cultivar_undeclared_trait.json"), p);
        p.addAll(DefinitionValidator.validate(c, Carrot.SPECIES));
        assertTrue(has(p, Problem.Severity.ERROR, "requires.quantitative.sugar"), p::toString);
    }

    // ---------------------------------------------------------------- NaN / ±Infinity

    /** Every numeric leaf of the carrot JSON, replaced by NaN, +Infinity and −Infinity, is an ERROR at its path. */
    @TestFactory
    Stream<DynamicTest> nonFiniteNumberInAnyFieldIsAnError() {
        Object base = MiniJson.resource("/species/wild_carrot.json");
        List<String> leaves = new ArrayList<>();
        numericLeaves(base, "", leaves);
        assertTrue(leaves.size() > 50, "carrot has " + leaves.size() + " numeric fields");
        List<DynamicTest> tests = new ArrayList<>();
        for (String leaf : leaves) {
            for (double bad : new double[] {Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY}) {
                tests.add(DynamicTest.dynamicTest(leaf + " = " + bad, () -> {
                    Object json = MiniJson.parse(MiniJson.resourceText("/species/wild_carrot.json"));
                    set(json, leaf, bad);
                    List<Problem> p = speciesProblems(json);
                    assertTrue(has(p, Problem.Severity.ERROR, leaf), () -> leaf + ": " + p);
                }));
            }
        }
        return tests.stream();
    }

    @SuppressWarnings("unchecked")
    static void numericLeaves(Object node, String path, List<String> out) {
        if (node instanceof Map<?, ?> m) {
            for (Map.Entry<String, Object> e : ((Map<String, Object>) m).entrySet()) {
                numericLeaves(e.getValue(), path.isEmpty() ? e.getKey() : path + "." + e.getKey(), out);
            }
        } else if (node instanceof List<?> l) {
            for (int i = 0; i < l.size(); i++) {
                numericLeaves(l.get(i), path + "[" + i + "]", out);
            }
        } else if (node instanceof Double) {
            out.add(path);
        }
    }

    /** Sets a leaf addressed by {@code a.b[2].c}. */
    @SuppressWarnings("unchecked")
    static void set(Object root, String path, Object value) {
        List<Object> keys = new ArrayList<>();
        for (String part : path.split("\\.")) {
            int br = part.indexOf('[');
            if (br < 0) {
                keys.add(part);
            } else {
                keys.add(part.substring(0, br));
                for (String idx : part.substring(br + 1, part.length() - 1).split("\\]\\[")) {
                    keys.add(Integer.parseInt(idx));
                }
            }
        }
        Object node = root;
        for (int i = 0; i < keys.size() - 1; i++) {
            Object k = keys.get(i);
            node = k instanceof Integer n ? ((List<Object>) node).get(n) : ((Map<String, Object>) node).get(k);
        }
        Object last = keys.get(keys.size() - 1);
        if (last instanceof Integer n) {
            ((List<Object>) node).set(n, value);
        } else {
            ((Map<String, Object>) node).put((String) last, value);
        }
    }

    // ---------------------------------------------------------------- DataLimits rows

    /** SEC-28 rows: the bound is accepted, one step beyond is an ERROR at the same path. */
    @ParameterizedTest(name = "{0}: {1} ok, {2} error")
    @CsvSource(delimiter = '|', value = {
        "quantitative.yield.loci            | 16    | 17",
        "quantitative.yield.loci            | 1     | 0",
        "load.loci                          | 16    | 17",
        "load.loci                          | 0     | -1",
        "tradeoffs[0].c                     | 10    | 10.01",
        "tradeoffs[0].c                     | 0     | -0.01",
        "tradeoffs[0].threshold             | 1     | 1.01",
        "tradeoffs[0].threshold             | 0     | -0.01",
        "budget.weights.yield               | 10    | 10.5",
        "budget.limit                       | 32    | 32.5",
        "budget.limit                       | 0.01  | 0",
        "mutation.quantitative              | 0.05  | 0.051",
        "mutation.qualitative               | 0     | -0.001",
        "pollination.radius                 | 16    | 17",
        "pollination.radius                 | 0     | -1",
        "timing.to_harvest_min              | 1440  | 1441",
        "timing.to_harvest_min              | 0.1   | 0.09",
        "qualitative.dormancy.wild_freq     | 1     | 1.01",
        "wild_F                             | 1     | 1.5",
        "pollination.outcross               | 1     | 1.2",
        "quality_axes.flavor                | 0     | -0.5",
        "wild_allele_freq.elite_loci.yield  | 6     | 7"})
    void dataLimitsBoundaryAndBeyond(String path, double ok, double bad) {
        Object good = MiniJson.parse(MiniJson.resourceText("/species/wild_carrot.json"));
        set(good, path, ok);
        List<Problem> pg = speciesProblems(good);
        assertFalse(pg.stream().anyMatch(p -> p.isError() && p.path().equals(path)), () -> "bound rejected: " + pg);
        Object beyond = MiniJson.parse(MiniJson.resourceText("/species/wild_carrot.json"));
        set(beyond, path, bad);
        List<Problem> pb = speciesProblems(beyond);
        assertTrue(has(pb, Problem.Severity.ERROR, path), () -> path + "=" + bad + ": " + pb);
    }

    @Test
    void traitCountLimits() {
        Object json = MiniJson.parse(MiniJson.resourceText("/species/wild_carrot.json"));
        @SuppressWarnings("unchecked")
        Map<String, Object> qual = (Map<String, Object>) ((Map<String, Object>) json).get("qualitative");
        for (int i = qual.size(); i < 17; i++) {
            Map<String, Object> t = new LinkedHashMap<>();
            t.put("dominant", "wild");
            t.put("wild_freq", 0.1);
            qual.put("extra_" + i, t);
        }
        assertTrue(has(speciesProblems(json), Problem.Severity.ERROR, "qualitative"));
    }

    @Test
    void cultivarLimits() {
        CultivarDef c = Carrot.CARROT;
        CultivarDef bad = new CultivarDef(c.id(), c.species(), 3, 2000, c.quantitative(), c.qualitative(), c.anyOf(),
                0.6, c.qualityAxes(), c.parent());
        List<Problem> p = DefinitionValidator.validate(bad, Carrot.SPECIES);
        assertTrue(has(p, Problem.Severity.ERROR, "tier"));
        assertTrue(has(p, Problem.Severity.ERROR, "priority"));
        assertTrue(has(p, Problem.Severity.ERROR, "hysteresis"));
    }

    // ---------------------------------------------------------------- reference graph (SEC-29)

    @Test
    void cycleOfTwoRejectsBoth() {
        List<Problem> p = ReferenceGraph.check(Map.of("A", List.of("B"), "B", List.of("A"), "C", List.of()));
        assertEquals(java.util.Set.of("A", "B"), ReferenceGraph.rejected(p));
    }

    @Test
    void selfReferenceIsACycle() {
        assertEquals(java.util.Set.of("A"), ReferenceGraph.rejected(ReferenceGraph.check(Map.of("A", List.of("A")))));
    }

    @Test
    void chainOf17IsTooDeepWithoutStackOverflow() {
        Map<String, List<String>> g = new LinkedHashMap<>();
        for (int i = 0; i < 17; i++) {
            g.put("n" + i, i == 16 ? List.of() : List.of("n" + (i + 1)));
        }
        List<Problem> p = ReferenceGraph.check(g);
        assertTrue(ReferenceGraph.rejected(p).contains("n0"), p::toString);
        Map<String, List<String>> ok = new LinkedHashMap<>();
        for (int i = 0; i < 16; i++) {
            ok.put("n" + i, i == 15 ? List.of() : List.of("n" + (i + 1)));
        }
        assertEquals(List.of(), ReferenceGraph.check(ok));
        Map<String, List<String>> huge = new LinkedHashMap<>();
        for (int i = 0; i < 200_000; i++) {
            huge.put("n" + i, i == 199_999 ? List.of("n0") : List.of("n" + (i + 1)));
        }
        assertEquals(200_000, ReferenceGraph.rejected(ReferenceGraph.check(huge)).size());
    }

    @Test
    void danglingReferenceRejectsRecordAndDependents() {
        List<Problem> p = ReferenceGraph.check(Map.of("cultivar:a", List.of("species:missing"),
                "cultivar:b", List.of("cultivar:a"), "cultivar:c", List.of()));
        assertEquals(java.util.Set.of("cultivar:a", "cultivar:b"), ReferenceGraph.rejected(p));
    }

    @Test
    void tradeoffCyclesBetweenTraitsAreAllowed() {
        Object json = MiniJson.parse(MiniJson.resourceText("/species/wild_carrot.json"));
        @SuppressWarnings("unchecked")
        List<Object> t = (List<Object>) ((Map<String, Object>) json).get("tradeoffs");
        Map<String, Object> back = new LinkedHashMap<>();
        back.put("trait", "fruit_size");
        back.put("against", "flavor");
        back.put("c", 0.2);
        back.put("threshold", 0.5);
        t.add(back);
        assertFalse(DefinitionValidator.hasErrors(speciesProblems(json)));
    }
}
