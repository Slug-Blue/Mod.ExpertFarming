package io.github.slugblue.wildroots.client.journal.scene;

import static io.github.slugblue.wildroots.client.journal.scene.SceneFixtures.POP;
import static io.github.slugblue.wildroots.client.journal.scene.SceneFixtures.load;
import static io.github.slugblue.wildroots.client.journal.scene.SceneFixtures.repeat;
import static io.github.slugblue.wildroots.client.journal.scene.SceneFixtures.scene;
import static io.github.slugblue.wildroots.client.journal.scene.SceneFixtures.setBlock;
import static io.github.slugblue.wildroots.client.journal.scene.SceneFixtures.step;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.slugblue.wildroots.client.journal.GuideFiles;
import io.github.slugblue.wildroots.core.Ids;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.IoSupplier;
import org.junit.jupiter.api.Test;

/**
 * WR-181k: the scene loader (journal v2 §5.8). Each limit of the table:
 * over it, the scene is not loaded, exactly one log line, nothing throws.
 * What the spec replaces by air (NBT, block entities, unknown blocks) is
 * replaced with one line and the scene still loads. Pure, no game.
 */
class SceneParserTest {

    /** Over a limit: no scene, one log line. */
    static void rejected(String json, String why) {
        SceneFixtures.Loaded l = load(json);
        assertTrue(l.scenes().isEmpty(), why + ": loaded anyway");
        assertEquals(1, l.log().size(), why + ": log " + l.log());
    }

    static String withStep(String actions) {
        return scene(step(actions));
    }

    @Test
    void validSceneLoads() {
        SceneFixtures.Loaded l = load(withStep(POP + ", " + setBlock(5, 1, 1, 1, "minecraft:air")));
        SceneDef<String> d = l.only();
        assertTrue(l.log().isEmpty(), "log " + l.log());
        assertEquals("scene.test", d.id());
        assertEquals(3 * 2 * 3, d.volume());
        assertEquals("minecraft:air", d.states().get(0));
        assertEquals("wildroots:wild_carrot[stage=0,size=1]", d.states().get(d.cells()[d.index(1, 1, 1)]));
        assertEquals(2, d.steps().get(0).actions().size());
        assertEquals(1, d.steps().get(0).frame().size());
    }

    // ------------------------------------------------------------------ structure

    @Test
    void axisOverSevenIsRejected() {
        rejected(scene(step(POP)).replace("[3, 2, 3]", "[8, 1, 1]"), "x = 8");
        rejected(scene(step(POP)).replace("[3, 2, 3]", "[0, 2, 3]"), "x = 0");
    }

    @Test
    void volumeOver245IsRejected() {
        // 7 × 6 × 7 = 294 > 245, each axis within 1…7
        StringBuilder layers = new StringBuilder();
        for (int y = 0; y < 6; y++) {
            layers.append(y == 0 ? "" : ", ").append("[").append(repeat("\"fffffff\"", 7)).append("]");
        }
        String json = scene(step(POP)).replace("[3, 2, 3]", "[7, 6, 7]")
                .replaceAll("\"layers\": \\[.*?]],", "\"layers\": [" + layers + "],");
        rejected(json, "7×6×7");
    }

    @Test
    void sevenFiveSevenIsAllowed() {
        StringBuilder layers = new StringBuilder();
        for (int y = 0; y < 5; y++) {
            layers.append(y == 0 ? "" : ", ").append("[").append(repeat("\"fffffff\"", 7)).append("]");
        }
        String json = scene(step("")).replace("[3, 2, 3]", "[7, 5, 7]")
                .replaceAll("\"layers\": \\[.*?]],", "\"layers\": [" + layers + "],");
        assertEquals(245, load(json).only().volume());
    }

    @Test
    void paletteOverSixteenIsRejected() {
        StringBuilder p = new StringBuilder("\"f\": \"minecraft:farmland\", \".\": \"minecraft:air\", \"c\": \"wildroots:wild_carrot\"");
        for (int i = 0; i < 14; i++) {
            p.append(", \"").append((char) ('A' + i)).append("\": \"minecraft:grass_block\"");
        }
        String json = scene(step(POP)).replaceAll("\"palette\": \\{.*?},", "\"palette\": {" + p + "},");
        rejected(json, "17 palette entries");
    }

    @Test
    void paletteKeyOfTwoCharactersIsRejected() {
        rejected(scene(step(POP)).replace("\"f\": \"minecraft:farmland[moisture=7]\"",
                "\"ff\": \"minecraft:farmland[moisture=7]\""), "key ff");
    }

    @Test
    void stateOver128CharactersIsRejected() {
        String longState = "minecraft:" + "a".repeat(130);
        rejected(scene(step(POP)).replace("minecraft:farmland[moisture=7]", longState), "palette state");
        rejected(withStep(setBlock(1, 0, 0, 0, longState)), "set_block state");
    }

    @Test
    void layerShapeIsChecked() {
        rejected(scene(step(POP)).replace("[\"fff\", \"fff\", \"fff\"]", "[\"fff\", \"fff\"]"), "rows");
        rejected(scene(step(POP)).replace("[\"fff\", \"fff\", \"fff\"]", "[\"ffff\", \"fff\", \"fff\"]"), "row length");
        rejected(scene(step(POP)).replace("[\"fff\", \"fff\", \"fff\"]", "[\"fff\", \"fxf\", \"fff\"]"), "unknown char");
    }

    // ------------------------------------------------------------------ what becomes air

    @Test
    void nbtInAStateBecomesAir() {
        SceneFixtures.Loaded l = load(scene(step(POP)).replace("minecraft:farmland[moisture=7]",
                "minecraft:chest{Items:[]}"));
        SceneDef<String> d = l.only();
        assertEquals(0, d.cells()[d.index(0, 0, 0)], "NBT state is not air");
        assertEquals(1, l.log().size(), "log " + l.log());
    }

    @Test
    void blockEntityBecomesAir() {
        SceneFixtures.Loaded l = load(withStep(setBlock(1, 0, 1, 0, "minecraft:chest[facing=north]") + ", " + POP));
        SceneDef<String> d = l.only();
        SceneAction.SetBlock b = (SceneAction.SetBlock) d.steps().get(0).actions().get(0);
        assertEquals(0, b.state(), "chest is not air");
        assertEquals(1, l.log().size(), "log " + l.log());
    }

    @Test
    void unknownBlockBecomesAir() {
        SceneFixtures.Loaded l = load(scene(step(POP)).replace("minecraft:farmland[moisture=7]", "mymod:nothing"));
        assertEquals(0, l.only().cells()[0]);
        assertEquals(1, l.log().size(), "log " + l.log());
    }

    @Test
    void nbtFieldOnAnActionIsRejected() {
        rejected(withStep("{\"at\": 5, \"type\": \"pop_item\", \"pos\": [1, 1, 1], \"item\": \"wildroots:seed_lot\","
                + " \"nbt\": \"{x:1}\"}"), "nbt on pop_item");
    }

    @Test
    void unknownItemDropsTheAction() {
        SceneFixtures.Loaded l = load(withStep(POP.replace("wildroots:seed_lot", "mymod:gold") + ", " + POP));
        assertEquals(1, l.only().steps().get(0).actions().size());
        assertEquals(1, l.log().size(), "log " + l.log());
    }

    @Test
    void unknownVariantIsRejected() {
        rejected(withStep(POP.replace("}", ", \"variant\": \"colour=green\"}")), "variant");
        assertEquals(1, load(withStep(POP.replace("}", ", \"variant\": \"colour=purple\"}"))).only().steps().get(0)
                .actions().size());
    }

    // ------------------------------------------------------------------ steps

    @Test
    void stepCountIsLimited() {
        rejected(scene(repeat(step(POP), 17)), "17 steps");
        rejected(scene(""), "no steps");
        assertEquals(16, load(scene(repeat(step(POP), 16))).only().steps().size());
    }

    @Test
    void durationIsLimited() {
        rejected(scene(step(9, POP)), "duration 9");
        rejected(scene(step(401, POP)), "duration 401");
        load(scene(step(10, POP))).only();
        load(scene(step(400, POP))).only();
    }

    @Test
    void wholeSceneIsAtMostTwoMinutes() {
        // 7 × 400 = 2800 > 2400
        rejected(scene(repeat(step(400, POP), 7)), "2800 ticks");
        load(scene(repeat(step(400, POP), 6))).only();
    }

    @Test
    void actionsPerStepAreLimited() {
        rejected(withStep(repeat(setBlock(1, 0, 0, 0, "minecraft:air"), 17)), "17 actions");
        load(withStep(repeat(setBlock(1, 0, 0, 0, "minecraft:air"), 16))).only();
    }

    @Test
    void actionsPerSceneAreLimited() {
        String label = "{\"at\": 1, \"type\": \"label\", \"pos\": [1, 1, 1], \"key\": \"wildroots.tooltip.stage.seed\"}";
        String icon = "{\"at\": 1, \"type\": \"icon\", \"icon\": \"hand\", \"path\": [[0, 1, 0]], \"ticks\": 1}";
        // 8 labels + 4 icons + 4 highlights = 16 per step, none of them set_block; 9 steps = 144 > 128
        String hl = "{\"at\": 1, \"type\": \"highlight\", \"from\": [0, 0, 0]}";
        String full = repeat(label, 8) + ", " + repeat(icon, 4) + ", " + repeat(hl, 4);
        rejected(scene(repeat(step(10, full), 9)), "144 actions");
        load(scene(repeat(step(10, full), 8))).only();
    }

    @Test
    void setBlocksPerSceneAreLimited() {
        String sixteen = repeat(setBlock(1, 0, 0, 0, "minecraft:air"), 16);
        rejected(scene(repeat(step(10, sixteen), 4) + ", " + step(10, setBlock(1, 0, 0, 0, "minecraft:air"))),
                "65 set_block");
        load(scene(repeat(step(10, sixteen), 4))).only();
    }

    @Test
    void atMustLieInTheStep() {
        rejected(withStep(POP.replace("\"at\": 5", "\"at\": 40")), "at = duration");
        rejected(withStep(POP.replace("\"at\": 5", "\"at\": -1")), "at < 0");
    }

    @Test
    void cellsOutsideTheStructureAreRejected() {
        rejected(withStep(setBlock(1, 3, 0, 0, "minecraft:air")), "x = 3");
        rejected(withStep(setBlock(1, 0, 2, 0, "minecraft:air")), "y = 2");
        rejected(withStep(POP.replace("[1, 1, 1]", "[1, 1, -1]")), "pop z = -1");
        rejected(withStep("{\"at\": 0, \"type\": \"highlight\", \"from\": [0, 0, 0], \"to\": [3, 0, 0]}"), "highlight");
    }

    @Test
    void pathsKeepAMarginOfOneCell() {
        String ok = "{\"at\": 0, \"type\": \"bee\", \"path\": [[-1, -1, -1], [3, 2, 3]], \"ticks\": 20}";
        load(withStep(ok)).only();
        rejected(withStep(ok.replace("[3, 2, 3]", "[4, 2, 3]")), "x = 4");
        rejected(withStep(ok.replace("[-1, -1, -1]", "[-1.5, 0, 0]")), "x = -1.5");
    }

    @Test
    void pathLengthIsLimited() {
        String p8 = repeat("[0, 0, 0]", 8);
        load(withStep("{\"at\": 0, \"type\": \"bee\", \"path\": [" + p8 + "], \"ticks\": 20}")).only();
        rejected(withStep("{\"at\": 0, \"type\": \"bee\", \"path\": [" + p8 + ", [0, 0, 0]], \"ticks\": 20}"), "9 points");
        rejected(withStep("{\"at\": 0, \"type\": \"bee\", \"path\": [], \"ticks\": 20}"), "empty path");
    }

    @Test
    void ticksMustEndInTheStep() {
        rejected(withStep("{\"at\": 30, \"type\": \"bee\", \"path\": [[0, 0, 0]], \"ticks\": 11}"), "past the step");
        rejected(withStep("{\"at\": 0, \"type\": \"bee\", \"path\": [[0, 0, 0]]}"), "no ticks");
    }

    @Test
    void popsAtOnceAreLimited() {
        rejected(withStep(repeat(POP, 13)), "13 pops");
        load(withStep(repeat(POP, 12))).only();
    }

    @Test
    void beesAtOnceAreLimited() {
        String bee = "{\"at\": 0, \"type\": \"bee\", \"path\": [[0, 0, 0]], \"ticks\": 20}";
        rejected(withStep(repeat(bee, 5)), "5 bees");
        load(withStep(repeat(bee, 4))).only();
        // five bees one after another are never more than one at a time
        String late = "{\"at\": 25, \"type\": \"bee\", \"path\": [[0, 0, 0]], \"ticks\": 10}";
        load(withStep(repeat(bee, 4) + ", " + late)).only();
    }

    @Test
    void iconsAtOnceAreLimited() {
        String icon = "{\"at\": 0, \"type\": \"icon\", \"icon\": \"clock\", \"path\": [[0, 0, 0]], \"ticks\": 20}";
        rejected(withStep(repeat(icon, 5)), "5 icons");
        load(withStep(repeat(icon, 4))).only();
    }

    @Test
    void labelsAtOnceAreLimited() {
        String label = "{\"at\": 0, \"type\": \"label\", \"pos\": [1, 1, 1], \"key\": \"wildroots.tooltip.stage.seed\"}";
        rejected(withStep(repeat(label, 9)), "9 labels");
        load(withStep(repeat(label, 8))).only();
    }

    @Test
    void textsAreOnlyLangKeys() {
        rejected(withStep("{\"at\": 0, \"type\": \"label\", \"pos\": [1, 1, 1], \"key\": \"Hello there\"}"), "literal");
        rejected(scene(step(POP).replace("wildroots.journal.scene.test.1", "Step one!")), "caption literal");
    }

    @Test
    void unknownTypesAreRejected() {
        rejected(withStep("{\"at\": 0, \"type\": \"explode\"}"), "action type");
        rejected(withStep("{\"at\": 0, \"type\": \"icon\", \"icon\": \"skull\", \"path\": [[0, 0, 0]], \"ticks\": 5}"),
                "icon");
    }

    @Test
    void unlockListsAreLimited() {
        StringBuilder ids = new StringBuilder();
        for (int i = 0; i < 9; i++) {
            ids.append(i == 0 ? "" : ", ").append("\"tool.x").append(i).append("\"");
        }
        rejected(scene(step(POP).replace("\"actions\"", "\"unlock\": [" + ids + "], \"actions\"")), "9 unlock ids");
    }

    @Test
    void frameIsLimited() {
        String icons = repeat("{\"item\": \"wildroots:seed_lot\"}", 9);
        rejected(scene(step(POP).replaceAll("\"frame\": \\[.*?]", "\"frame\": [" + icons + "]")), "9 frame icons");
    }

    @Test
    void cameraIsClampedNotRejected() {
        SceneDef<String> d = load(scene(step(POP)).replace("\"yaw\": 200, \"pitch\": 30, \"zoom\": 1.0",
                "\"yaw\": -30, \"pitch\": 95, \"zoom\": 9")).only();
        assertEquals(330f, d.camera().yaw(), 1e-4);
        assertEquals(SceneLimits.MAX_PITCH, d.camera().pitch(), 1e-4);
        assertEquals(SceneLimits.MAX_ZOOM, d.camera().zoom(), 1e-4);
    }

    @Test
    void badJsonIsOneLine() {
        rejected("{\"id\": ", "truncated");
        rejected("[1, 2]", "not an object");
        rejected("{" + "\"a\": [".repeat(20) + "]".repeat(20) + "}", "too deep");
    }

    @Test
    void firstSceneOfAnIdWins() {
        List<String> log = new ArrayList<>();
        Map<ResourceLocation, byte[]> files = new java.util.TreeMap<>();
        files.put(Ids.mod("journal/scenes/a.json"), withStep(POP).getBytes(StandardCharsets.UTF_8));
        files.put(Ids.mod("journal/scenes/b.json"), withStep("").getBytes(StandardCharsets.UTF_8));
        var scenes = SceneParser.loadAll(files, SceneFixtures.RESOLVER, SceneFixtures.ITEM_CHECK, log);
        assertEquals(1, scenes.size());
        assertEquals(1, scenes.get("scene.test").steps().get(0).actions().size());
        assertEquals(1, log.size(), "log " + log);
    }

    /** The folder limits (16 KiB a file, 32 files, 256 KiB) are applied by the guide reader with the section's numbers. */
    @Test
    void fileLimitsOfTheFolder() {
        Map<ResourceLocation, IoSupplier<InputStream>> files = new LinkedHashMap<>();
        byte[] big = new byte[SceneLimits.MAX_FILE_BYTES + 1];
        java.util.Arrays.fill(big, (byte) ' ');
        files.put(Ids.mod("journal/scenes/big.json"), () -> new ByteArrayInputStream(big));
        for (int i = 0; i < 40; i++) {
            byte[] ok = withStep(POP).getBytes(StandardCharsets.UTF_8);
            files.put(Ids.mod("journal/scenes/s" + (100 + i) + ".json"), () -> new ByteArrayInputStream(ok));
        }
        List<String> log = new ArrayList<>();
        Map<ResourceLocation, byte[]> read = GuideFiles.read(files, SceneRegistry.INSTANCE.folder(),
                SceneRegistry.INSTANCE.maxFileBytes(), SceneRegistry.INSTANCE.maxFiles(),
                SceneRegistry.INSTANCE.maxTotalBytes(), log);
        assertEquals(SceneLimits.MAX_FILES, read.size());
        assertTrue(read.keySet().stream().noneMatch(rl -> rl.getPath().endsWith("big.json")));
        assertEquals(2, log.size(), "log " + log);
        assertEquals(16 * 1024, SceneLimits.MAX_FILE_BYTES);
        assertEquals(256 * 1024, SceneLimits.MAX_TOTAL_BYTES);
    }
}
