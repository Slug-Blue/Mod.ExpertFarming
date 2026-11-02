package io.github.slugblue.wildroots.core.line;

import io.github.slugblue.wildroots.core.Ids;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.common.crafting.conditions.ICondition;
import org.junit.jupiter.api.Test;

/** WR-124: line manifest, registry with disabled lines, condition wildroots:line_loaded. */
class LineRegistryTest {

    static final LineManifest TWO_LINES = LineManifest.parse(new StringReader("""
            {"lines": [
              {"id": "carrot", "wave": 1, "vanilla_crops": ["minecraft:carrot"]},
              {"id": "tomato", "wave": 5, "fd_analog": "farmersdelight:tomatoes"}
            ]}"""));

    @Test
    void jarManifestHasOnlyCarrotInWaveOne() {
        LineManifest m = LineManifest.fromClasspath();
        assertEquals(1, m.lines().size());
        LineManifest.Line carrot = m.lines().get(0);
        assertEquals("carrot", carrot.id());
        assertEquals(1, carrot.wave());
        assertEquals(List.of(Ids.parse("minecraft:carrot")), carrot.vanillaCrops());
        assertEquals(Optional.empty(), carrot.fdAnalog());
    }

    @Test
    void disabledLinesAreRemovedAndUnknownOnesWarn() {
        List<String> warnings = new ArrayList<>();
        LineRegistry r = LineRegistry.build(TWO_LINES, List.of("tomato", "potatoes"), warnings::add);
        assertTrue(r.isEnabled("carrot"));
        assertFalse(r.isEnabled("tomato"));
        assertEquals(List.of("carrot"), r.enabled().stream().map(LineManifest.Line::id).toList());
        assertEquals(2, r.all().size());
        assertEquals(1, warnings.size());
        assertTrue(warnings.get(0).contains("potatoes"), warnings.get(0));
    }

    @Test
    void conditionFollowsTheRegistry() {
        LineRegistry on = LineRegistry.build(TWO_LINES, List.of(), s -> { });
        LineRegistry off = LineRegistry.build(TWO_LINES, List.of("carrot"), s -> { });
        assertTrue(new LineLoadedCondition("carrot", on::isEnabled).test(ICondition.IContext.EMPTY));
        assertFalse(new LineLoadedCondition("carrot", off::isEnabled).test(ICondition.IContext.EMPTY));
        assertFalse(new LineLoadedCondition("nope", on::isEnabled).test(ICondition.IContext.EMPTY));
    }

    @Test
    void conditionJsonRoundTrip() {
        JsonObject json = JsonParser.parseString("{\"type\": \"wildroots:line_loaded\", \"line\": \"carrot\"}")
                .getAsJsonObject();
        LineLoadedCondition c = LineLoadedCondition.SERIALIZER.read(json);
        assertEquals("carrot", c.line());
        assertEquals(Ids.of("wildroots", "line_loaded"), LineLoadedCondition.SERIALIZER.getID());
        JsonObject out = new JsonObject();
        LineLoadedCondition.SERIALIZER.write(out, c);
        assertEquals("carrot", out.get("line").getAsString());
        assertThrows(RuntimeException.class, () -> LineLoadedCondition.SERIALIZER.read(new JsonObject()));
    }

    @Test
    void contentOfADisabledLineIsHidden() {
        LineRegistry off = LineRegistry.build(TWO_LINES, List.of("carrot"), s -> { });
        assertFalse(LineContent.visible(Optional.of("carrot"), off));
        assertTrue(LineContent.visible(Optional.of("tomato"), off));
        assertTrue(LineContent.visible(Optional.empty(), off), "core items have no line");
    }

    @Test
    void malformedManifestIsRejected() {
        assertThrows(RuntimeException.class, () -> LineManifest.parse(new StringReader(
                "{\"lines\": [{\"id\": \"Carrot!\", \"wave\": 1}]}")));
        assertThrows(RuntimeException.class, () -> LineManifest.parse(new StringReader(
                "{\"lines\": [{\"id\": \"carrot\"}]}")));
    }
}
