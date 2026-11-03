package io.github.slugblue.wildroots.plant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * WR-130: the blockstate JSON of {@code wild_carrot} gives models by partial
 * variant keys; {@code village} and {@code active} never appear in a key, so
 * they do not multiply models (docs/05-architecture.md §7.2).
 */
class FieldPlantBlockstateJsonTest {

    static final Set<String> MODEL_PROPERTIES = Set.of("stage", "size", "variant", "ailing");

    static JsonObject read(String path) throws Exception {
        try (InputStream in = FieldPlantBlockstateJsonTest.class.getResourceAsStream(path)) {
            assertNotNull(in, path);
            return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
        }
    }

    @Test
    void partialKeysWithoutVillageAndActive() throws Exception {
        JsonObject variants = read("/assets/wildroots/blockstates/wild_carrot.json").getAsJsonObject("variants");
        Set<Integer> stages = new HashSet<>();
        Set<String> models = new HashSet<>();
        for (Map.Entry<String, com.google.gson.JsonElement> e : variants.entrySet()) {
            for (String kv : e.getKey().split(",")) {
                String[] p = kv.split("=");
                assertTrue(MODEL_PROPERTIES.contains(p[0]), () -> "property " + p[0] + " in key " + e.getKey());
                assertFalse(p[0].equals("village") || p[0].equals("active"));
                if (p[0].equals("stage")) {
                    stages.add(Integer.parseInt(p[1]));
                }
            }
            String model = e.getValue().getAsJsonObject().get("model").getAsString();
            models.add(model);
            String file = "/assets/wildroots/models/" + model.substring("wildroots:".length()) + ".json";
            assertNotNull(read(file), file);
        }
        assertEquals(Set.of(0, 1, 2, 3, 4, 5), stages, "every stage has a model");
        // 768 states, but village and active never select a model: at most 6 × 4 × 4 × 2 = 192 models
        assertTrue(models.size() <= 6 * 4 * 4 * 2, () -> models.size() + " models");
        assertEquals(6, models.size(), "placeholder models until WR-193: one per stage");
    }
}
