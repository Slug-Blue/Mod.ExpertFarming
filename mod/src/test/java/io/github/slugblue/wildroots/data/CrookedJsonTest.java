package io.github.slugblue.wildroots.data;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * WR-125 / WR-126, SEC-27 (TAE): "crooked" JSON that Gson's lenient parser
 * (used by {@code JsonParser.parseString}) accepts: non-finite numbers,
 * trailing garbage, comments and single-quoted strings around the iterative
 * depth check. Every case is a {@code Problem} for one file, never an
 * exception, and never a definition with a non-finite value.
 */
class CrookedJsonTest {

    static DefinitionLoader.LoadResult loadSpecies(String json) {
        return DefinitionLoader.load(Map.of(DefinitionLoaderTest.species("wild_carrot"), DefinitionLoaderTest.text(json)),
                Map.of());
    }

    static List<String> lines(DefinitionLoader.LoadResult r) {
        return r.lines().stream().map(DefinitionLoader.LogLine::text).toList();
    }

    static String carrotWith(String from, String to) {
        assertTrue(DefinitionLoaderTest.CARROT.contains(from), from);
        return DefinitionLoaderTest.CARROT.replace(from, to);
    }

    @ParameterizedTest
    @ValueSource(strings = {"NaN", "Infinity", "-Infinity", "1e400", "-1e400"})
    void nonFiniteNumbersAreRejected(String value) {
        for (String field : List.of("\"outcross\": 0.60", "\"wild_F\": 0.1", "\"radius\": 2")) {
            String key = field.substring(0, field.indexOf(':') + 1);
            DefinitionLoader.LoadResult r = loadSpecies(carrotWith(field, key + " " + value));
            assertTrue(r.snapshot().species("wildroots:wild_carrot").isEmpty(),
                    () -> field + " = " + value + " was loaded; lines " + lines(r));
            assertEquals(1, r.snapshot().stats().speciesSkipped());
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {" }", " {}", " garbage", ", 1", " ]"})
    void trailingGarbageIsAProblem(String tail) {
        DefinitionLoader.LoadResult r = loadSpecies(DefinitionLoaderTest.CARROT + tail);
        assertTrue(r.snapshot().species("wildroots:wild_carrot").isEmpty(), () -> "loaded with tail " + tail);
        assertFalse(lines(r).isEmpty());
    }

    static String deep(int n) {
        return "[".repeat(n) + "]".repeat(n);
    }

    @Test
    void plainDeepFieldIsRejectedByTheDepthCheck() {
        DefinitionLoader.LoadResult r = loadSpecies(carrotWith("\"wild_F\": 0.1,", "\"wild_F\": 0.1, \"pad\": " + deep(40) + ","));
        assertTrue(lines(r).stream().anyMatch(l -> l.contains("deeper than 32")), lines(r)::toString);
    }

    /**
     * Was a BUG on {@code feat/wr-129} (found by TAE, fixed in
     * {@code fix/wr-12x-core-review-1}): the character-level depth counter
     * knew only double-quoted strings, while Gson parsed leniently, so a
     * {@code "} inside {@code '…'} or a comment hid a 40-deep nesting and the
     * file loaded with no Problem. Now the strict reader refuses the file.
     */
    @ParameterizedTest
    @ValueSource(strings = {"\"a\": '\"', \"pad\": DEEP, \"b\": '\"',", "/* \" */ \"pad\": DEEP, /* \" */",
            "\"pad\": DEEP, # \" comment\n"})
    void lenientSyntaxCannotHideNesting(String trick) {
        String json = carrotWith("\"wild_F\": 0.1,", "\"wild_F\": 0.1, " + trick.replace("DEEP", deep(40)));
        DefinitionLoader.LoadResult r = loadSpecies(json);
        assertTrue(r.snapshot().species("wildroots:wild_carrot").isEmpty(), () -> "loaded; lines " + lines(r));
        assertEquals(1, lines(r).stream().filter(l -> l.startsWith("wildroots:crop_species/wild_carrot.json: ")).count(),
                lines(r)::toString);
    }

    @Test
    void hiddenVeryDeepNestingIsContained() {
        String json = carrotWith("\"wild_F\": 0.1,", "\"wild_F\": 0.1, \"a\": '\"', \"pad\": " + deep(100_000)
                + ", \"b\": '\"',");
        assertTrue(json.length() < DefinitionLoader.MAX_FILE_BYTES);
        DefinitionLoader.LoadResult r = loadSpecies(json); // must not throw
        assertEquals(1, r.snapshot().stats().speciesSkipped());
    }

    @Test
    void depthCounterIsNotFooledBySingleQuotes() {
        assertTrue(depthOrError("{\"a\": " + deep(40) + "}") > 32);
        assertTrue(depthOrError("{\"a\": '\"', \"p\": " + deep(40) + ", \"b\": '\"'}") > 32);
        assertTrue(depthOrError("{\"a\": \"[[[[\\\"]]\", \"p\": " + deep(33) + "}") > 32);
        assertEquals(32, depthOrError(deep(32)));
    }

    /** {@code jsonDepth}, with a refusal of non-standard JSON counted as "too deep". */
    static int depthOrError(String text) {
        try {
            return DefinitionLoader.jsonDepth(text, 32);
        } catch (java.io.IOException e) {
            return Integer.MAX_VALUE;
        }
    }
}
