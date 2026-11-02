package io.github.slugblue.wildroots.core.safety;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/** WR-121, SEC-47: audit logger name and escaping of player strings. */
class AuditLogTest {

    @Test
    void loggerIsWildrootsAudit() {
        assertEquals("wildroots.audit", AuditLog.logger().getName());
    }

    static Stream<Arguments> vectors() {
        return Stream.of(
                Arguments.of("a\nb", "a\\u000Ab"),
                Arguments.of("a\rb", "a\\u000Db"),
                Arguments.of("a\u001b[31mb", "a\\u001B[31mb"),
                Arguments.of("a‮b", "a\\u202Eb"),
                Arguments.of("a‍b", "a\\u200Db"),
                Arguments.of("a b", "a\\u2028b"),
                Arguments.of("ab", "a\\uE000b"),
                Arguments.of("a\"b", "a\\u0022b"),
                Arguments.of("a\\b", "a\\u005Cb"),
                Arguments.of("Морковь 1", "Морковь 1"));
    }

    @ParameterizedTest
    @MethodSource("vectors")
    void escapesUnsafeCharacters(String in, String expected) {
        assertEquals(expected, AuditLog.escape(in));
    }

    @Test
    void loneSurrogateIsEscaped() {
        assertEquals("x\\uD800y", AuditLog.escape("x\uD800y"));
    }

    @Test
    void cutToSixtyFourCodePoints() {
        String supplementary = new String(Character.toChars(0x1D400)).repeat(100); // two UTF-16 units each
        String out = AuditLog.escape(supplementary);
        assertEquals(64, out.codePointCount(0, out.length()));
        assertEquals(64, AuditLog.escape("a".repeat(500)).length());
    }

    @Test
    void lineCannotBeForged() {
        String line = AuditLog.format("name_selection", "player", "Steve", "name", "ok\nwildroots.audit FAKE");
        assertFalse(line.contains("\n"), line);
        assertEquals("name_selection player=\"Steve\" name=\"ok\\u000Awildroots.audit FAKE\"", line);
        assertEquals("rate_limit count=12", AuditLog.format("rate_limit", "count", 12));
    }
}
