package io.github.slugblue.wildroots.genetics.testutil;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Test-only JSON reader: the engine has no JSON library (ADR-0001), and the
 * real loader is the Codec layer of {@code :mod} (WR-125). Values: {@code Map}
 * (insertion order), {@code List}, {@code String}, {@code Double},
 * {@code Boolean}, {@code null}. Like Gson in lenient mode it accepts
 * {@code NaN}, {@code Infinity} and {@code -Infinity}, so validation can be
 * tested against them (SEC-28).
 */
public final class MiniJson {

    private final String s;
    private int i;

    private MiniJson(String s) {
        this.s = s;
    }

    public static Object parse(String text) {
        MiniJson p = new MiniJson(text);
        p.ws();
        Object v = p.value();
        p.ws();
        if (p.i != p.s.length()) {
            throw p.err("trailing data");
        }
        return v;
    }

    public static Object resource(String path) {
        try (InputStream in = MiniJson.class.getResourceAsStream(path)) {
            if (in == null) {
                throw new IllegalArgumentException("no resource " + path);
            }
            return parse(new String(in.readAllBytes(), StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    public static String resourceText(String path) {
        try (InputStream in = MiniJson.class.getResourceAsStream(path)) {
            if (in == null) {
                throw new IllegalArgumentException("no resource " + path);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private IllegalArgumentException err(String m) {
        return new IllegalArgumentException(m + " at " + i);
    }

    private void ws() {
        while (i < s.length() && Character.isWhitespace(s.charAt(i))) {
            i++;
        }
    }

    private boolean take(String word) {
        if (s.startsWith(word, i)) {
            i += word.length();
            return true;
        }
        return false;
    }

    private Object value() {
        if (i >= s.length()) {
            throw err("eof");
        }
        char c = s.charAt(i);
        if (c == '{') {
            return object();
        }
        if (c == '[') {
            return array();
        }
        if (c == '"') {
            return string();
        }
        if (take("true")) {
            return Boolean.TRUE;
        }
        if (take("false")) {
            return Boolean.FALSE;
        }
        if (take("null")) {
            return null;
        }
        if (take("NaN")) {
            return Double.NaN;
        }
        if (take("Infinity")) {
            return Double.POSITIVE_INFINITY;
        }
        if (take("-Infinity")) {
            return Double.NEGATIVE_INFINITY;
        }
        return number();
    }

    private Map<String, Object> object() {
        Map<String, Object> m = new LinkedHashMap<>();
        i++;
        ws();
        if (take("}")) {
            return m;
        }
        while (true) {
            ws();
            String k = string();
            ws();
            if (!take(":")) {
                throw err("':' expected");
            }
            ws();
            m.put(k, value());
            ws();
            if (take(",")) {
                continue;
            }
            if (take("}")) {
                return m;
            }
            throw err("',' or '}' expected");
        }
    }

    private List<Object> array() {
        List<Object> l = new ArrayList<>();
        i++;
        ws();
        if (take("]")) {
            return l;
        }
        while (true) {
            ws();
            l.add(value());
            ws();
            if (take(",")) {
                continue;
            }
            if (take("]")) {
                return l;
            }
            throw err("',' or ']' expected");
        }
    }

    private String string() {
        if (s.charAt(i) != '"') {
            throw err("string expected");
        }
        i++;
        StringBuilder b = new StringBuilder();
        while (true) {
            char c = s.charAt(i++);
            if (c == '"') {
                return b.toString();
            }
            if (c == '\\') {
                char e = s.charAt(i++);
                switch (e) {
                    case 'n' -> b.append('\n');
                    case 't' -> b.append('\t');
                    case 'u' -> {
                        b.append((char) Integer.parseInt(s.substring(i, i + 4), 16));
                        i += 4;
                    }
                    default -> b.append(e);
                }
            } else {
                b.append(c);
            }
        }
    }

    private Double number() {
        int start = i;
        while (i < s.length() && "+-0123456789.eE".indexOf(s.charAt(i)) >= 0) {
            i++;
        }
        if (start == i) {
            throw err("value expected");
        }
        return Double.parseDouble(s.substring(start, i));
    }
}
