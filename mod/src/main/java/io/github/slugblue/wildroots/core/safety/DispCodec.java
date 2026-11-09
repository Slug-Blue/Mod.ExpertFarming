package io.github.slugblue.wildroots.core.safety;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;

/**
 * The {@code disp} block of lots and other items (SEC-07;
 * docs/05-architecture.md §6.1, §6.3): tooltip lines as a translation key
 * plus arguments, written by the server. Never serialised components.
 *
 * <p>Rules: a key matches {@code ^wildroots\.[a-z0-9_.]{1,96}$}; a line has
 * at most {@value #MAX_ARGS} arguments of at most {@value #MAX_ARG_LENGTH}
 * code points without control, format or {@code §} characters; at most
 * {@value #MAX_LINES} lines. A line that breaks a rule is dropped (on write
 * and on read), the others stay. Arguments are shown as
 * {@code Component.literal}, except a <em>key argument</em>: the marker
 * {@code U+0001} followed by a key without its {@code wildroots.} prefix (as
 * stored lines are), or by a vanilla {@code biome.*} key, which is shown
 * translated (a species name, a scale word; WR-180, WR-183). The marker is a
 * control character and a literal argument may hold no control character at
 * all, so a literal (a player's line name, whatever its NBT says) is never
 * read as a key (review 1, m1); a key argument is valid only as a whole.
 *
 * <p>Storage (compact, for the 512 B lot target): {@code disp} is a list of
 * strings, one per line: the key without the {@code wildroots.} prefix,
 * then each argument after {@value #SEPARATOR_NAME}. The separator is a
 * control character, which no valid key or argument contains, so a line
 * splits unambiguously.
 */
public final class DispCodec {

    public static final Pattern KEY = Pattern.compile("^wildroots\\.[a-z0-9_.]{1,96}$");
    public static final String KEY_PREFIX = "wildroots.";
    public static final int MAX_LINES = 24;
    public static final int MAX_ARGS = 4;
    public static final int MAX_ARG_LENGTH = 64;
    /** U+001F INFORMATION SEPARATOR ONE: between the key and each argument of a stored line. */
    public static final char SEPARATOR = '\u001F';
    static final String SEPARATOR_NAME = "U+001F";
    /** Marker of a key argument, U+0001: marker + {@code species.wild_carrot} is shown as {@code wildroots.species.wild_carrot}. */
    public static final String KEY_ARG = "\u0001";
    /** Vanilla keys a key argument may name besides the mod's own: biome names as {@code Util.makeDescriptionId} builds them. */
    public static final Pattern BIOME_KEY = Pattern.compile("^biome\\.[a-z0-9_.\\-]{1,96}$");

    /** One tooltip line. */
    public record Line(String key, List<String> args) {

        public Line {
            args = List.copyOf(args);
        }

        public static Line of(String key, String... args) {
            return new Line(key, List.of(args));
        }
    }

    private DispCodec() {
    }

    /** Whether {@code line} may be stored and shown. */
    public static boolean valid(Line line) {
        if (line == null || line.key() == null || !KEY.matcher(line.key()).matches()
                || line.args().size() > MAX_ARGS) {
            return false;
        }
        for (String a : line.args()) {
            if (!validArg(a)) {
                return false;
            }
        }
        return true;
    }

    private static boolean validArg(String a) {
        if (a == null || a.codePointCount(0, a.length()) > MAX_ARG_LENGTH) {
            return false;
        }
        if (a.startsWith(KEY_ARG)) {
            return argKey(a) != null;
        }
        return a.codePoints().noneMatch(cp -> switch (Character.getType(cp)) {
            case Character.CONTROL, Character.FORMAT, Character.SURROGATE, Character.PRIVATE_USE,
                    Character.UNASSIGNED, Character.LINE_SEPARATOR, Character.PARAGRAPH_SEPARATOR -> true;
            default -> cp == '§';
        });
    }

    /** Writes the valid lines, at most {@value #MAX_LINES}. */
    public static ListTag encode(List<Line> lines) {
        ListTag out = new ListTag();
        for (Line l : lines) {
            if (out.size() >= MAX_LINES) {
                break;
            }
            if (!valid(l)) {
                continue;
            }
            StringBuilder sb = new StringBuilder(l.key().substring(KEY_PREFIX.length()));
            for (String a : l.args()) {
                sb.append(SEPARATOR).append(a);
            }
            out.add(StringTag.valueOf(sb.toString()));
        }
        return out;
    }

    /** Reads lines from untrusted NBT: wrong types and invalid lines are skipped, never thrown. */
    public static List<Line> decode(Tag tag) {
        List<Line> out = new ArrayList<>();
        if (!(tag instanceof ListTag list) || (list.getElementType() != Tag.TAG_STRING && !list.isEmpty())) {
            return out;
        }
        for (int i = 0; i < list.size() && out.size() < MAX_LINES; i++) {
            decodeLine(list.getString(i)).ifPresent(out::add);
        }
        return out;
    }

    private static Optional<Line> decodeLine(String stored) {
        // limit + 1 parts: a sixth part means more than MAX_ARGS arguments
        String[] parts = stored.split(String.valueOf(SEPARATOR), MAX_ARGS + 2);
        if (parts.length > MAX_ARGS + 1) {
            return Optional.empty();
        }
        List<String> args = new ArrayList<>(parts.length - 1);
        for (int i = 1; i < parts.length; i++) {
            args.add(parts[i]);
        }
        Line l = new Line(KEY_PREFIX + parts[0], args);
        return valid(l) ? Optional.of(l) : Optional.empty();
    }

    /** A key argument for {@code key} (the server writes it; see the class comment). */
    public static String keyArg(String key) {
        return KEY_ARG + (key.startsWith(KEY_PREFIX) ? key.substring(KEY_PREFIX.length()) : key);
    }

    /** The translation key a key argument names, or {@code null} if {@code a} is a literal. */
    public static String argKey(String a) {
        if (a == null || !a.startsWith(KEY_ARG)) {
            return null;
        }
        String rest = a.substring(KEY_ARG.length());
        if (BIOME_KEY.matcher(rest).matches()) {
            return rest;
        }
        String key = KEY_PREFIX + rest;
        return KEY.matcher(key).matches() ? key : null;
    }

    /** Tooltip component: the key, arguments as literals or key arguments translated (never parsed as JSON). */
    public static Component toComponent(Line line) {
        Object[] args = line.args().stream().map(DispCodec::argComponent).toArray();
        return Component.translatable(line.key(), args);
    }

    /** One argument: a valid key argument translated, anything else literal. */
    public static Component argComponent(String a) {
        String key = argKey(a);
        return key != null ? Component.translatable(key) : Component.literal(a);
    }
}
