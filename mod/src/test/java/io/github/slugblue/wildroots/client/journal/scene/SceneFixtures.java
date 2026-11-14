package io.github.slugblue.wildroots.client.journal.scene;

import com.google.gson.JsonObject;
import io.github.slugblue.wildroots.client.journal.GuideLoader;
import io.github.slugblue.wildroots.core.Ids;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Predicate;
import net.minecraft.resources.ResourceLocation;

/** Scenes without the game: a fake block table, a fake item registry, a small valid scene to break. */
final class SceneFixtures {

    static final Set<String> BLOCKS = Set.of("minecraft:air", "minecraft:farmland", "minecraft:grass_block",
            "minecraft:grass", "minecraft:beehive", "wildroots:wild_carrot");
    /** Blocks with a block entity in the fake table (not allowed in a scene). */
    static final Set<String> ENTITY_BLOCKS = Set.of("minecraft:chest", "minecraft:spawner");
    static final Set<String> ITEMS = Set.of("wildroots:seed_lot", "wildroots:mother_root",
            "wildroots:wild_carrot_root", "wildroots:eastern_carrot", "minecraft:beehive");
    static final Predicate<ResourceLocation> ITEM_CHECK = rl -> ITEMS.contains(rl.toString());

    /** Normalises {@code ns:path[props]} to a full id; the real resolver uses BlockStateParser. */
    static final StateResolver<String> RESOLVER = new StateResolver<>() {
        @Override
        public String air() {
            return "minecraft:air";
        }

        @Override
        public String resolve(String state, String where, List<String> log) {
            String id = state.contains("[") ? state.substring(0, state.indexOf('[')) : state;
            String full = id.contains(":") ? id : "minecraft:" + id;
            if (ENTITY_BLOCKS.contains(full)) {
                log.add(where + ": " + state + " has a block entity, air instead");
                return air();
            }
            if (!BLOCKS.contains(full)) {
                log.add(where + ": unknown block " + full + ", air instead");
                return air();
            }
            return full + state.substring(id.length());
        }
    };

    static final ResourceLocation FILE = Ids.mod("journal/scenes/test.json");

    private SceneFixtures() {
    }

    /**
     * A valid 3 × 2 × 3 scene: farmland and a carrot; one step that pops a
     * lot. {@code %STEPS%} and the like are replaced by the tests.
     */
    static String scene(String steps) {
        return "{\"id\": \"scene.test\", \"species\": \"wildroots:wild_carrot\", \"size\": [3, 2, 3],"
                + "\"palette\": {\"f\": \"minecraft:farmland[moisture=7]\", \".\": \"minecraft:air\","
                + "\"c\": \"wildroots:wild_carrot[stage=0,size=1]\"},"
                + "\"layers\": [[\"fff\", \"fff\", \"fff\"], [\"...\", \".c.\", \"...\"]],"
                + "\"camera\": {\"yaw\": 200, \"pitch\": 30, \"zoom\": 1.0},"
                + "\"steps\": [" + steps + "]}";
    }

    static String step(String actions) {
        return step(40, actions);
    }

    static String step(int duration, String actions) {
        return "{\"caption\": \"wildroots.journal.scene.test.1\", \"duration\": " + duration + ", \"actions\": ["
                + actions + "], \"frame\": [{\"item\": \"wildroots:seed_lot\"}]}";
    }

    static final String POP = "{\"at\": 5, \"type\": \"pop_item\", \"pos\": [1, 1, 1], \"item\": \"wildroots:seed_lot\"}";

    static String setBlock(int at, int x, int y, int z, String state) {
        return "{\"at\": " + at + ", \"type\": \"set_block\", \"pos\": [" + x + ", " + y + ", " + z + "], \"state\": \""
                + state + "\"}";
    }

    static String repeat(String one, int n) {
        List<String> out = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            out.add(one);
        }
        return String.join(", ", out);
    }

    /** What {@link SceneParser#loadAll} gives for one file, and its log. */
    record Loaded(Map<String, SceneDef<String>> scenes, List<String> log) {

        SceneDef<String> only() {
            if (scenes.size() != 1) {
                throw new AssertionError("expected one scene, got " + scenes.keySet() + "; log " + log);
            }
            return scenes.values().iterator().next();
        }
    }

    static Loaded load(String json) {
        List<String> log = new ArrayList<>();
        Map<ResourceLocation, byte[]> files = new TreeMap<>();
        files.put(FILE, json.getBytes(StandardCharsets.UTF_8));
        return new Loaded(SceneParser.loadAll(files, RESOLVER, ITEM_CHECK, log), log);
    }

    static JsonObject object(String json) {
        try {
            return GuideLoader.parseObject(json.getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** A shipped scene file of the mod, by file name. */
    static String resource(String name) {
        try (InputStream in = SceneFixtures.class.getResourceAsStream("/assets/wildroots/journal/scenes/" + name)) {
            if (in == null) {
                throw new AssertionError("no scene file " + name);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    static Loaded loadResource(String name) {
        List<String> log = new ArrayList<>();
        Map<ResourceLocation, byte[]> files = new TreeMap<>();
        files.put(Ids.mod("journal/scenes/" + name), resource(name).getBytes(StandardCharsets.UTF_8));
        return new Loaded(SceneParser.loadAll(files, RESOLVER, ITEM_CHECK, log), log);
    }
}
