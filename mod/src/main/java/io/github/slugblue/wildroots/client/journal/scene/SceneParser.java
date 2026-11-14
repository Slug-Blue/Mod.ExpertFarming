package io.github.slugblue.wildroots.client.journal.scene;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import io.github.slugblue.wildroots.client.journal.GuideFormatException;
import io.github.slugblue.wildroots.client.journal.GuideJson;
import io.github.slugblue.wildroots.client.journal.GuideLimits;
import io.github.slugblue.wildroots.client.journal.page.SpotlightPage;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * Reads one scene file (journal v2 §5.8) under every limit of
 * {@link SceneLimits}. A limit or format violation throws
 * {@link GuideFormatException} (the caller logs one line and skips the
 * scene); what the spec replaces instead of rejecting (an unknown block or
 * property, a block with a block entity, NBT in a state, a fluid) becomes
 * air with one line in {@code log}, and a {@code pop_item} or frame icon of
 * an unknown item is dropped with one line. Pure: the game is reached only
 * through the {@link StateResolver} and the item predicate.
 */
@OnlyIn(Dist.CLIENT)
public final class SceneParser {

    /** Shape of a display variant (§3.1), before the table check. */
    static final java.util.regex.Pattern VARIANT = java.util.regex.Pattern.compile("[a-z_]{1,32}=[a-z0-9_]{1,32}");

    private SceneParser() {
    }

    /** Mutable counters of one scene. */
    private static final class Budget {
        int actions;
        int setBlocks;
    }

    private static final class States<S> {
        final StateResolver<S> resolver;
        final List<S> table = new ArrayList<>();
        final Map<String, Integer> byString = new HashMap<>();

        States(StateResolver<S> resolver) {
            this.resolver = resolver;
            table.add(resolver.air());
        }

        int index(String state, String where, List<String> log) throws GuideFormatException {
            if (state.length() > SceneLimits.MAX_STATE_CHARS) {
                throw new GuideFormatException(where + ": block state longer than " + SceneLimits.MAX_STATE_CHARS);
            }
            Integer known = byString.get(state);
            if (known != null) {
                return known;
            }
            S s;
            if (!SceneLimits.STATE.matcher(state).matches()) {
                log.add(where + ": \"" + shorten(state) + "\" is not a plain block state (NBT or tags?), air instead");
                s = resolver.air();
            } else {
                s = resolver.resolve(state, where, log);
            }
            int i;
            if (s == null || s.equals(resolver.air())) {
                i = 0;
            } else {
                i = table.indexOf(s);
                if (i < 0) {
                    table.add(s);
                    i = table.size() - 1;
                }
            }
            byString.put(state, i);
            return i;
        }
    }

    /**
     * Parses every file of the folder (sorted by path); a file that fails
     * is skipped with one line; the first scene of an id wins.
     */
    public static <S> Map<String, SceneDef<S>> loadAll(Map<ResourceLocation, byte[]> files, StateResolver<S> resolver,
                                                       Predicate<ResourceLocation> items, List<String> log) {
        Map<String, SceneDef<S>> out = new java.util.LinkedHashMap<>();
        for (Map.Entry<ResourceLocation, byte[]> f : files.entrySet()) {
            try {
                JsonObject o = io.github.slugblue.wildroots.client.journal.GuideLoader.parseObject(f.getValue());
                SceneDef<S> def = parse(f.getKey(), o, resolver, items, log);
                if (out.containsKey(def.id())) {
                    log.add(f.getKey() + ": scene " + def.id() + " is defined twice, skipped");
                    continue;
                }
                out.put(def.id(), def);
            } catch (Exception | StackOverflowError e) {
                log.add(f.getKey() + ": " + message(e) + "; scene skipped");
            }
        }
        return java.util.Collections.unmodifiableMap(out);
    }

    /** One short line of an exception. */
    static String message(Throwable e) {
        String m = e.getMessage();
        if (m == null || m.isBlank()) {
            return e.getClass().getSimpleName();
        }
        m = m.replaceAll("[\r\n\t]", " ");
        return m.length() > 160 ? m.substring(0, 160) + "…" : m;
    }

    /**
     * @param file  the file, for log lines
     * @param items whether an item id is in the registry
     */
    public static <S> SceneDef<S> parse(ResourceLocation file, JsonObject o, StateResolver<S> resolver,
                                        Predicate<ResourceLocation> items, List<String> log)
            throws GuideFormatException {
        String id = GuideJson.string(o, "id", GuideLimits.ID);
        String where = file + " (" + id + ")";
        Optional<ResourceLocation> species = GuideJson.optLocation(o, "species");
        int[] size = size(o);
        int sx = size[0];
        int sy = size[1];
        int sz = size[2];
        States<S> states = new States<>(resolver);
        Map<Character, Integer> palette = palette(o, states, where, log);
        int[] cells = layers(o, palette, sx, sy, sz);
        SceneCamera camera = camera(o.get("camera"), SceneCamera.DEFAULT, "camera");
        SceneDef.FrameLayout layout = frameLayout(o);
        JsonArray stepsJson = array(o, "steps");
        if (stepsJson.isEmpty()) {
            throw new GuideFormatException("no steps");
        }
        if (stepsJson.size() > SceneLimits.MAX_STEPS) {
            throw new GuideFormatException("more than " + SceneLimits.MAX_STEPS + " steps");
        }
        Budget budget = new Budget();
        List<SceneStep> steps = new ArrayList<>();
        int total = 0;
        for (int i = 0; i < stepsJson.size(); i++) {
            if (!(stepsJson.get(i) instanceof JsonObject so)) {
                throw new GuideFormatException("step " + (i + 1) + " is not an object");
            }
            SceneStep step = step(so, i + 1, camera, sx, sy, sz, states, items, budget, where, log);
            total += step.duration();
            steps.add(step);
        }
        if (total > SceneLimits.MAX_TOTAL_TICKS) {
            throw new GuideFormatException("steps last " + total + " ticks, more than " + SceneLimits.MAX_TOTAL_TICKS);
        }
        return new SceneDef<>(id, species, sx, sy, sz, states.table, cells, camera, steps, layout);
    }

    // ------------------------------------------------------------------ structure

    static int[] size(JsonObject o) throws GuideFormatException {
        JsonArray a = array(o, "size");
        if (a.size() != 3) {
            throw new GuideFormatException("\"size\" is not [x, y, z]");
        }
        int[] s = new int[3];
        for (int i = 0; i < 3; i++) {
            s[i] = GuideJson.integer("size", a.get(i), 1, SceneLimits.MAX_AXIS);
        }
        if (s[0] * s[1] * s[2] > SceneLimits.MAX_VOLUME) {
            throw new GuideFormatException("structure has more than " + SceneLimits.MAX_VOLUME + " cells");
        }
        return s;
    }

    static <S> Map<Character, Integer> palette(JsonObject o, States<S> states, String where, List<String> log)
            throws GuideFormatException {
        if (!(o.get("palette") instanceof JsonObject p)) {
            throw new GuideFormatException("\"palette\" is not an object");
        }
        if (p.size() > SceneLimits.MAX_PALETTE) {
            throw new GuideFormatException("palette has more than " + SceneLimits.MAX_PALETTE + " entries");
        }
        Map<Character, Integer> out = new HashMap<>();
        for (Map.Entry<String, JsonElement> e : p.entrySet()) {
            String key = e.getKey();
            if (key.length() != 1 || Character.isWhitespace(key.charAt(0)) || Character.isSurrogate(key.charAt(0))) {
                throw new GuideFormatException("palette key \"" + shorten(key) + "\" is not one character");
            }
            if (!(e.getValue() instanceof JsonPrimitive v) || !v.isString()) {
                throw new GuideFormatException("palette \"" + key + "\" is not a string");
            }
            out.put(key.charAt(0), states.index(v.getAsString(), where + " palette \"" + key + "\"", log));
        }
        return out;
    }

    static int[] layers(JsonObject o, Map<Character, Integer> palette, int sx, int sy, int sz)
            throws GuideFormatException {
        JsonArray layers = array(o, "layers");
        if (layers.size() != sy) {
            throw new GuideFormatException("\"layers\" has " + layers.size() + " layers, size y is " + sy);
        }
        int[] cells = new int[sx * sy * sz];
        for (int y = 0; y < sy; y++) {
            if (!(layers.get(y) instanceof JsonArray rows) || rows.size() != sz) {
                throw new GuideFormatException("layer " + y + " is not " + sz + " rows");
            }
            for (int z = 0; z < sz; z++) {
                if (!(rows.get(z) instanceof JsonPrimitive r) || !r.isString() || r.getAsString().length() != sx) {
                    throw new GuideFormatException("layer " + y + " row " + z + " is not " + sx + " characters");
                }
                String row = r.getAsString();
                for (int x = 0; x < sx; x++) {
                    Integer s = palette.get(row.charAt(x));
                    if (s == null) {
                        throw new GuideFormatException("layer " + y + " row " + z + ": '" + row.charAt(x)
                                + "' is not in the palette");
                    }
                    cells[(y * sz + z) * sx + x] = s;
                }
            }
        }
        return cells;
    }

    static SceneCamera camera(JsonElement e, SceneCamera base, String name) throws GuideFormatException {
        if (e == null || e.isJsonNull()) {
            return base;
        }
        if (!(e instanceof JsonObject c)) {
            throw new GuideFormatException("\"" + name + "\" is not an object");
        }
        return new SceneCamera(number(c, "yaw", base.yaw()), number(c, "pitch", base.pitch()),
                number(c, "zoom", base.zoom()));
    }

    static SceneDef.FrameLayout frameLayout(JsonObject o) throws GuideFormatException {
        Optional<String> s = GuideJson.optString(o, "frame_layout", GuideLimits.ID);
        if (s.isEmpty() || s.get().equals("row")) {
            return SceneDef.FrameLayout.ROW;
        }
        if (s.get().equals("ring")) {
            return SceneDef.FrameLayout.RING;
        }
        throw new GuideFormatException("\"frame_layout\" is not row or ring");
    }

    // ------------------------------------------------------------------ steps

    static <S> SceneStep step(JsonObject o, int n, SceneCamera sceneCamera, int sx, int sy, int sz,
                              States<S> states, Predicate<ResourceLocation> items, Budget budget, String where,
                              List<String> log) throws GuideFormatException {
        String at = "step " + n;
        try {
            String caption = GuideJson.string(o, "caption", GuideLimits.LANG_KEY);
            int duration = GuideJson.integer(o, "duration", -1, SceneLimits.MIN_DURATION, SceneLimits.MAX_DURATION);
            if (duration < 0) {
                throw new GuideFormatException("missing \"duration\"");
            }
            List<String> unlock = GuideJson.list(o, "unlock", GuideLimits.UNLOCK);
            List<String> unless = GuideJson.list(o, "unless", GuideLimits.UNLOCK);
            Optional<SceneStep.Move> move = Optional.empty();
            if (o.get("camera") instanceof JsonObject c) {
                move = Optional.of(new SceneStep.Move(camera(c, sceneCamera, "camera"),
                        GuideJson.integer(c, "ease", 20, 0, SceneLimits.MAX_EASE)));
            } else if (o.has("camera") && !o.get("camera").isJsonNull()) {
                throw new GuideFormatException("\"camera\" is not an object");
            }
            JsonArray actionsJson = o.has("actions") ? array(o, "actions") : new JsonArray();
            if (actionsJson.size() > SceneLimits.MAX_ACTIONS_PER_STEP) {
                throw new GuideFormatException("more than " + SceneLimits.MAX_ACTIONS_PER_STEP + " actions");
            }
            budget.actions += actionsJson.size();
            if (budget.actions > SceneLimits.MAX_ACTIONS) {
                throw new GuideFormatException("scene has more than " + SceneLimits.MAX_ACTIONS + " actions");
            }
            List<SceneAction> actions = new ArrayList<>();
            for (int i = 0; i < actionsJson.size(); i++) {
                if (!(actionsJson.get(i) instanceof JsonObject a)) {
                    throw new GuideFormatException("action " + (i + 1) + " is not an object");
                }
                SceneAction act = action(a, i + 1, duration, sx, sy, sz, states, items, where + " " + at, log);
                if (act instanceof SceneAction.SetBlock && ++budget.setBlocks > SceneLimits.MAX_SET_BLOCKS) {
                    throw new GuideFormatException("scene has more than " + SceneLimits.MAX_SET_BLOCKS + " set_block");
                }
                if (act != null) {
                    actions.add(act);
                }
            }
            checkAtOnce(actions);
            List<SceneStep.FrameIcon> frame = frame(o, items, where + " " + at, log);
            return new SceneStep(caption, duration, unlock, unless, move, actions, frame);
        } catch (GuideFormatException e) {
            throw new GuideFormatException(at + ": " + e.getMessage());
        }
    }

    /** What is on screen at once (§5.8): pops, labels, highlights stay to the end of the step. */
    static void checkAtOnce(List<SceneAction> actions) throws GuideFormatException {
        int pops = 0;
        int labels = 0;
        int highlights = 0;
        List<int[]> bees = new ArrayList<>();
        List<int[]> icons = new ArrayList<>();
        for (SceneAction a : actions) {
            if (a instanceof SceneAction.PopItem) {
                pops++;
            } else if (a instanceof SceneAction.Label) {
                labels++;
            } else if (a instanceof SceneAction.Highlight) {
                highlights++;
            } else if (a instanceof SceneAction.Bee b) {
                bees.add(new int[] {b.at(), b.at() + b.ticks()});
            } else if (a instanceof SceneAction.Icon i) {
                icons.add(new int[] {i.at(), i.at() + i.ticks()});
            }
        }
        if (pops > SceneLimits.MAX_POPS) {
            throw new GuideFormatException("more than " + SceneLimits.MAX_POPS + " pop_item at once");
        }
        if (labels > SceneLimits.MAX_LABELS) {
            throw new GuideFormatException("more than " + SceneLimits.MAX_LABELS + " labels at once");
        }
        if (highlights > SceneLimits.MAX_HIGHLIGHTS) {
            throw new GuideFormatException("more than " + SceneLimits.MAX_HIGHLIGHTS + " highlights at once");
        }
        if (maxOverlap(bees) > SceneLimits.MAX_BEES) {
            throw new GuideFormatException("more than " + SceneLimits.MAX_BEES + " bees at once");
        }
        if (maxOverlap(icons) > SceneLimits.MAX_ICONS) {
            throw new GuideFormatException("more than " + SceneLimits.MAX_ICONS + " icons at once");
        }
    }

    /** Most half-open intervals {@code [from, to)} covering one tick. */
    static int maxOverlap(List<int[]> intervals) {
        int best = 0;
        for (int[] a : intervals) {
            int n = 0;
            for (int[] b : intervals) {
                if (b[0] <= a[0] && a[0] < b[1]) {
                    n++;
                }
            }
            best = Math.max(best, n);
        }
        return best;
    }

    static List<SceneStep.FrameIcon> frame(JsonObject o, Predicate<ResourceLocation> items, String where,
                                           List<String> log) throws GuideFormatException {
        if (!o.has("frame")) {
            return List.of();
        }
        JsonArray a = array(o, "frame");
        if (a.size() > SceneLimits.MAX_FRAME) {
            throw new GuideFormatException("frame has more than " + SceneLimits.MAX_FRAME + " icons");
        }
        List<SceneStep.FrameIcon> out = new ArrayList<>();
        for (JsonElement e : a) {
            if (!(e instanceof JsonObject f)) {
                throw new GuideFormatException("frame icon is not an object");
            }
            Optional<ResourceLocation> item = GuideJson.optLocation(f, "item");
            Optional<ResourceLocation> sprite = GuideJson.optLocation(f, "sprite");
            if (item.isPresent() == sprite.isPresent()) {
                throw new GuideFormatException("frame icon needs one of \"item\" or \"sprite\"");
            }
            if (item.isPresent()) {
                if (items.test(item.get())) {
                    out.add(new SceneStep.FrameItem(item.get()));
                } else {
                    log.add(where + ": frame item " + item.get() + " is unknown, icon dropped");
                }
            } else {
                out.add(new SceneStep.FrameSprite(sprite.get()));
            }
        }
        return out;
    }

    // ------------------------------------------------------------------ actions

    /** One action; {@code null} when it is dropped (an unknown item). */
    static <S> SceneAction action(JsonObject a, int n, int duration, int sx, int sy, int sz, States<S> states,
                                  Predicate<ResourceLocation> items, String where, List<String> log)
            throws GuideFormatException {
        try {
            if (a.has("nbt") || a.has("tag") || a.has("components")) {
                throw new GuideFormatException("NBT is not allowed");
            }
            String type = GuideJson.string(a, "type", GuideLimits.ID);
            int at = GuideJson.integer(a, "at", 0, 0, duration - 1);
            switch (type) {
                case "set_block": {
                    SceneAction.Cell pos = cell(a, "pos", sx, sy, sz);
                    String s = rawString(a, "state");
                    return new SceneAction.SetBlock(at, pos, states.index(s, where + " set_block", log));
                }
                case "pop_item": {
                    SceneAction.Cell pos = cell(a, "pos", sx, sy, sz);
                    ResourceLocation item = GuideJson.location(a, "item");
                    Optional<String> variant = GuideJson.optString(a, "variant", VARIANT);
                    if (variant.isPresent() && !SpotlightPage.VARIANTS.contains(variant.get())) {
                        throw new GuideFormatException("unknown variant " + variant.get());
                    }
                    if (!items.test(item)) {
                        log.add(where + ": pop_item " + item + " is unknown, action dropped");
                        return null;
                    }
                    return new SceneAction.PopItem(at, pos, item, variant);
                }
                case "icon": {
                    String icon = GuideJson.string(a, "icon", GuideLimits.ID);
                    SceneAction.IconKind kind = SceneAction.IconKind.byId(icon)
                            .orElseThrow(() -> new GuideFormatException("unknown icon " + icon));
                    return new SceneAction.Icon(at, kind, path(a, sx, sy, sz), ticks(a, at, duration));
                }
                case "bee":
                    return new SceneAction.Bee(at, path(a, sx, sy, sz), ticks(a, at, duration));
                case "label":
                    return new SceneAction.Label(at, point(a.get("pos"), "pos", sx, sy, sz),
                            GuideJson.string(a, "key", GuideLimits.LANG_KEY),
                            GuideJson.optString(a, "tooltip", GuideLimits.LANG_KEY));
                case "highlight": {
                    SceneAction.Cell from = cell(a, "from", sx, sy, sz);
                    SceneAction.Cell to = a.has("to") ? cell(a, "to", sx, sy, sz) : from;
                    return new SceneAction.Highlight(at,
                            new SceneAction.Cell(Math.min(from.x(), to.x()), Math.min(from.y(), to.y()),
                                    Math.min(from.z(), to.z())),
                            new SceneAction.Cell(Math.max(from.x(), to.x()), Math.max(from.y(), to.y()),
                                    Math.max(from.z(), to.z())));
                }
                default:
                    throw new GuideFormatException("unknown action type " + type);
            }
        } catch (GuideFormatException e) {
            throw new GuideFormatException("action " + n + ": " + e.getMessage());
        }
    }

    static int ticks(JsonObject a, int at, int duration) throws GuideFormatException {
        int t = GuideJson.integer(a, "ticks", -1, 1, Math.max(1, duration - at));
        if (t < 0) {
            throw new GuideFormatException("missing \"ticks\"");
        }
        return t;
    }

    /** A cell inside the structure. */
    static SceneAction.Cell cell(JsonObject o, String name, int sx, int sy, int sz) throws GuideFormatException {
        JsonElement e = o.get(name);
        if (!(e instanceof JsonArray a) || a.size() != 3) {
            throw new GuideFormatException("\"" + name + "\" is not [x, y, z]");
        }
        int x = GuideJson.integer(name, a.get(0), 0, sx - 1);
        int y = GuideJson.integer(name, a.get(1), 0, sy - 1);
        int z = GuideJson.integer(name, a.get(2), 0, sz - 1);
        return new SceneAction.Cell(x, y, z);
    }

    /** A point within the structure grown by one cell on every side. */
    static SceneAction.Point point(JsonElement e, String name, int sx, int sy, int sz) throws GuideFormatException {
        if (!(e instanceof JsonArray a) || a.size() != 3) {
            throw new GuideFormatException("\"" + name + "\" is not [x, y, z]");
        }
        return new SceneAction.Point(coord(a.get(0), name, sx), coord(a.get(1), name, sy), coord(a.get(2), name, sz));
    }

    private static double coord(JsonElement e, String name, int size) throws GuideFormatException {
        if (!(e instanceof JsonPrimitive p) || !p.isNumber()) {
            throw new GuideFormatException("\"" + name + "\" is not a number");
        }
        double d = p.getAsDouble();
        if (!Double.isFinite(d) || d < -1 || d > size) {
            throw new GuideFormatException("\"" + name + "\" is outside the structure");
        }
        return d;
    }

    static List<SceneAction.Point> path(JsonObject a, int sx, int sy, int sz) throws GuideFormatException {
        JsonArray p = array(a, "path");
        if (p.isEmpty() || p.size() > SceneLimits.MAX_PATH) {
            throw new GuideFormatException("\"path\" is not 1…" + SceneLimits.MAX_PATH + " points");
        }
        List<SceneAction.Point> out = new ArrayList<>();
        for (JsonElement e : p) {
            out.add(point(e, "path", sx, sy, sz));
        }
        return out;
    }

    // ------------------------------------------------------------------ fields

    static JsonArray array(JsonObject o, String name) throws GuideFormatException {
        if (!(o.get(name) instanceof JsonArray a)) {
            throw new GuideFormatException("\"" + name + "\" is not a list");
        }
        return a;
    }

    /** A float field; non-finite or absent gives the fallback (the camera clamps it). */
    static float number(JsonObject o, String name, float fallback) throws GuideFormatException {
        JsonElement e = o.get(name);
        if (e == null || e.isJsonNull()) {
            return fallback;
        }
        if (!(e instanceof JsonPrimitive p) || !p.isNumber()) {
            throw new GuideFormatException("\"" + name + "\" is not a number");
        }
        double d = p.getAsDouble();
        if (!Double.isFinite(d) || Math.abs(d) > 1e6) {
            throw new GuideFormatException("\"" + name + "\" is out of range");
        }
        return (float) d;
    }

    /** A string field before its shape is checked (the shape decides air, not rejection); length is a limit. */
    static String rawString(JsonObject o, String name) throws GuideFormatException {
        if (!(o.get(name) instanceof JsonPrimitive p) || !p.isString()) {
            throw new GuideFormatException("\"" + name + "\" is not a string");
        }
        String s = p.getAsString();
        if (s.length() > SceneLimits.MAX_STATE_CHARS) {
            throw new GuideFormatException("\"" + name + "\" is longer than " + SceneLimits.MAX_STATE_CHARS);
        }
        return s;
    }

    static String shorten(String s) {
        String t = s.replaceAll("[\\p{Cntrl}]", "?");
        return (t.length() > 40 ? t.substring(0, 40) + "…" : t).toLowerCase(Locale.ROOT);
    }
}
