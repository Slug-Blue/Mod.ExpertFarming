package io.github.slugblue.wildroots.data;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import io.github.slugblue.wildroots.genetics.api.ClimateStress;
import io.github.slugblue.wildroots.genetics.testutil.MiniJson;
import io.github.slugblue.wildroots.genetics.validation.DefinitionValidator;
import io.github.slugblue.wildroots.genetics.validation.Problem;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * WR-710 review M2: the REAL codec of {@code climate_stress} ({@link SpeciesSchema#CODEC}), not the engine's test
 * mapping. A value of the wrong type, a fraction or an overflow in an integer field is an error that names the path
 * and never throws; a missing number takes its default; the loader's documented conventions (unknown keys are
 * ignored, JSON {@code null} reads as absent, {@code PathCodecs}) apply to this block as to every other.
 */
class SpeciesSchemaTest {

    static JsonObject carrot() {
        return JsonParser.parseString(MiniJson.resourceText("/species/wild_carrot.json")).getAsJsonObject();
    }

    static DataResult<SpeciesSchema.Raw> parse(JsonObject json) {
        return SpeciesSchema.CODEC.parse(JsonOps.INSTANCE, json);
    }

    static JsonObject with(String key, JsonElement value) {
        JsonObject j = carrot();
        j.getAsJsonObject("climate_stress").add(key, value);
        return j;
    }

    static String error(JsonObject json) {
        DataResult<SpeciesSchema.Raw> r = assertDoesNotThrow(() -> parse(json));
        assertTrue(r.error().isPresent(), "expected an error for " + json.get("climate_stress"));
        return r.error().get().message();
    }

    static ClimateStress stress(JsonObject json) {
        SpeciesSchema.Raw raw = parse(json).result().orElseThrow();
        return SpeciesSchema.toDefinition(raw).def().climateStress();
    }

    @Test
    void shippedBlockEqualsTheDesignDefaultsWithTheJungleHumidPenalty() {
        assertEquals(ClimateStress.defaults(0.40), stress(carrot()));
    }

    @Test
    void anEmptyBlockIsTheDefaultsWithoutHumidPenalty() {
        JsonObject j = carrot();
        j.add("climate_stress", new JsonObject());
        assertEquals(ClimateStress.defaults(0), stress(j));
    }

    @Test
    void noBlockMeansTheSpeciesNeverWilts() {
        JsonObject j = carrot();
        j.remove("climate_stress");
        assertEquals(ClimateStress.NONE, stress(j));
    }

    @Test
    void aStringForANumberIsAnErrorWithItsPath() {
        for (String key : List.of("wilt_margin", "humid_penalty", "buffer_shelter", "buffer_irrigation")) {
            String m = error(with(key, JsonParser.parseString("\"0.35\"")));
            assertTrue(m.contains("climate_stress." + key), key + ": " + m);
        }
    }

    @Test
    void anArrayOrANumberForTheObjectIsAnError() {
        for (String bad : List.of("[1,2]", "5", "\"x\"", "true")) {
            JsonObject j = carrot();
            j.add("climate_stress", JsonParser.parseString(bad));
            String m = error(j);
            assertTrue(m.contains("climate_stress"), bad + ": " + m);
        }
    }

    @Test
    void aFractionOrAnOverflowInAnIntegerFieldIsAnError() {
        for (String key : List.of("grace_ticks", "shelter_sky_light")) {
            for (String bad : List.of("2.5", "99999999999", "-99999999999", "1e300")) {
                String m = error(with(key, JsonParser.parseString(bad)));
                assertTrue(m.contains("climate_stress." + key), key + "=" + bad + ": " + m);
            }
            assertTrue(error(with(key, JsonParser.parseString("[1]"))).contains("climate_stress." + key));
        }
    }

    /** The codec only checks structure and types; a number outside its bounds is caught by the validator (SEC-28). */
    @Test
    void numbersOutOfBoundsPassTheCodecAndFailTheValidatorWithThePath() {
        for (String[] bad : new String[][] {{"grace_ticks", "-1"}, {"grace_ticks", "2147483647"},
            {"wilt_margin", "1e300"}, {"humid_penalty", "-5"}}) {
            SpeciesSchema.Mapped m = SpeciesSchema.toDefinition(
                    parse(with(bad[0], JsonParser.parseString(bad[1]))).result().orElseThrow());
            List<Problem> p = new ArrayList<>(DefinitionValidator.validate(m.def()));
            assertTrue(p.stream().anyMatch(x -> x.severity() == Problem.Severity.ERROR
                    && x.path().equals("climate_stress." + bad[0])), bad[0] + "=" + bad[1] + ": " + p);
        }
    }

    /** Conventions of the loader, stated for this block: an unknown key is ignored, {@code null} is "absent". */
    @Test
    void unknownKeysAreIgnoredAndNullReadsAsAbsent() {
        ClimateStress shipped = stress(carrot());
        assertEquals(shipped, stress(with("wilt_margn", JsonParser.parseString("9"))), "a misspelt key changes nothing");
        JsonObject n = with("wilt_margin", JsonNull.INSTANCE);
        assertEquals(ClimateStress.defaults(0.40).wiltMargin(), stress(n).wiltMargin(), "null reads as the default");
        JsonObject whole = carrot();
        whole.add("climate_stress", JsonNull.INSTANCE);
        assertEquals(ClimateStress.NONE, stress(whole), "a null block is no block");
        assertFalse(stress(whole).enabled());
    }
}
