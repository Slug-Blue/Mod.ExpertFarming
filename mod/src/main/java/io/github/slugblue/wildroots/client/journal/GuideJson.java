package io.github.slugblue.wildroots.client.journal;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * Bounded readers of guide JSON fields (journal v2 §5.2): every string is
 * at most {@link GuideLimits#MAX_STRING} characters and matches its
 * pattern, every list at most {@link GuideLimits#MAX_LIST} items. A wrong
 * field throws {@link GuideFormatException} with the field name.
 */
@OnlyIn(Dist.CLIENT)
public final class GuideJson {

    private GuideJson() {
    }

    public static Optional<String> optString(JsonObject o, String name, Pattern pattern) throws GuideFormatException {
        JsonElement e = o.get(name);
        if (e == null || e.isJsonNull()) {
            return Optional.empty();
        }
        return Optional.of(checked(name, e, pattern));
    }

    public static String string(JsonObject o, String name, Pattern pattern) throws GuideFormatException {
        return optString(o, name, pattern).orElseThrow(() -> new GuideFormatException("missing \"" + name + "\""));
    }

    private static String checked(String name, JsonElement e, Pattern pattern) throws GuideFormatException {
        if (!(e instanceof JsonPrimitive p) || !p.isString()) {
            throw new GuideFormatException("\"" + name + "\" is not a string");
        }
        String s = p.getAsString();
        if (s.length() > GuideLimits.MAX_STRING) {
            throw new GuideFormatException("\"" + name + "\" is longer than " + GuideLimits.MAX_STRING);
        }
        if (!pattern.matcher(s).matches()) {
            throw new GuideFormatException("\"" + name + "\" has a bad value");
        }
        return s;
    }

    /** A list of strings (absent = empty), duplicates dropped, at most {@link GuideLimits#MAX_LIST}. */
    public static List<String> list(JsonObject o, String name, Pattern pattern) throws GuideFormatException {
        JsonElement e = o.get(name);
        if (e == null || e.isJsonNull()) {
            return List.of();
        }
        if (!(e instanceof JsonArray a)) {
            throw new GuideFormatException("\"" + name + "\" is not a list");
        }
        if (a.size() > GuideLimits.MAX_LIST) {
            throw new GuideFormatException("\"" + name + "\" has more than " + GuideLimits.MAX_LIST + " items");
        }
        LinkedHashSet<String> out = new LinkedHashSet<>();
        for (JsonElement x : a) {
            out.add(checked(name, x, pattern));
        }
        return List.copyOf(out);
    }

    /** A namespaced id ({@code wildroots:hand_lens}); a bare path gets {@code minecraft:} as usual. */
    public static Optional<ResourceLocation> optLocation(JsonObject o, String name) throws GuideFormatException {
        Optional<String> s = optString(o, name, LOCATION);
        if (s.isEmpty()) {
            return Optional.empty();
        }
        ResourceLocation rl = ResourceLocation.tryParse(s.get());
        if (rl == null) {
            throw new GuideFormatException("\"" + name + "\" is not an id");
        }
        return Optional.of(rl);
    }

    public static ResourceLocation location(JsonObject o, String name) throws GuideFormatException {
        return optLocation(o, name).orElseThrow(() -> new GuideFormatException("missing \"" + name + "\""));
    }

    /** Shape of a namespaced id before {@link ResourceLocation#tryParse} checks it. */
    public static final Pattern LOCATION = Pattern.compile("([a-z0-9_.-]{1,64}:)?[a-z0-9_./-]{1,100}");

    public static boolean bool(JsonObject o, String name, boolean fallback) throws GuideFormatException {
        JsonElement e = o.get(name);
        if (e == null || e.isJsonNull()) {
            return fallback;
        }
        if (!(e instanceof JsonPrimitive p) || !p.isBoolean()) {
            throw new GuideFormatException("\"" + name + "\" is not true or false");
        }
        return p.getAsBoolean();
    }

    /** An integer in {@code [min, max]}. */
    public static int integer(JsonObject o, String name, int fallback, int min, int max) throws GuideFormatException {
        JsonElement e = o.get(name);
        if (e == null || e.isJsonNull()) {
            return fallback;
        }
        return integer(name, e, min, max);
    }

    public static int integer(String name, JsonElement e, int min, int max) throws GuideFormatException {
        if (!(e instanceof JsonPrimitive p) || !p.isNumber()) {
            throw new GuideFormatException("\"" + name + "\" is not a number");
        }
        double d = p.getAsDouble();
        if (d != Math.rint(d) || d < min || d > max) {
            throw new GuideFormatException("\"" + name + "\" is not a whole number in " + min + "…" + max);
        }
        return (int) d;
    }

    /** A list of integers in {@code [min, max]}, at most {@link GuideLimits#MAX_LIST}. */
    public static List<Integer> intList(JsonObject o, String name, int min, int max) throws GuideFormatException {
        JsonElement e = o.get(name);
        if (e == null || e.isJsonNull()) {
            return List.of();
        }
        if (!(e instanceof JsonArray a)) {
            throw new GuideFormatException("\"" + name + "\" is not a list");
        }
        if (a.size() > GuideLimits.MAX_LIST) {
            throw new GuideFormatException("\"" + name + "\" has more than " + GuideLimits.MAX_LIST + " items");
        }
        List<Integer> out = new ArrayList<>();
        for (JsonElement x : a) {
            out.add(integer(name, x, min, max));
        }
        return List.copyOf(out);
    }
}
