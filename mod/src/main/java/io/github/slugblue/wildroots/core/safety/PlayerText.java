package io.github.slugblue.wildroots.core.safety;

import java.text.Normalizer;
import java.util.Optional;

/**
 * The one sanitiser of player text (SEC-07; docs/05-architecture.md §14
 * item 4): names of seedling varieties, {@code line_name} from the anvil
 * and every future input field.
 *
 * <ol>
 *   <li>NFC normalisation;</li>
 *   <li>{@code §} and the formatting code after it are removed;</li>
 *   <li>characters of the categories Cc, Cf (bidi, zero-width, BOM), Co,
 *       Cs, Cn, Zl, Zp reject the whole string;</li>
 *   <li>allowed are letters (L*), numbers (N*), the space U+0020 and
 *       {@code - ' ’ . , ( ) &}; anything else rejects the string;</li>
 *   <li>runs of spaces collapse to one, the ends are trimmed, the result is
 *       not empty and has at most {@code maxCodePoints} code points.</li>
 * </ol>
 *
 * <p>Lengths are code points. {@code FriendlyByteBuf#readUtf(max)} counts
 * UTF-16 code units (S14), so a packet field reads with
 * {@link #readUtfLimit(int)} = 2 × the code-point limit and this class
 * enforces the real limit after reading. Player strings are shown only as
 * {@code Component.literal}, never parsed as JSON components.
 */
public final class PlayerText {

    /** Name of a seedling variety, code points (SEC-06, SEC-07). */
    public static final int CULTIVAR_NAME_MAX = 24;
    /** {@code line_name} from the anvil, code points (vanilla anvil limit). */
    public static final int LINE_NAME_MAX = 50;

    private static final String EXTRA_ALLOWED = "-'’.,()&";

    /** Why a string was refused; the key goes to the player without echoing the string (SEC-06). */
    public enum Reason {
        EMPTY("empty"),
        TOO_LONG("too_long"),
        FORBIDDEN_CHARACTER("forbidden_character");

        private final String id;

        Reason(String id) {
            this.id = id;
        }

        public String translationKey() {
            return "wildroots.message.player_text." + id;
        }
    }

    /** Sanitised text or the reason of the refusal. */
    public record Result(Optional<String> text, Optional<Reason> refusal) {

        static Result ok(String s) {
            return new Result(Optional.of(s), Optional.empty());
        }

        static Result refuse(Reason r) {
            return new Result(Optional.empty(), Optional.of(r));
        }

        public boolean accepted() {
            return text.isPresent();
        }
    }

    private PlayerText() {
    }

    /** {@code readUtf} limit for a field of {@code maxCodePoints} code points: 2 UTF-16 units per code point. */
    public static int readUtfLimit(int maxCodePoints) {
        return 2 * maxCodePoints;
    }

    /** Sanitises {@code s}; see the class comment for the rules. */
    public static Result sanitize(String s, int maxCodePoints) {
        if (s == null) {
            return Result.refuse(Reason.EMPTY);
        }
        String n = Normalizer.normalize(s, Normalizer.Form.NFC);
        StringBuilder out = new StringBuilder(n.length());
        boolean pendingSpace = false;
        int count = 0;
        int i = 0;
        while (i < n.length()) {
            int cp = n.codePointAt(i);
            i += Character.charCount(cp);
            if (cp == '§') {
                if (i < n.length()) {
                    i += Character.charCount(n.codePointAt(i)); // the formatting code after §
                }
                continue;
            }
            if (forbiddenCategory(cp) || !allowed(cp)) {
                return Result.refuse(Reason.FORBIDDEN_CHARACTER);
            }
            if (cp == ' ') {
                pendingSpace = count > 0;
                continue;
            }
            if (pendingSpace) {
                out.append(' ');
                count++;
                pendingSpace = false;
            }
            out.appendCodePoint(cp);
            count++;
            if (count > maxCodePoints) {
                return Result.refuse(Reason.TOO_LONG);
            }
        }
        if (count == 0) {
            return Result.refuse(Reason.EMPTY);
        }
        return Result.ok(out.toString());
    }

    private static boolean forbiddenCategory(int cp) {
        return switch (Character.getType(cp)) {
            case Character.CONTROL, Character.FORMAT, Character.PRIVATE_USE, Character.SURROGATE,
                    Character.UNASSIGNED, Character.LINE_SEPARATOR, Character.PARAGRAPH_SEPARATOR -> true;
            default -> false;
        };
    }

    private static boolean allowed(int cp) {
        if (Character.isLetter(cp) || cp == ' ' || EXTRA_ALLOWED.indexOf(cp) >= 0) {
            return true;
        }
        return switch (Character.getType(cp)) {
            case Character.DECIMAL_DIGIT_NUMBER, Character.LETTER_NUMBER, Character.OTHER_NUMBER -> true;
            default -> false;
        };
    }
}
