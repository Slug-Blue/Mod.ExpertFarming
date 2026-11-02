package io.github.slugblue.wildroots.core.safety;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.netty.buffer.Unpooled;
import io.netty.handler.codec.DecoderException;
import java.util.Optional;
import java.util.stream.Stream;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/** WR-122, SEC-07: table of sanitiser vectors and the readUtf / code-point boundary (S14). */
class PlayerTextTest {

    static final PlayerText.Reason EMPTY = PlayerText.Reason.EMPTY;
    static final PlayerText.Reason LONG = PlayerText.Reason.TOO_LONG;
    static final PlayerText.Reason BAD = PlayerText.Reason.FORBIDDEN_CHARACTER;

    static String cp(int codePoint) {
        return new String(Character.toChars(codePoint));
    }

    static Arguments ok(String in, String out) {
        return Arguments.of(in, out, null);
    }

    static Arguments no(String in, PlayerText.Reason r) {
        return Arguments.of(in, null, r);
    }

    static Stream<Arguments> vectors() {
        String mathA = cp(0x1D400); // MATHEMATICAL BOLD CAPITAL A, a letter outside the BMP
        return Stream.of(
                ok("Golden Line", "Golden Line"),
                ok("Морковь Вавилова", "Морковь Вавилова"),
                ok("Ранняя 2", "Ранняя 2"),
                ok("  spaced   out  ", "spaced out"),
                ok("O'Brien's", "O'Brien's"),
                ok("Dad’s Pride", "Dad’s Pride"),
                ok("Smith & Co-op (II), Jr's", "Smith & Co-op (II), Jr's"),
                ok("§kMagic", "Magic"),
                ok("Red§cBold", "RedBold"),
                ok("tail§", "tail"),
                ok("é", "é"), // NFC composes e + combining acute
                ok("арре", "арре"), // Cyrillic homoglyphs of "appe": letters
                ok("١٢٣", "١٢٣"), // Arabic-Indic digits (Nd)
                ok("Ⅷ Line", "Ⅷ Line"), // ROMAN NUMERAL EIGHT (Nl)
                ok("½ Acre", "½ Acre"), // VULGAR FRACTION ONE HALF (No)
                ok("田園", "田園"),
                ok(mathA.repeat(24), mathA.repeat(24)),
                ok("a".repeat(24), "a".repeat(24)),
                no("a".repeat(25), LONG),
                no(mathA.repeat(25), LONG),
                no("", EMPTY),
                no("    ", EMPTY),
                no("§k", EMPTY),
                no(null, EMPTY),
                no("new\nline", BAD),
                no("tab\there", BAD),
                no("cr\rhere", BAD),
                no("nul\u0000", BAD),
                no("esc\u001b[0m", BAD),
                no("del\u007F", BAD),
                no("rtl‮evil", BAD),
                no("lri⁦x", BAD),
                no("zw​space", BAD),
                no("zw‌nj", BAD),
                no("zw‍j", BAD),
                no("bom﻿", BAD),
                no("soft­hyphen", BAD),
                no("private", BAD),
                no("private" + cp(0xF0000), BAD),
                no("lone\uD800", BAD),
                no("lone\uDC00x", BAD),
                no("ls x", BAD),
                no("ps x", BAD),
                no("unassigned͸", BAD),
                no("emoji " + cp(0x1F955), BAD), // carrot emoji, So
                no("heart♥", BAD),
                no("nbsp x", BAD),
                no("ideographic　space", BAD),
                no("quote\"x", BAD),
                no("{\"text\":\"x\"}", BAD),
                no("<b>x</b>", BAD),
                no("50%", BAD),
                no("a+b", BAD),
                no("path/x", BAD),
                no("back\\slash", BAD),
                no("1́", BAD)); // a combining mark that NFC cannot compose stays Mn
    }

    @ParameterizedTest
    @MethodSource("vectors")
    void sanitizeCultivarName(String in, String expected, PlayerText.Reason reason) {
        PlayerText.Result r = PlayerText.sanitize(in, PlayerText.CULTIVAR_NAME_MAX);
        assertEquals(Optional.ofNullable(expected), r.text(), "input " + in);
        assertEquals(Optional.ofNullable(reason), r.refusal(), "input " + in);
    }

    @Test
    void atLeastFortyVectors() {
        assertTrue(vectors().count() >= 40);
    }

    @Test
    void lineNameAllowsFiftyCodePoints() {
        assertTrue(PlayerText.sanitize("l".repeat(50), PlayerText.LINE_NAME_MAX).accepted());
        assertFalse(PlayerText.sanitize("l".repeat(51), PlayerText.LINE_NAME_MAX).accepted());
    }

    @Test
    void reasonsHaveTranslationKeys() {
        assertEquals("wildroots.message.player_text.too_long", LONG.translationKey());
    }

    /** S14: readUtf counts UTF-16 units, so 24 letters outside the BMP pass readUtf(48) and PlayerText; 25 do not. */
    @Test
    void readUtfLimitAndCodePointsAgree() {
        String mathA = cp(0x1D400);
        assertEquals(48, PlayerText.readUtfLimit(PlayerText.CULTIVAR_NAME_MAX));
        String ok = mathA.repeat(24);
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        buf.writeUtf(ok);
        String read = buf.readUtf(PlayerText.readUtfLimit(PlayerText.CULTIVAR_NAME_MAX));
        assertTrue(PlayerText.sanitize(read, PlayerText.CULTIVAR_NAME_MAX).accepted());

        FriendlyByteBuf over = new FriendlyByteBuf(Unpooled.buffer());
        over.writeUtf(mathA.repeat(25));
        assertThrows(DecoderException.class, () -> over.readUtf(PlayerText.readUtfLimit(PlayerText.CULTIVAR_NAME_MAX)));

        // 25 BMP letters pass readUtf(48) but not the code-point limit
        FriendlyByteBuf bmp = new FriendlyByteBuf(Unpooled.buffer());
        bmp.writeUtf("ж".repeat(25));
        String read25 = bmp.readUtf(PlayerText.readUtfLimit(PlayerText.CULTIVAR_NAME_MAX));
        assertEquals(Optional.of(LONG), PlayerText.sanitize(read25, PlayerText.CULTIVAR_NAME_MAX).refusal());
    }
}
