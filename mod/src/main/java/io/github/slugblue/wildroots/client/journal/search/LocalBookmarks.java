package io.github.slugblue.wildroots.client.journal.search;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.github.slugblue.wildroots.client.journal.GuideLimits;
import io.github.slugblue.wildroots.client.journal.GuideLoader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * Bookmarks of guide entries, kept on this computer (journal v2 §3.10):
 * {@code config/wildroots-journal-client.json}, by world or server key, at
 * most {@value #MAX_PER_WORLD} a world, the file at most
 * {@value #MAX_FILE_BYTES} bytes (the worlds used longest ago go first).
 * Records are bookmarked on the server instead ({@code PIN}). A broken or
 * oversized file reads as empty; nothing here throws.
 */
@OnlyIn(Dist.CLIENT)
public final class LocalBookmarks {

    public static final int MAX_PER_WORLD = 16;
    public static final int MAX_FILE_BYTES = 4 * 1024;
    static final int MAX_KEY = 128;

    /** World key → entry ids, most recently used world last. */
    private final LinkedHashMap<String, List<String>> worlds = new LinkedHashMap<>();

    public static LocalBookmarks read(byte[] bytes) {
        LocalBookmarks b = new LocalBookmarks();
        if (bytes == null || bytes.length == 0 || bytes.length > MAX_FILE_BYTES) {
            return b;
        }
        try {
            JsonObject o = GuideLoader.parseObject(bytes);
            for (Map.Entry<String, JsonElement> w : o.entrySet()) {
                if (w.getKey().length() > MAX_KEY || !(w.getValue() instanceof JsonArray a)) {
                    continue;
                }
                List<String> ids = new ArrayList<>();
                for (JsonElement e : a) {
                    if (e.isJsonPrimitive() && e.getAsJsonPrimitive().isString()
                            && GuideLimits.ID.matcher(e.getAsString()).matches() && !ids.contains(e.getAsString())
                            && ids.size() < MAX_PER_WORLD) {
                        ids.add(e.getAsString());
                    }
                }
                b.worlds.put(w.getKey(), ids);
            }
        } catch (Exception | StackOverflowError e) {
            return new LocalBookmarks();
        }
        return b;
    }

    public List<String> get(String world) {
        return List.copyOf(worlds.getOrDefault(world, List.of()));
    }

    public boolean has(String world, String id) {
        return worlds.getOrDefault(world, List.of()).contains(id);
    }

    /** Adds a bookmark; {@code false} when the world already has {@value #MAX_PER_WORLD}. */
    public boolean add(String world, String id) {
        if (world.length() > MAX_KEY || !GuideLimits.ID.matcher(id).matches()) {
            return false;
        }
        List<String> ids = new ArrayList<>(worlds.getOrDefault(world, List.of()));
        if (ids.contains(id)) {
            touch(world, ids);
            return true;
        }
        if (ids.size() >= MAX_PER_WORLD) {
            return false;
        }
        ids.add(id);
        touch(world, ids);
        return true;
    }

    public void remove(String world, String id) {
        List<String> ids = new ArrayList<>(worlds.getOrDefault(world, List.of()));
        if (ids.remove(id)) {
            touch(world, ids);
        }
    }

    private void touch(String world, List<String> ids) {
        worlds.remove(world);
        if (!ids.isEmpty()) {
            worlds.put(world, ids);
        }
    }

    /** The file, at most {@value #MAX_FILE_BYTES} bytes: the worlds used longest ago are dropped to fit. */
    public byte[] write() {
        while (true) {
            JsonObject o = new JsonObject();
            worlds.forEach((w, ids) -> {
                JsonArray a = new JsonArray();
                ids.forEach(a::add);
                o.add(w, a);
            });
            byte[] b = o.toString().getBytes(StandardCharsets.UTF_8);
            if (b.length <= MAX_FILE_BYTES || worlds.isEmpty()) {
                return b;
            }
            worlds.remove(worlds.keySet().iterator().next());
        }
    }
}
