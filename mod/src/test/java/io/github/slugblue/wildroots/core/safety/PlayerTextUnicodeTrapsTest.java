package io.github.slugblue.wildroots.core.safety;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * WR-122, SEC-07 (TAE): Unicode traps beyond the vector table of
 * {@link PlayerTextTest}: every bidi control, invisible and zero-width
 * characters, tag characters, surrogate edge cases, and the length after NFC,
 * {@code §} removal and space collapsing, counted in code points.
 */
class PlayerTextUnicodeTrapsTest {

    static final int MAX = PlayerText.CULTIVAR_NAME_MAX;

    static String cp(int codePoint) {
        return new String(Character.toChars(codePoint));
    }

    static PlayerText.Result s(String in) {
        return PlayerText.sanitize(in, MAX);
    }

    /** Bidi controls: LRM, RLM, ALM, LRE…RLO, LRI…PDI. */
    @ParameterizedTest
    @ValueSource(ints = {0x200E, 0x200F, 0x061C, 0x202A, 0x202B, 0x202C, 0x202D, 0x202E, 0x2066, 0x2067, 0x2068, 0x2069})
    void everyBidiControlIsRefused(int c) {
        assertEquals(Optional.of(PlayerText.Reason.FORBIDDEN_CHARACTER), s("ab" + cp(c) + "cd").refusal(),
                () -> "U+" + Integer.toHexString(c));
    }

    /**
     * Zero-width and invisible: ZWSP, ZWNJ, ZWJ, word joiner, invisible
     * operators, BOM, Mongolian vowel separator, CGJ, variation selectors,
     * tag characters, interlinear annotation, braille blank, U+FFFC/FFFD,
     * noncharacters.
     */
    @ParameterizedTest
    @ValueSource(ints = {0x200B, 0x200C, 0x200D, 0x2060, 0x2061, 0x2062, 0x2063, 0x2064, 0xFEFF, 0x180E, 0x034F,
            0xFE00, 0xFE0F, 0xE0100, 0xE0001, 0xE0041, 0xE007F, 0xFFF9, 0xFFFB, 0x2800, 0xFFFC, 0xFFFD, 0xFFFE, 0xFFFF,
            0x1FFFE, 0x00AD})
    void invisibleCharactersAreRefused(int c) {
        assertFalse(s("ab" + cp(c) + "cd").accepted(), () -> "U+" + Integer.toHexString(c));
        assertFalse(s(cp(c)).accepted(), () -> "alone: U+" + Integer.toHexString(c));
    }

    /**
     * BUG: Hangul fillers (U+115F, U+1160, U+3164, U+FFA0) are category Lo,
     * so {@code PlayerText} accepts them as letters, yet they render as blank
     * space: a name made only of them is invisible (the EMPTY check does not
     * see it), and they can pad names to look identical to another player's.
     * Expected: refused like zero-width characters.
     */
    @Disabled("BUG: Hangul filler characters (Lo, rendered blank) pass PlayerText and give invisible names")
    @ParameterizedTest
    @ValueSource(ints = {0x115F, 0x1160, 0x3164, 0xFFA0})
    void hangulFillersAreRefused(int c) {
        assertFalse(s(cp(c)).accepted(), () -> "alone: U+" + Integer.toHexString(c));
        assertFalse(s("Golden" + cp(c)).accepted(), () -> "padding: U+" + Integer.toHexString(c));
    }

    @Test
    void surrogateEdgeCases() {
        String astral = cp(0x1D400);
        assertTrue(s(astral).accepted());
        assertFalse(s(astral.substring(1) + astral.substring(0, 1)).accepted(), "low before high");
        assertFalse(s("x\uD835").accepted(), "lone high at the end");
        assertFalse(s("\uDC00").accepted(), "lone low alone");
        assertFalse(s("\uD835𝐀").accepted(), "high, then a valid pair");
        // § takes the whole code point after it, never half a surrogate pair
        assertEquals(Optional.of("xy"), s("x§" + cp(0x1F955) + "y").text());
        assertEquals(Optional.of("x"), s("x§\uD835").text(), "§ eats the lone high surrogate");
        assertEquals(Optional.of("ab"), s("a§ b").text(), "§ eats the following space");
    }

    @Test
    void lengthIsCountedInCodePointsAfterNfc() {
        String decomposed = "é"; // 2 code points, NFC → 1
        assertEquals(Optional.of("é".repeat(MAX)), s(decomposed.repeat(MAX)).text(), "48 units → 24 after NFC");
        assertEquals(Optional.of(PlayerText.Reason.TOO_LONG), s(decomposed.repeat(MAX + 1)).refusal());
        String astral = cp(0x1D400);
        assertTrue(s("ж".repeat(MAX - 1) + astral).accepted(), "23 BMP + 1 astral = 24 code points, 25 units");
        assertEquals(Optional.of(PlayerText.Reason.TOO_LONG), s(astral.repeat(MAX) + "ж").refusal());
        // spaces and § do not count once removed
        assertTrue(s("  " + "a".repeat(MAX) + "   ").accepted());
        assertTrue(s("§a".repeat(40) + "a".repeat(MAX)).accepted());
        assertEquals(Optional.of(PlayerText.Reason.TOO_LONG), s("a".repeat(MAX) + " b").refusal(),
                "an inner space is a code point");
        assertEquals(Optional.of("a b"), s("a" + " ".repeat(200) + "b").text());
    }

    @Test
    void nfcThatExpandsToMarksIsRefused() {
        // U+FB2C is a composition exclusion: NFC gives SHIN + two combining marks (Mn)
        assertFalse(s(cp(0xFB2C)).accepted());
        // U+2126 OHM SIGN normalises to GREEK CAPITAL OMEGA, a letter
        assertEquals(Optional.of(cp(0x03A9)), s(cp(0x2126)).text());
    }

    @Test
    void noAcceptedResultContainsAForbiddenCategory() {
        // exhaustive over the BMP and the first astral plane: what comes out is letters, numbers and the allowed marks only
        IntStream.range(0, 0x20000).forEach(c -> {
            PlayerText.Result r = s("a" + cp(c) + "b");
            if (r.accepted()) {
                String t = r.text().orElseThrow();
                t.codePoints().forEach(o -> {
                    int type = Character.getType(o);
                    assertTrue(Character.isLetterOrDigit(o) || type == Character.LETTER_NUMBER
                                    || type == Character.OTHER_NUMBER || "-'’.,()& ".indexOf(o) >= 0,
                            () -> "U+" + Integer.toHexString(c) + " let U+" + Integer.toHexString(o) + " through");
                    assertFalse(type == Character.FORMAT || type == Character.CONTROL || type == Character.SURROGATE
                            || type == Character.PRIVATE_USE || type == Character.UNASSIGNED);
                });
                assertTrue(t.codePointCount(0, t.length()) <= MAX);
            }
        });
    }
}
