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
 * {@code Component.literal}.
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

    /** Tooltip component: the key, arguments as literals (never parsed as JSON). */
    public static Component toComponent(Line line) {
        Object[] args = line.args().stream().map(Component::literal).toArray();
        return Component.translatable(line.key(), args);
    }
}
