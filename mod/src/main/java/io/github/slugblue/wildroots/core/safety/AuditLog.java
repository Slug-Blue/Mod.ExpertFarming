package io.github.slugblue.wildroots.core.safety;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Audit log (SEC-47; docs/05-architecture.md §3 {@code core.safety}): logger
 * {@value #LOGGER_NAME} at INFO. Records named selections, sold finds, cap
 * refusals, rate-limit hits and inert objects. Player strings are escaped
 * (control, format, line and paragraph separator characters, the backslash
 * and the double quote become {@code \\uXXXX}) and cut to
 * {@value #MAX_STRING} code points, so a player cannot forge or break audit
 * lines. No personal data beyond UUID and player name.
 */
public final class AuditLog {

    public static final String LOGGER_NAME = "wildroots.audit";
    /** Maximum length of a player string in an audit line, code points (SEC-47). */
    public static final int MAX_STRING = 64;

    private static final Logger LOGGER = LoggerFactory.getLogger(LOGGER_NAME);

    private AuditLog() {
    }

    public static Logger logger() {
        return LOGGER;
    }

    /** Writes {@code event key=value …}; values are escaped. */
    public static void record(String event, Object... keyValues) {
        LOGGER.info(format(event, keyValues));
    }

    /** The audit line for {@code event} and alternating keys and values. */
    public static String format(String event, Object... keyValues) {
        StringBuilder sb = new StringBuilder(escape(event));
        for (int i = 0; i + 1 < keyValues.length; i += 2) {
            sb.append(' ').append(escape(String.valueOf(keyValues[i]))).append('=');
            Object v = keyValues[i + 1];
            if (v instanceof CharSequence) {
                sb.append('"').append(escape(v.toString())).append('"');
            } else {
                sb.append(escape(String.valueOf(v)));
            }
        }
        return sb.toString();
    }

    /**
     * Cuts {@code s} to {@value #MAX_STRING} code points and escapes
     * characters of the categories Cc, Cf, Cs, Co, Cn, Zl, Zp, the backslash
     * and the double quote as {@code \\uXXXX} (per UTF-16 unit).
     */
    public static String escape(String s) {
        if (s == null) {
            return "null";
        }
        StringBuilder sb = new StringBuilder();
        int count = 0;
        int i = 0;
        while (i < s.length() && count < MAX_STRING) {
            int cp = s.codePointAt(i);
            int len = Character.charCount(cp);
            if (unsafe(cp)) {
                for (int j = i; j < i + len; j++) {
                    sb.append(String.format("\\u%04X", (int) s.charAt(j)));
                }
            } else {
                sb.appendCodePoint(cp);
            }
            i += len;
            count++;
        }
        return sb.toString();
    }

    private static boolean unsafe(int cp) {
        if (cp == '\\' || cp == '"') {
            return true;
        }
        return switch (Character.getType(cp)) {
            case Character.CONTROL, Character.FORMAT, Character.SURROGATE, Character.PRIVATE_USE,
                    Character.UNASSIGNED, Character.LINE_SEPARATOR, Character.PARAGRAPH_SEPARATOR -> true;
            default -> false;
        };
    }
}
