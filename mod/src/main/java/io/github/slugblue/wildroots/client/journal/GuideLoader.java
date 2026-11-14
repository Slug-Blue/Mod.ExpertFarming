package io.github.slugblue.wildroots.client.journal;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import io.github.slugblue.wildroots.client.journal.page.PageData;
import io.github.slugblue.wildroots.client.journal.page.PageParser;
import io.github.slugblue.wildroots.client.journal.page.PageParsers;
import io.github.slugblue.wildroots.data.DefinitionLoader;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.IoSupplier;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * Reads the guide files of {@code assets/wildroots/journal/} into a
 * {@link GuideBook} (journal v2 §5.2; WR-181a). No game state: the reload
 * listener calls it with the files of the resource manager, tests with
 * fixtures.
 *
 * <p>A server resource pack may replace these files, so the loader never
 * throws: each file, entry and page is read inside its own
 * {@code try/catch}, and whatever breaks a limit of {@link GuideLimits},
 * is not JSON, names an unknown page type or item, or an unknown category
 * is skipped with one line in {@link Result#log()}. Text never comes from
 * JSON: only lang keys.
 *
 * <p>Files, in order of their path:
 * <ul>
 *   <li>{@code journal/categories/<id>.json}: {@code id}, {@code icon},
 *       {@code group} (only {@code guide}), {@code order};</li>
 *   <li>{@code journal/entries/<id>.json}: {@code id} ({@code guide.*}),
 *       {@code category}, {@code icon}, {@code order}, {@code unlock},
 *       {@code items}, {@code see_also}, {@code pages};</li>
 *   <li>{@code journal/hint_links.json}: hint id → entry id (§4.3).</li>
 * </ul>
 * {@code journal/scenes/} is the scene engine's ({@link GuideSection}).
 */
@OnlyIn(Dist.CLIENT)
public final class GuideLoader {

    /** Folder of the guide under {@code assets/<namespace>/}. */
    public static final String ROOT = "journal";
    public static final String NAMESPACE = "wildroots";
    static final String CATEGORIES = ROOT + "/categories/";
    static final String ENTRIES = ROOT + "/entries/";
    static final String HINT_LINKS = ROOT + "/hint_links.json";
    static final String ENTRY_PREFIX = "guide.";

    /** What the loader may ask about the game: only registries. */
    public interface Registries extends PageParser.Context {
    }

    /** The book and the lines for the log (one per skipped thing). */
    public record Result(GuideBook book, List<String> log) {
    }

    private GuideLoader() {
    }

    /** Loads every guide file of {@code files}; other paths (scenes, unknown) are ignored. */
    public static Result load(Map<ResourceLocation, IoSupplier<InputStream>> files, Registries reg) {
        List<String> log = new ArrayList<>();
        Map<ResourceLocation, JsonObject> cats = new LinkedHashMap<>();
        Map<ResourceLocation, JsonObject> entries = new LinkedHashMap<>();
        JsonObject links = null;
        ResourceLocation linksFile = null;
        long total = 0;
        boolean totalWarned = false;
        int candidates = 0;
        for (Map.Entry<ResourceLocation, IoSupplier<InputStream>> f : new TreeMap<>(files).entrySet()) {
            ResourceLocation rl = f.getKey();
            String path = rl.getPath();
            boolean isCat = path.startsWith(CATEGORIES) && path.endsWith(".json");
            boolean isEntry = path.startsWith(ENTRIES) && path.endsWith(".json");
            boolean isLinks = path.equals(HINT_LINKS);
            if (!rl.getNamespace().equals(NAMESPACE) || !(isCat || isEntry || isLinks)) {
                continue;
            }
            if (++candidates > GuideLimits.MAX_CANDIDATES) {
                log.add(rl + ": more than " + GuideLimits.MAX_CANDIDATES
                        + " guide files; this and later files not read");
                break;
            }
            byte[] bytes;
            try (InputStream in = f.getValue().get()) {
                bytes = readBounded(in, GuideLimits.MAX_FILE_BYTES);
            } catch (Exception | StackOverflowError e) {
                log.add(rl + ": cannot be read (" + e.getClass().getSimpleName() + ")");
                continue;
            }
            if (bytes == null) {
                log.add(rl + ": larger than " + GuideLimits.MAX_FILE_BYTES + " bytes, skipped");
                continue;
            }
            if (total + bytes.length > GuideLimits.MAX_TOTAL_BYTES) {
                if (!totalWarned) {
                    log.add(rl + ": guide files are over " + GuideLimits.MAX_TOTAL_BYTES
                            + " bytes together; this and later files skipped");
                    totalWarned = true;
                }
                continue;
            }
            total += bytes.length;
            JsonObject o;
            try {
                o = parseObject(bytes);
            } catch (Exception | StackOverflowError e) {
                log.add(rl + ": not a JSON object (" + message(e) + ")");
                continue;
            }
            if (isCat) {
                cats.put(rl, o);
            } else if (isEntry) {
                entries.put(rl, o);
            } else {
                links = o;
                linksFile = rl;
            }
        }

        List<GuideCategory> categories = new ArrayList<>();
        Set<String> catIds = new HashSet<>();
        for (Map.Entry<ResourceLocation, JsonObject> c : cats.entrySet()) {
            if (categories.size() >= GuideLimits.MAX_CATEGORIES) {
                log.add(c.getKey() + ": more than " + GuideLimits.MAX_CATEGORIES + " categories, skipped");
                continue;
            }
            try {
                GuideCategory cat = category(c.getValue(), reg);
                if (!catIds.add(cat.id())) {
                    log.add(c.getKey() + ": duplicate category " + cat.id() + ", skipped");
                    continue;
                }
                categories.add(cat);
            } catch (GuideFormatException e) {
                log.add(c.getKey() + ": " + e.getMessage());
            } catch (Exception | StackOverflowError e) {
                log.add(c.getKey() + ": " + message(e));
            }
        }

        List<GuideEntry> list = new ArrayList<>();
        Set<String> entryIds = new HashSet<>();
        for (Map.Entry<ResourceLocation, JsonObject> en : entries.entrySet()) {
            if (list.size() >= GuideLimits.MAX_ENTRIES) {
                log.add(en.getKey() + ": more than " + GuideLimits.MAX_ENTRIES + " entries, skipped");
                continue;
            }
            try {
                GuideEntry entry = entry(en.getKey(), en.getValue(), catIds, reg, log);
                if (!entryIds.add(entry.id())) {
                    log.add(en.getKey() + ": duplicate entry " + entry.id() + ", skipped");
                    continue;
                }
                list.add(entry);
            } catch (GuideFormatException e) {
                log.add(en.getKey() + ": " + e.getMessage());
            } catch (Exception | StackOverflowError e) {
                log.add(en.getKey() + ": " + message(e));
            }
        }

        Map<String, String> hintLinks = new LinkedHashMap<>();
        if (links != null) {
            hintLinks(linksFile, links, entryIds, hintLinks, log);
        }
        return new Result(new GuideBook(categories, list, hintLinks), capLog(log, "journal/"));
    }

    static GuideCategory category(JsonObject o, Registries reg) throws GuideFormatException {
        String id = GuideJson.string(o, "id", GuideLimits.ID);
        String group = GuideJson.optString(o, "group", GuideLimits.ID).orElse(GuideCategory.GROUP_GUIDE);
        if (!group.equals(GuideCategory.GROUP_GUIDE)) {
            throw new GuideFormatException("unknown group " + group);
        }
        GuideIcon icon = iconOf(o, reg);
        return new GuideCategory(id, icon, GuideJson.integer(o, "order", 0, -10_000, 10_000));
    }

    static GuideIcon iconOf(JsonObject o, Registries reg) throws GuideFormatException {
        Optional<String> s = GuideJson.optString(o, "icon", ICON);
        return s.isEmpty() ? GuideIcon.NONE : GuideIcon.parse(s.get(), reg);
    }

    static final java.util.regex.Pattern ICON = java.util.regex.Pattern.compile("gui:[a-z0-9_]{1,32}|"
            + GuideJson.LOCATION.pattern());

    static GuideEntry entry(ResourceLocation file, JsonObject o, Set<String> categories, Registries reg,
                            List<String> log) throws GuideFormatException {
        String id = GuideJson.string(o, "id", GuideLimits.ID);
        if (!id.startsWith(ENTRY_PREFIX) || id.length() == ENTRY_PREFIX.length()) {
            throw new GuideFormatException("entry id " + id + " does not start with " + ENTRY_PREFIX);
        }
        String category = GuideJson.string(o, "category", GuideLimits.ID);
        if (!categories.contains(category)) {
            throw new GuideFormatException("unknown category " + category);
        }
        GuideIcon icon = iconOf(o, reg);
        int order = GuideJson.integer(o, "order", 0, -10_000, 10_000);
        List<String> unlock = GuideJson.list(o, "unlock", GuideLimits.UNLOCK);
        List<ResourceLocation> items = new ArrayList<>();
        for (String s : GuideJson.list(o, "items", GuideJson.LOCATION)) {
            ResourceLocation rl = ResourceLocation.tryParse(s);
            if (rl == null || !reg.itemExists(rl)) {
                throw new GuideFormatException("unknown item " + s);
            }
            items.add(rl);
        }
        List<String> seeAlso = GuideJson.list(o, "see_also", GuideLimits.ID);
        JsonElement p = o.get("pages");
        if (!(p instanceof JsonArray arr)) {
            throw new GuideFormatException("\"pages\" is not a list");
        }
        if (arr.size() > GuideLimits.MAX_PAGES) {
            throw new GuideFormatException("more than " + GuideLimits.MAX_PAGES + " pages");
        }
        List<GuidePage> pages = new ArrayList<>();
        for (int i = 0; i < arr.size(); i++) {
            try {
                pages.add(page(arr.get(i), reg));
            } catch (GuideFormatException e) {
                log.add(file + ": page " + (i + 1) + ": " + e.getMessage() + ", skipped");
            } catch (Exception | StackOverflowError e) {
                log.add(file + ": page " + (i + 1) + ": " + message(e) + ", skipped");
            }
        }
        if (pages.isEmpty()) {
            throw new GuideFormatException("entry " + id + " has no usable page, skipped");
        }
        return new GuideEntry(id, category, icon, order, unlock, items, seeAlso, pages);
    }

    static GuidePage page(JsonElement e, Registries reg) throws GuideFormatException {
        if (!(e instanceof JsonObject o)) {
            throw new GuideFormatException("not an object");
        }
        String type = GuideJson.string(o, "type", PageParsers.TYPE);
        PageParser parser = PageParsers.get(type)
                .orElseThrow(() -> new GuideFormatException("unknown page type " + type));
        List<String> unlock = GuideJson.list(o, "unlock", GuideLimits.UNLOCK);
        PageData data = parser.parse(o, reg);
        return new GuidePage(type, unlock, data);
    }

    static void hintLinks(ResourceLocation file, JsonObject o, Set<String> entries, Map<String, String> out,
                          List<String> log) {
        if (o.size() > GuideLimits.MAX_HINT_LINKS) {
            log.add(file + ": more than " + GuideLimits.MAX_HINT_LINKS + " links, skipped");
            return;
        }
        for (Map.Entry<String, JsonElement> e : o.entrySet()) {
            String hint = e.getKey();
            if (hint.length() > GuideLimits.MAX_STRING || !GuideLimits.ID.matcher(hint).matches()
                    || !hint.startsWith("hint.")) {
                log.add(file + ": bad hint id, skipped");
                continue;
            }
            if (!(e.getValue() instanceof JsonPrimitive v) || !v.isString()
                    || !GuideLimits.ID.matcher(v.getAsString()).matches()) {
                log.add(file + ": " + hint + ": not an entry id, skipped");
                continue;
            }
            if (!entries.contains(v.getAsString())) {
                log.add(file + ": " + hint + ": unknown entry " + v.getAsString() + ", skipped");
                continue;
            }
            out.put(hint, v.getAsString());
        }
    }

    /**
     * At most {@link GuideLimits#MAX_LOG_LINES} lines, then one line saying
     * how many more there were (review M1).
     */
    public static List<String> capLog(List<String> log, String where) {
        if (log.size() <= GuideLimits.MAX_LOG_LINES + 1) {
            return List.copyOf(log);
        }
        List<String> out = new ArrayList<>(log.subList(0, GuideLimits.MAX_LOG_LINES));
        out.add((log.size() - GuideLimits.MAX_LOG_LINES) + " more problems in " + where + " suppressed");
        return List.copyOf(out);
    }

    /** The bytes of {@code in}, or {@code null} if there are more than {@code max}. */
    public static byte[] readBounded(InputStream in, int max) throws IOException {
        byte[] b = in.readNBytes(max + 1);
        return b.length > max ? null : b;
    }

    /** Strict JSON of one object, nested at most {@link GuideLimits#MAX_JSON_DEPTH}; a UTF-8 BOM is dropped. */
    public static JsonObject parseObject(byte[] bytes) throws IOException {
        String text = new String(bytes, StandardCharsets.UTF_8);
        if (!text.isEmpty() && text.charAt(0) == '﻿') {
            text = text.substring(1);
        }
        if (text.isBlank()) {
            throw new IOException("empty");
        }
        if (DefinitionLoader.jsonDepth(text, GuideLimits.MAX_JSON_DEPTH) > GuideLimits.MAX_JSON_DEPTH) {
            throw new IOException("nested deeper than " + GuideLimits.MAX_JSON_DEPTH);
        }
        JsonElement e = JsonParser.parseString(text);
        if (!(e instanceof JsonObject o)) {
            throw new IOException("not an object");
        }
        return o;
    }

    static String message(Throwable e) {
        String m = e.getMessage();
        if (m == null || m.isBlank()) {
            return e.getClass().getSimpleName();
        }
        // one short line: a message may quote the file
        m = m.replaceAll("[\\r\\n\\t]", " ");
        return m.length() > 160 ? m.substring(0, 160) + "…" : m;
    }
}
