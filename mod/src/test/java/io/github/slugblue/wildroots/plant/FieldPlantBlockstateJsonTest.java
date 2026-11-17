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
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * WR-130, WR-193: the blockstate JSON of {@code wild_carrot} gives models by partial
 * variant keys; {@code village} and {@code active} never appear in a key, so
 * they do not multiply models (docs/05-architecture.md §7.2). Every combination
 * of the model properties is matched by exactly one key, so no state is left
 * without a model and none is ambiguous.
 */
class FieldPlantBlockstateJsonTest {

    static final Set<String> MODEL_PROPERTIES = Set.of("stage", "size", "variant", "ailing", "strain");

    static JsonObject read(String path) throws Exception {
        try (InputStream in = FieldPlantBlockstateJsonTest.class.getResourceAsStream(path)) {
            assertNotNull(in, path);
            return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
        }
    }

    static Map<String, String> parseKey(String key) {
        Map<String, String> m = new HashMap<>();
        for (String kv : key.split(",")) {
            String[] p = kv.split("=");
            m.put(p[0], p[1]);
        }
        return m;
    }

    @Test
    void partialKeysWithoutVillageAndActive() throws Exception {
        JsonObject variants = read("/assets/wildroots/blockstates/wild_carrot.json").getAsJsonObject("variants");
        Set<Integer> stages = new HashSet<>();
        Set<String> models = new HashSet<>();
        for (Map.Entry<String, com.google.gson.JsonElement> e : variants.entrySet()) {
            for (Map.Entry<String, String> p : parseKey(e.getKey()).entrySet()) {
                assertTrue(MODEL_PROPERTIES.contains(p.getKey()), () -> "property " + p.getKey() + " in key " + e.getKey());
                assertFalse(p.getKey().equals("village") || p.getKey().equals("active"));
                if (p.getKey().equals("stage")) {
                    stages.add(Integer.parseInt(p.getValue()));
                }
            }
            String model = e.getValue().getAsJsonObject().get("model").getAsString();
            models.add(model);
            String file = "/assets/wildroots/models/" + model.substring("wildroots:".length()) + ".json";
            assertNotNull(read(file), file);
        }
        assertEquals(Set.of(0, 1, 2, 3, 4, 5), stages, "every stage has a model");
        // 2880 states, but village and active never select a model: at most 6 × 4 × 5 × 3 × 2 = 720 models
        assertTrue(models.size() <= 6 * 4 * 5 * 3 * 2, () -> models.size() + " models");
    }

    @Test
    void everyModelStateMatchesExactlyOneKey() throws Exception {
        JsonObject variants = read("/assets/wildroots/blockstates/wild_carrot.json").getAsJsonObject("variants");
        for (int stage = 0; stage <= 5; stage++) {
            for (int size = 0; size <= 3; size++) {
                for (int variant = 0; variant <= FieldPlantBlock.MAX_VARIANT; variant++) {
                    for (boolean ailing : new boolean[] {false, true}) {
                        for (int strain = 0; strain <= FieldPlantBlock.MAX_STRAIN; strain++) {
                            Map<String, String> state = Map.of("stage", "" + stage, "size", "" + size,
                                    "variant", "" + variant, "ailing", "" + ailing, "strain", "" + strain);
                            int matches = 0;
                            for (String key : variants.keySet()) {
                                boolean all = true;
                                for (Map.Entry<String, String> p : parseKey(key).entrySet()) {
                                    all &= p.getValue().equals(state.get(p.getKey()));
                                }
                                matches += all ? 1 : 0;
                            }
                            int m = matches;
                            assertEquals(1, m, () -> state + " matches " + m + " keys");
                        }
                    }
                }
            }
        }
    }

    /** WR-702: the red root has its own model (red crown, four sizes, healthy and ailing) and every model file exists. */
    @Test
    void redRootsUseTheRedCrown() throws Exception {
        JsonObject variants = read("/assets/wildroots/blockstates/wild_carrot.json").getAsJsonObject("variants");
        int red = 0;
        for (Map.Entry<String, com.google.gson.JsonElement> e : variants.entrySet()) {
            Map<String, String> key = parseKey(e.getKey());
            if ("4".equals(key.get("variant"))) {
                red++;
                assertEquals("3", key.get("stage"), "only the root shoulder of stage 3 has a colour: " + e.getKey());
                String model = e.getValue().getAsJsonObject().get("model").getAsString();
                assertTrue(model.contains("_red"), model);
                String file = read("/assets/wildroots/models/" + model.substring("wildroots:".length()) + ".json").toString();
                assertTrue(file.contains("wildroots:block/wild_carrot_crown_red"), model);
            }
        }
        assertEquals(8, red, "4 sizes × healthy and ailing");
        assertNotNull(FieldPlantBlockstateJsonTest.class.getResource("/assets/wildroots/textures/block/wild_carrot_crown_red.png"));
        assertNotNull(FieldPlantBlockstateJsonTest.class.getResource("/assets/wildroots/textures/item/eastern_carrot_red.png"));
    }

    /**
     * WR-711: the young stages 1 and 2 have a drooping and a wilted model (the later stages never carry a strain),
     * the wilted one with its own brown texture, and every texture the models name exists.
     */
    @Test
    void strainModelsExist() throws Exception {
        JsonObject variants = read("/assets/wildroots/blockstates/wild_carrot.json").getAsJsonObject("variants");
        for (int stage = 1; stage <= 2; stage++) {
            for (String name : new String[] {"drooping", "wilted"}) {
                int strain = name.equals("drooping") ? 1 : 2;
                String model = variants.getAsJsonObject("stage=" + stage + ",strain=" + strain).get("model").getAsString();
                assertEquals("wildroots:block/wild_carrot_stage" + stage + "_" + name, model);
                String file = read("/assets/wildroots/models/" + model.substring("wildroots:".length()) + ".json").toString();
                String texture = com.google.gson.JsonParser.parseString(file).getAsJsonObject().getAsJsonObject("textures")
                        .get("crop").getAsString();
                assertNotNull(FieldPlantBlockstateJsonTest.class.getResource(
                        "/assets/wildroots/textures/" + texture.substring("wildroots:".length()) + ".png"), texture);
            }
        }
    }
}
