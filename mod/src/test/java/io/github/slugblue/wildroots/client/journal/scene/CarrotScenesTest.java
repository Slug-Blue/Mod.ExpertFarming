package io.github.slugblue.wildroots.client.journal.scene;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/**
 * WR-181l: the two carrot scenes (journal v2 §3.3, §3.6). They load
 * cleanly, stay within every §5.8 limit with a margin of at least 25 %,
 * name only lang keys present in both languages, rewind
 * deterministically, and every {@code pop_item} matches what
 * {@code HarvestService} drops for the plant in that cell at that moment
 * (the GameTest {@code JournalSceneHarvestGameTests} checks the same
 * table against real drops).
 */
class CarrotScenesTest {

    static final List<String> FILES = List.of("carrot_stages.json", "carrot_cycle.json");
    static final String BRUSH = "tool.pollination_brush";

    /**
     * Item ids that {@code HarvestService} drops by stage: 0–2 the
     * seedling lot, 3 (not a mother root) food and a mother root, 4 (a
     * planted mother) the mother root, 5 (a planted mother) the seed lot.
     */
    static final Map<Integer, Set<String>> DROPS = Map.of(
            0, Set.of("wildroots:seed_lot"), 1, Set.of("wildroots:seed_lot"), 2, Set.of("wildroots:seed_lot"),
            3, Set.of("wildroots:wild_carrot_root", "wildroots:mother_root"),
            4, Set.of("wildroots:mother_root"), 5, Set.of("wildroots:seed_lot"));

    static final Pattern STAGE = Pattern.compile("wildroots:wild_carrot\\[.*?stage=(\\d).*]");

    static SceneDef<String> scene(String file) {
        SceneFixtures.Loaded l = SceneFixtures.loadResource(file);
        assertTrue(l.log().isEmpty(), file + ": " + l.log());
        return l.only();
    }

    @Test
    void bothLoadWithTheirIds() {
        assertEquals("scene.carrot_stages", scene("carrot_stages.json").id());
        assertEquals("scene.carrot_cycle", scene("carrot_cycle.json").id());
        SceneDef<String> st = scene("carrot_stages.json");
        assertEquals(List.of(6, 2, 3), List.of(st.sizeX(), st.sizeY(), st.sizeZ()));
        // the data are mirrored in x so that S0 to S3 read left to right with the front row nearest (playtest 2)
        assertEquals(160f, st.camera().yaw(), 1e-4);
        assertEquals(30f, st.camera().pitch(), 1e-4);
        SceneDef<String> cy = scene("carrot_cycle.json");
        assertEquals(List.of(5, 3, 5), List.of(cy.sizeX(), cy.sizeY(), cy.sizeZ()));
        assertEquals(135f, cy.camera().yaw(), 1e-4);
        assertEquals(35f, cy.camera().pitch(), 1e-4);
        assertEquals(SceneDef.FrameLayout.RING, cy.frameLayout());
    }

    /** Every limit used at most to 75 %. */
    @Test
    void limitsWithAQuarterToSpare() throws IOException {
        int bytes = 0;
        for (String f : FILES) {
            String text = SceneFixtures.resource(f);
            int size = text.getBytes(StandardCharsets.UTF_8).length;
            bytes += size;
            assertTrue(size <= SceneLimits.MAX_FILE_BYTES * 3 / 4, f + ": " + size + " bytes");
            JsonObject o = JsonParser.parseString(text).getAsJsonObject();
            assertTrue(o.getAsJsonObject("palette").size() <= SceneLimits.MAX_PALETTE * 3 / 4, f + " palette");
            SceneDef<String> d = scene(f);
            assertTrue(d.volume() <= SceneLimits.MAX_VOLUME * 3 / 4, f + " volume " + d.volume());
            assertTrue(d.steps().size() <= SceneLimits.MAX_STEPS * 3 / 4, f + " steps");
            int ticks = 0;
            int actions = 0;
            int setBlocks = 0;
            for (SceneStep s : d.steps()) {
                ticks += s.duration();
                actions += s.actions().size();
                assertTrue(s.actions().size() <= SceneLimits.MAX_ACTIONS_PER_STEP * 3 / 4,
                        f + " actions in a step: " + s.actions().size());
                assertTrue(s.duration() <= SceneLimits.MAX_DURATION * 3 / 4, f + " duration");
                assertTrue(s.frame().size() <= SceneLimits.MAX_FRAME * 3 / 4, f + " frame");
                int pops = 0;
                int labels = 0;
                List<int[]> bees = new ArrayList<>();
                List<int[]> icons = new ArrayList<>();
                for (SceneAction a : s.actions()) {
                    if (a instanceof SceneAction.SetBlock) {
                        setBlocks++;
                    } else if (a instanceof SceneAction.PopItem) {
                        pops++;
                    } else if (a instanceof SceneAction.Label) {
                        labels++;
                    } else if (a instanceof SceneAction.Bee b) {
                        bees.add(new int[] {b.at(), b.at() + b.ticks()});
                        assertTrue(b.path().size() <= SceneLimits.MAX_PATH * 3 / 4, f + " bee path");
                    } else if (a instanceof SceneAction.Icon i) {
                        icons.add(new int[] {i.at(), i.at() + i.ticks()});
                        assertTrue(i.path().size() <= SceneLimits.MAX_PATH * 3 / 4, f + " icon path");
                    }
                }
                assertTrue(pops <= SceneLimits.MAX_POPS * 3 / 4, f + " pops " + pops);
                assertTrue(labels <= SceneLimits.MAX_LABELS * 3 / 4, f + " labels " + labels);
                assertTrue(SceneParser.maxOverlap(bees) <= SceneLimits.MAX_BEES * 3 / 4, f + " bees");
                assertTrue(SceneParser.maxOverlap(icons) <= SceneLimits.MAX_ICONS * 3 / 4, f + " icons");
            }
            assertTrue(ticks <= SceneLimits.MAX_TOTAL_TICKS * 3 / 4, f + " ticks " + ticks);
            assertTrue(actions <= SceneLimits.MAX_ACTIONS * 3 / 4, f + " actions " + actions);
            assertTrue(setBlocks <= SceneLimits.MAX_SET_BLOCKS * 3 / 4, f + " set_block " + setBlocks);
        }
        // §5.8: JSON of the M1a scenes in the jar ≤ 32 KiB
        assertTrue(bytes <= 32 * 1024, "scenes " + bytes + " bytes");
    }

    /** One harvest seen in a scene: the plant state just before the pop and the items that pop out of it. */
    record Harvest(String scene, int step, SceneAction.Cell cell, String plant, Set<String> items) {
    }

    /** Every harvest in a scene for one reader (with or without the brush), via the rewind of the timeline. */
    static List<Harvest> harvests(SceneDef<String> d, Predicate<List<String>> opened) {
        SceneTimeline tl = new SceneTimeline(d, opened);
        Map<String, Harvest> out = new LinkedHashMap<>();
        for (int n = 0; n < tl.steps().size(); n++) {
            for (SceneAction a : tl.steps().get(n).actions()) {
                if (a instanceof SceneAction.PopItem p) {
                    int[] before = tl.blocksAt(n, p.at() - 1);
                    String plant = d.states().get(before[d.index(p.pos())]);
                    int step = n;
                    Harvest h = out.computeIfAbsent(n + "@" + p.pos(),
                            k -> new Harvest(d.id(), step, p.pos(), plant, new TreeSet<>()));
                    h.items().add(p.item().toString());
                }
            }
        }
        return List.copyOf(out.values());
    }

    static int stageOf(String plant) {
        Matcher m = STAGE.matcher(plant);
        assertTrue(m.matches(), "not a carrot: " + plant);
        return Integer.parseInt(m.group(1));
    }

    @Test
    void everyPopIsWhatTheHarvestDrops() {
        Set<Integer> stagesSeen = new TreeSet<>();
        for (String f : FILES) {
            SceneDef<String> d = scene(f);
            for (Predicate<List<String>> opened : List.<Predicate<List<String>>>of(ids -> ids.isEmpty(),
                    ids -> ids.isEmpty() || ids.contains(BRUSH))) {
                List<Harvest> hs = harvests(d, opened);
                assertFalse(hs.isEmpty(), f + ": no harvest");
                for (Harvest h : hs) {
                    int stage = stageOf(h.plant());
                    stagesSeen.add(stage);
                    assertEquals(DROPS.get(stage), h.items(), f + " " + h.cell() + " at stage " + stage);
                }
            }
        }
        assertEquals(Set.of(1, 3, 4, 5), stagesSeen, "stages harvested in the scenes");
    }

    /**
     * §3.3: the plants of the "stages" scene, stage by stage; front row from
     * seed, back row a planted mother. Stage s stands at x = 5 - s: seen from
     * the front (yaw near 180, the z = 0 row nearest), higher x is further
     * left on the screen, so S0 to S3 and M3 to M5 read left to right
     * (owner playtest 2).
     */
    @Test
    void stagesSceneHasBothRows() {
        SceneDef<String> d = scene("carrot_stages.json");
        int[] c = d.cells();
        for (int s = 0; s < 4; s++) {
            assertEquals(s, stageOf(d.states().get(c[d.index(5 - s, 1, 0)])), "S" + s);
        }
        for (int s = 3; s < 6; s++) {
            assertEquals(s, stageOf(d.states().get(c[d.index(5 - s, 1, 2)])), "M" + s);
        }
        // and on the screen: S0 left of S3 under the scene's own camera
        com.mojang.blaze3d.vertex.PoseStack p = new com.mojang.blaze3d.vertex.PoseStack();
        SceneRenderer.pose(p, 20, d.camera().pitch(), d.camera().yaw(), 132, 56, d.sizeX(), d.sizeY(), d.sizeZ());
        org.joml.Matrix4f m = p.last().pose();
        float s0 = m.transform(new org.joml.Vector4f(5 + 0.5f, 1, 0.5f, 1)).x();
        float s3 = m.transform(new org.joml.Vector4f(2 + 0.5f, 1, 0.5f, 1)).x();
        float m3 = m.transform(new org.joml.Vector4f(2 + 0.5f, 1, 2.5f, 1)).x();
        float m5 = m.transform(new org.joml.Vector4f(0 + 0.5f, 1, 2.5f, 1)).x();
        assertTrue(s0 < s3 && m3 < m5, "rows do not read left to right: S0 " + s0 + ", S3 " + s3 + ", M3 " + m3
                + ", M5 " + m5);
        float front = m.transform(new org.joml.Vector4f(3, 1, 0.5f, 1)).z();
        float back = m.transform(new org.joml.Vector4f(3, 1, 2.5f, 1)).z();
        assertTrue(front > back, "the front row is not nearest");
        assertEquals(6, d.steps().size());
    }

    /** §3.6: the brush step is shown only with {@code tool.pollination_brush}; six steps either way. */
    @Test
    void brushStepOnlyWithTheBrush() {
        SceneDef<String> d = scene("carrot_cycle.json");
        SceneTimeline without = new SceneTimeline(d, ids -> ids.isEmpty());
        SceneTimeline with = new SceneTimeline(d, ids -> ids.isEmpty() || ids.contains(BRUSH));
        assertEquals(6, without.steps().size());
        assertEquals(6, with.steps().size());
        assertEquals("wildroots.journal.scene.carrot_cycle.5", without.steps().get(4).caption());
        assertEquals("wildroots.journal.scene.carrot_cycle.5.brush", with.steps().get(4).caption());
        assertTrue(with.steps().get(4).actions().stream().anyMatch(a -> a instanceof SceneAction.Icon i
                && i.icon() == SceneAction.IconKind.HAND_BRUSH));
        assertTrue(without.steps().get(4).actions().stream().noneMatch(a -> a instanceof SceneAction.Icon i
                && i.icon() == SceneAction.IconKind.HAND_BRUSH));
        SceneTimelineTest.assertDeterministic(d, ids -> ids.isEmpty());
        SceneTimelineTest.assertDeterministic(d, ids -> ids.isEmpty() || ids.contains(BRUSH));
        SceneTimelineTest.assertDeterministic(scene("carrot_stages.json"), ids -> ids.isEmpty());
    }

    /** At stage 3 the plants of the cycle differ in size (§3.6: selection). */
    @Test
    void cycleRipensToDifferentSizes() {
        SceneDef<String> d = scene("carrot_cycle.json");
        SceneTimeline tl = new SceneTimeline(d, ids -> ids.isEmpty());
        int[] ripe = tl.blocksAtStart(3);
        Set<String> sizes = new TreeSet<>();
        for (int x = 1; x <= 3; x++) {
            for (int z = 1; z <= 3; z++) {
                String s = d.states().get(ripe[d.index(x, 1, z)]);
                assertEquals(3, stageOf(s), "cell " + x + "," + z);
                sizes.add(s.replaceAll(".*size=(\\d).*", "$1"));
            }
        }
        assertTrue(sizes.size() >= 3, "sizes " + sizes);
    }

    @Test
    void everyKeyIsInBothLanguages() throws IOException {
        Map<String, String> en = lang("en_us");
        Map<String, String> ru = lang("ru_ru");
        for (String f : FILES) {
            SceneDef<String> d = scene(f);
            for (SceneStep s : d.steps()) {
                List<String> keys = new ArrayList<>();
                keys.add(s.caption());
                for (SceneAction a : s.actions()) {
                    if (a instanceof SceneAction.Label l) {
                        keys.add(l.key());
                        l.tooltip().ifPresent(keys::add);
                    }
                }
                for (String k : keys) {
                    assertTrue(en.containsKey(k), f + ": " + k + " not in en_us");
                    assertTrue(ru.containsKey(k), f + ": " + k + " not in ru_ru");
                    assertFalse(en.get(k).matches(".*\\d.*"), f + ": " + k + " has a number (§3.3)");
                }
            }
        }
    }

    static Map<String, String> lang(String name) throws IOException {
        try (InputStream in = CarrotScenesTest.class.getResourceAsStream("/assets/wildroots/lang/" + name + ".json")) {
            JsonObject o = JsonParser.parseString(new String(in.readAllBytes(), StandardCharsets.UTF_8))
                    .getAsJsonObject();
            Map<String, String> out = new LinkedHashMap<>();
            o.entrySet().forEach(e -> out.put(e.getKey(), e.getValue().getAsString()));
            return out;
        }
    }
}
