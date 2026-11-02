package io.github.slugblue.wildroots.data.codec;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.MapLike;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;
import net.minecraft.resources.ResourceLocation;

/**
 * DFU codecs for definition JSON whose errors carry the JSON path
 * (docs/05-architecture.md §8.2 item 1; WR-125): a wrong type at
 * {@code quantitative.yield.loci} becomes
 * {@code $.quantitative.yield.loci: Not a number: "six"}.
 *
 * <p>An error message keeps its path between two control characters
 * ({@value #MARK_NAME} path {@value #SEP_NAME} message); every enclosing
 * object, list or map codec prefixes the paths inside it, so errors that DFU
 * joins with {@code "; "} stay separate. {@link #errors(String)} turns a
 * message back into (path, message) pairs. Characters of the markers in a
 * message from the input are replaced, so a data pack cannot forge a path.
 *
 * <p>Objects are read with {@link #object(Reader)}: every field is decoded
 * independently and all errors of an object are reported, not only the
 * first; unknown keys are ignored. These codecs decode only (definitions are
 * never written back).
 */
public final class PathCodecs {

    static final char MARK = '\u001E';
    static final char SEP = '\u001F';
    static final String MARK_NAME = "U+001E";
    static final String SEP_NAME = "U+001F";

    /** Longest string field (SEC-27). */
    public static final int MAX_STRING = 256;
    /** Longest id (SEC-28, {@code DataLimits.MAX_ID_LENGTH}). */
    public static final int MAX_ID = 128;

    /** One decoding error: JSON path ({@code ""} for the root) and message. */
    public record PathError(String path, String message) {

        /** {@code $.a.b[2]} form. */
        public String jsonPath() {
            return path.isEmpty() ? "$" : path.startsWith("[") ? "$" + path : "$." + path;
        }
    }

    private PathCodecs() {
    }

    // ------------------------------------------------------------------ messages

    static String clean(String s) {
        return s.replace(MARK, '?').replace(SEP, '?');
    }

    /** Message with a path. */
    public static String at(String path, String message) {
        return MARK + path + SEP + clean(message);
    }

    /** Prefixes every path in {@code msg} with {@code segment} ({@code name} or {@code [i]}). */
    public static String prefix(String segment, String msg) {
        if (msg.indexOf(MARK) < 0) {
            return at(segment, msg);
        }
        StringBuilder sb = new StringBuilder();
        for (PathError e : errors(msg)) {
            if (sb.length() > 0) {
                sb.append("; ");
            }
            sb.append(MARK).append(join(segment, e.path())).append(SEP).append(e.message());
        }
        return sb.toString();
    }

    private static String join(String segment, String path) {
        if (path.isEmpty()) {
            return segment;
        }
        return path.startsWith("[") ? segment + path : segment + "." + path;
    }

    /** Splits a decoding error message into its paths and messages. */
    public static List<PathError> errors(String msg) {
        List<PathError> out = new ArrayList<>();
        int i = msg.indexOf(MARK);
        if (i < 0) {
            out.add(new PathError("", msg.strip()));
            return out;
        }
        String head = msg.substring(0, i).strip();
        if (!head.isEmpty() && !head.equals(";")) {
            out.add(new PathError("", trimJoin(head)));
        }
        while (i >= 0) {
            int next = msg.indexOf(MARK, i + 1);
            String part = next < 0 ? msg.substring(i + 1) : msg.substring(i + 1, next);
            int sep = part.indexOf(SEP);
            String path = sep < 0 ? "" : part.substring(0, sep);
            String text = sep < 0 ? part : part.substring(sep + 1);
            out.add(new PathError(path, trimJoin(text)));
            i = next;
        }
        return out;
    }

    private static String trimJoin(String s) {
        String t = s.strip();
        while (t.endsWith(";")) {
            t = t.substring(0, t.length() - 1).strip();
        }
        return t;
    }

    // ------------------------------------------------------------------ primitives

    private static boolean isJsonPrimitive(Object input, Predicate<JsonPrimitive> kind) {
        return !(input instanceof JsonElement e) || (e.isJsonPrimitive() && kind.test(e.getAsJsonPrimitive()));
    }

    /** {@code base}, but JSON input must have the right primitive kind (DFU's JsonOps converts loosely). */
    static <A> Codec<A> strict(Codec<A> base, Predicate<JsonPrimitive> kind, String expected) {
        return new DecodeOnly<>() {
            @Override
            public <T> DataResult<Pair<A, T>> decode(DynamicOps<T> ops, T input) {
                if (!isJsonPrimitive(input, kind)) {
                    String shown = String.valueOf(input);
                    return DataResult.error(() -> at("", expected + " expected, got "
                            + (shown.length() > 64 ? shown.substring(0, 64) + "…" : shown)));
                }
                return base.decode(ops, input).mapError(m -> at("", m));
            }
        };
    }

    /** A JSON number (finiteness is checked by the validator, SEC-28). */
    public static final Codec<Double> NUM = strict(Codec.DOUBLE, JsonPrimitive::isNumber, "number");

    /** An integer: a number without a fraction inside the {@code int} range. */
    public static final Codec<Integer> INT = NUM.flatXmap(d -> {
        if (!Double.isFinite(d)) {
            return DataResult.error(() -> at("", "not a finite number: " + d));
        }
        if (d != Math.rint(d) || d < Integer.MIN_VALUE || d > Integer.MAX_VALUE) {
            return DataResult.error(() -> at("", "integer expected: " + d));
        }
        return DataResult.success((int) (double) d);
    }, i -> DataResult.success((double) i));

    public static final Codec<Boolean> BOOL = strict(Codec.BOOL, JsonPrimitive::isBoolean, "boolean");

    /** A string of at most {@value #MAX_STRING} characters (SEC-27). */
    public static final Codec<String> STR = string(MAX_STRING);

    /** A string of at most {@code max} characters (longer limits only where a field needs them). */
    public static Codec<String> string(int max) {
        return strict(Codec.STRING, JsonPrimitive::isString, "string")
                .flatXmap(s -> s.length() <= max ? DataResult.success(s)
                        : DataResult.error(() -> at("", "string of " + s.length() + " chars > " + max)),
                        DataResult::success);
    }

    /** A valid {@code namespace:path} id of at most {@value #MAX_ID} characters. */
    public static final Codec<ResourceLocation> ID = STR.flatXmap(s -> {
        ResourceLocation rl = s.length() <= MAX_ID ? ResourceLocation.tryParse(s) : null;
        return rl != null ? DataResult.success(rl) : DataResult.error(() -> at("", "invalid id " + clean(s)));
    }, rl -> DataResult.success(rl.toString()));

    /** One of the given lower-case words. */
    public static Codec<String> oneOf(String... words) {
        List<String> allowed = List.of(words);
        return STR.flatXmap(s -> allowed.contains(s) ? DataResult.success(s)
                : DataResult.error(() -> at("", "one of " + allowed + " expected, got " + clean(s))),
                DataResult::success);
    }

    // ------------------------------------------------------------------ containers

    /** List; element errors get {@code [i]}. */
    public static <A> Codec<List<A>> list(Codec<A> element) {
        return new DecodeOnly<>() {
            @Override
            public <T> DataResult<Pair<List<A>, T>> decode(DynamicOps<T> ops, T input) {
                DataResult<java.util.stream.Stream<T>> stream = ops.getStream(input);
                if (stream.error().isPresent()) {
                    return DataResult.error(() -> at("", "array expected"));
                }
                List<T> items = stream.result().orElseThrow().toList();
                List<A> out = new ArrayList<>(items.size());
                List<String> errs = new ArrayList<>();
                for (int i = 0; i < items.size(); i++) {
                    int idx = i;
                    DataResult<A> r = element.parse(ops, items.get(i));
                    r.error().ifPresent(e -> errs.add(prefix("[" + idx + "]", e.message())));
                    r.result().ifPresent(out::add);
                }
                if (!errs.isEmpty()) {
                    return DataResult.error(() -> String.join("; ", errs));
                }
                return DataResult.success(Pair.of(List.copyOf(out), input));
            }
        };
    }

    /** Object used as a map, in file order; value errors get the key. */
    public static <A> Codec<Map<String, A>> map(Codec<A> value) {
        return new DecodeOnly<>() {
            @Override
            public <T> DataResult<Pair<Map<String, A>, T>> decode(DynamicOps<T> ops, T input) {
                DataResult<MapLike<T>> map = ops.getMap(input);
                if (map.error().isPresent()) {
                    return DataResult.error(() -> at("", "object expected"));
                }
                Map<String, A> out = new LinkedHashMap<>();
                List<String> errs = new ArrayList<>();
                map.result().orElseThrow().entries().forEach(e -> {
                    String key = ops.getStringValue(e.getFirst()).result().orElse("?");
                    DataResult<A> r = value.parse(ops, e.getSecond());
                    r.error().ifPresent(err -> errs.add(prefix(clean(key), err.message())));
                    r.result().ifPresent(v -> out.put(key, v));
                });
                if (!errs.isEmpty()) {
                    return DataResult.error(() -> String.join("; ", errs));
                }
                return DataResult.success(Pair.of(java.util.Collections.unmodifiableMap(out), input));
            }
        };
    }

    // ------------------------------------------------------------------ objects

    /** Field access of one JSON object while it is decoded; collects the errors of every field. */
    public static final class Fields {
        private final DynamicOps<Object> ops;
        private final MapLike<Object> map;
        private final List<String> errors = new ArrayList<>();

        Fields(DynamicOps<Object> ops, MapLike<Object> map) {
            this.ops = ops;
            this.map = map;
        }

        private Object raw(String name) {
            Object v = map.get(name);
            return v == null || v.equals(ops.empty()) ? null : v;
        }

        /** Whether the field is present and not JSON {@code null}. */
        public boolean has(String name) {
            return raw(name) != null;
        }

        /** Required field; {@code null} if missing or broken (the error is recorded). */
        public <A> A req(String name, Codec<A> codec) {
            Object v = raw(name);
            if (v == null) {
                errors.add(at(name, "missing"));
                return null;
            }
            return decode(name, codec, v).orElse(null);
        }

        /** Optional field: empty if absent or JSON {@code null}; a broken value is an error. */
        public <A> Optional<A> opt(String name, Codec<A> codec) {
            Object v = raw(name);
            return v == null ? Optional.empty() : decode(name, codec, v);
        }

        /** Records an error at {@code path} relative to this object. */
        public void error(String path, String message) {
            errors.add(at(path, message));
        }

        private <A> Optional<A> decode(String name, Codec<A> codec, Object v) {
            DataResult<A> r = codec.parse(ops, v);
            r.error().ifPresent(e -> errors.add(prefix(name, e.message())));
            return r.result();
        }
    }

    /** Builds a value from the fields of an object. */
    @FunctionalInterface
    public interface Reader<R> {
        R read(Fields f);
    }

    /** Object codec; a result is produced only if no field had an error. */
    public static <R> Codec<R> object(Reader<R> reader) {
        return new DecodeOnly<>() {
            @Override
            @SuppressWarnings("unchecked")
            public <T> DataResult<Pair<R, T>> decode(DynamicOps<T> ops, T input) {
                DataResult<MapLike<T>> map = ops.getMap(input);
                if (map.error().isPresent()) {
                    return DataResult.error(() -> at("", "object expected"));
                }
                Fields f = new Fields((DynamicOps<Object>) ops, (MapLike<Object>) map.result().orElseThrow());
                R value = reader.read(f);
                if (!f.errors.isEmpty()) {
                    List<String> errs = List.copyOf(f.errors);
                    return DataResult.error(() -> String.join("; ", errs));
                }
                return DataResult.success(Pair.of(value, input));
            }
        };
    }

    /** Maps a decoded value, keeping the codec decode-only. */
    public static <A, B> Codec<B> map(Codec<A> codec, Function<A, B> f) {
        return codec.flatXmap(a -> DataResult.success(f.apply(a)),
                b -> DataResult.error(() -> "encoding definitions is not supported"));
    }

    /** Codec that only decodes. */
    abstract static class DecodeOnly<A> implements Codec<A> {
        @Override
        public <T> DataResult<T> encode(A input, DynamicOps<T> ops, T prefix) {
            return DataResult.error(() -> "encoding definitions is not supported");
        }
    }
}
