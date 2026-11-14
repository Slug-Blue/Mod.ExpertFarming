package io.github.slugblue.wildroots.client.journal.page;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.slugblue.wildroots.core.Ids;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

/**
 * WR-181d: the 2D fallback frame is read from a scene file on its own
 * (journal v2 §3.13), so it works on a JSON stub before the scene engine
 * exists; the engine's fields are ignored, a broken file is one log line.
 */
class SceneFrameTest {

    /** A stub in the scene format of §5.8 (engine fields included, which the frame ignores). */
    static final String STUB = """
            { "id": "scene.carrot_cycle", "species": "wildroots:wild_carrot", "size": [5, 3, 5],
              "frame_layout": "ring",
              "palette": { "f": "minecraft:farmland[moisture=7]" }, "layers": [["fffff"]],
              "camera": { "yaw": 225, "pitch": 35, "zoom": 1.0 },
              "steps": [
                { "caption": "wildroots.journal.scene.carrot_cycle.1", "duration": 80,
                  "actions": [ { "at": 0, "type": "icon", "icon": "hand_lot" } ],
                  "frame": [ { "item": "wildroots:seed_lot" }, { "sprite": "wildroots:block/wild_carrot_stage0" } ] },
                { "caption": "wildroots.journal.scene.carrot_cycle.5.brush", "unlock": ["tool.pollination_brush"],
                  "frame": [ { "item": "wildroots:mother_root" } ] },
                { "caption": "wildroots.journal.scene.carrot_cycle.6" } ] }
            """;

    static Map<ResourceLocation, byte[]> files(String... texts) {
        Map<ResourceLocation, byte[]> m = new LinkedHashMap<>();
        for (int i = 0; i < texts.length; i++) {
            m.put(Ids.mod("journal/scenes/s" + i + ".json"), texts[i].getBytes(StandardCharsets.UTF_8));
        }
        return m;
    }

    @Test
    void aStubSceneGivesItsFrame() {
        List<String> log = new ArrayList<>();
        Map<String, SceneFrame> got = SceneFrames.parseAll(files(STUB), log);
        assertEquals(List.of(), log);
        SceneFrame f = got.get("scene.carrot_cycle");
        assertEquals(SceneFrame.Layout.RING, f.layout());
        assertEquals(3, f.steps().size());
        assertEquals(new SceneFrame.Icon(Optional.of(Ids.mod("seed_lot")), Optional.empty()),
                f.steps().get(0).icons().get(0));
        assertEquals(new SceneFrame.Icon(Optional.empty(), Optional.of(Ids.mod("block/wild_carrot_stage0"))),
                f.steps().get(0).icons().get(1));
        assertEquals(List.of("tool.pollination_brush"), f.steps().get(1).unlock());
        assertEquals(List.of(), f.steps().get(2).icons(), "a step without frame: its caption only");
    }

    /** The shipped cycle: 6 steps with or without the brush, as in 3D (the brush step replaces the bee step). */
    @Test
    void theShippedCycleShowsSixStepsWithOrWithoutTheBrush() throws Exception {
        java.nio.file.Path p = java.nio.file.Path.of(System.getProperty("user.dir"))
                .resolve("src/main/resources/assets/wildroots/journal/scenes/carrot_cycle.json");
        if (!java.nio.file.Files.exists(p)) {
            p = java.nio.file.Path.of("mod").resolve("src/main/resources/assets/wildroots/journal/scenes/carrot_cycle.json");
        }
        SceneFrame f = SceneFrame.parse(io.github.slugblue.wildroots.client.journal.GuideLoader
                .parseObject(java.nio.file.Files.readAllBytes(p)));
        java.util.function.Predicate<List<String>> noBrush = u -> u.isEmpty();
        java.util.function.Predicate<List<String>> brush = u -> u.isEmpty() || u.contains("tool.pollination_brush");
        List<SceneFrame.Step> without = FallbackFrameView.visibleSteps(f, noBrush);
        List<SceneFrame.Step> with = FallbackFrameView.visibleSteps(f, brush);
        assertEquals(6, without.size());
        assertEquals(6, with.size());
        assertTrue(with.stream().anyMatch(s -> s.caption().endsWith(".5.brush")));
        assertTrue(without.stream().noneMatch(s -> s.caption().endsWith(".5.brush")));
    }

    @Test
    void brokenFramesAreOneLineEach() {
        String nine = "[" + "{\"item\":\"wildroots:seed_lot\"},".repeat(8) + "{\"item\":\"wildroots:seed_lot\"}]";
        String[] bad = {
            "{",
            "{\"id\": \"scene.a\", \"steps\": []}",
            "{\"id\": \"scene.b\", \"frame_layout\": \"spiral\", \"steps\": [{\"caption\": \"a.b\"}]}",
            "{\"id\": \"scene.c\", \"steps\": [{\"caption\": \"a.b\", \"frame\": " + nine + "}]}",
            "{\"id\": \"scene.d\", \"steps\": [{\"caption\": \"a.b\", \"frame\": [{\"item\": \"a:b\", \"sprite\": \"a:c\"}]}]}",
            "{\"id\": \"scene.e\", \"steps\": [{\"caption\": \"Literal Text\"}]}",
            "{\"id\": \"scene.f\", \"steps\": " + "[{\"caption\": \"a.b\"}".repeat(1)
                    + ",{\"caption\": \"a.b\"}".repeat(SceneFrame.MAX_STEPS) + "]}",
        };
        List<String> log = new ArrayList<>();
        Map<String, SceneFrame> got = SceneFrames.parseAll(files(bad), log);
        assertEquals(Map.of(), got);
        assertEquals(bad.length, log.size(), log::toString);
        log.clear();
        assertEquals(1, SceneFrames.parseAll(files(STUB, STUB), log).size());
        assertTrue(log.get(0).contains("duplicate"));
    }
}
