package io.github.slugblue.wildroots.lens;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * WR-704 tripwire. The journal article {@code guide.colors} (lang keys {@code wildroots.journal.guide.colors.4},
 * the hints {@code purple_root}, {@code red_root}, {@code white_cold}) says which colour stands heat, cold or
 * dryness in words, never by number. The numbers of {@code colours.properties} are placeholders of the balance
 * pass (WR-715), and the words are only as true as their <b>signs</b>. If this test fails, a sign in
 * {@code wild_carrot.json} changed: rewrite those texts (en_us and ru_ru) and then this table, not the other way
 * round.
 */
class ColourTextSignsTest {

    /** colour → signs of heat, cold, drought and saturation-1 (−1, 0, +1) the texts were written against. */
    private static final Map<String, List<Integer>> SIGNS = Map.of(
            "purple", List.of(1, -1, 1, 1),
            "yellow", List.of(1, -1, 1, 0),
            "red", List.of(1, -1, 1, 1),
            "orange", List.of(-1, 1, 0, 0),
            "white", List.of(0, 1, 0, 0));

    private static JsonObject colours() throws IOException {
        String path = "/resourcepacks/lines/carrot/data/wildroots/crop_species/wild_carrot.json";
        try (InputStream in = ColourTextSignsTest.class.getResourceAsStream(path)) {
            assertNotNull(in, path);
            return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject()
                    .getAsJsonObject("colours");
        }
    }

    private static int sign(double v) {
        return Double.compare(v, 0) == 0 ? 0 : v > 0 ? 1 : -1;
    }

    private static double num(JsonObject o, String key, double def) {
        return o.has(key) ? o.get(key).getAsDouble() : def;
    }

    @Test
    void theColoursAreExactlyTheFiveTheTextsKnow() throws IOException {
        JsonObject c = colours();
        java.util.Set<String> ids = new java.util.TreeSet<>();
        c.getAsJsonArray("ids").forEach(e -> ids.add(e.getAsString()));
        assertEquals(new java.util.TreeSet<>(SIGNS.keySet()), ids, "a colour was added or renamed: write its texts");
        assertEquals(java.util.Set.of(), c.getAsJsonObject("properties").keySet().stream()
                .filter(k -> !SIGNS.containsKey(k)).collect(java.util.stream.Collectors.toSet()),
                "properties of a colour the texts do not know");
    }

    @Test
    void theSignsBehindTheColourTextsHold() throws IOException {
        JsonObject props = colours().getAsJsonObject("properties");
        for (Map.Entry<String, List<Integer>> e : SIGNS.entrySet()) {
            JsonObject p = props.has(e.getKey()) ? props.getAsJsonObject(e.getKey()) : new JsonObject();
            List<Integer> actual = List.of(sign(num(p, "heat", 0)), sign(num(p, "cold", 0)),
                    sign(num(p, "drought", 0)), sign(num(p, "saturation_mult", 1) - 1));
            assertEquals(e.getValue(), actual, e.getKey() + ": signs of heat, cold, water, saturation changed;"
                    + " rewrite guide.colors.4 and the colour hints in en_us.json and ru_ru.json");
        }
    }
}
