package io.github.slugblue.wildroots.core.safety;

import com.mojang.logging.LogUtils;
import java.util.function.Consumer;
import java.util.function.LongSupplier;
import java.util.function.Supplier;
import org.slf4j.Logger;

/**
 * Exception isolation at entry points (SEC-34; docs/05-architecture.md §3
 * {@code core.safety}, §16): our {@code randomTick}, {@code onLoad},
 * {@code use}/{@code useOn}, event handlers and mixin handlers run their
 * logic through {@link #run}. An unexpected {@link RuntimeException} does
 * not reach the vanilla tick: the caller's {@code onFailure} makes the object
 * inert, and the log gets at most one line per key (position or type) in 5
 * minutes.
 *
 * <p>In dev and GameTest runs ({@code -Dwildroots.strictErrors=true}) the
 * exception is rethrown so that bugs do not hide. The flag is a JVM property,
 * never a config value (SEC-48). {@link Error}s are not caught.
 */
public final class Guarded {

    /** JVM property that turns on strict mode. */
    public static final String STRICT_PROPERTY = "wildroots.strictErrors";
    /** At most one log line per key in this window (SEC-34). */
    public static final long THROTTLE_MS = 5 * 60 * 1000L;

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Guarded DEFAULT = new Guarded(Boolean.getBoolean(STRICT_PROPERTY),
            System::currentTimeMillis, LOGGER::warn);

    private final boolean strict;
    private final Consumer<String> sink;
    private final LogThrottle throttle;

    /**
     * @param strict  rethrow instead of isolating
     * @param clockMs wall clock in milliseconds (replaceable in tests)
     * @param sink    receives the log lines
     */
    public Guarded(boolean strict, LongSupplier clockMs, Consumer<String> sink) {
        this.strict = strict;
        this.sink = sink;
        this.throttle = new LogThrottle(clockMs, THROTTLE_MS);
    }

    /** The shared instance: strict when {@value #STRICT_PROPERTY} is {@code true}. */
    public static Guarded shared() {
        return DEFAULT;
    }

    /** Runs {@code body}; returns {@code false} if it threw (see {@link #execute}). */
    public static boolean run(String where, Object key, Runnable body) {
        return DEFAULT.execute(where, key, body, null);
    }

    /** Runs {@code body}; on a failure runs {@code onFailure} (for example, marks the object inert). */
    public static boolean run(String where, Object key, Runnable body, Runnable onFailure) {
        return DEFAULT.execute(where, key, body, onFailure);
    }

    /** Computes {@code body}; on a failure returns {@code fallback}. */
    public static <T> T call(String where, Object key, Supplier<T> body, T fallback) {
        return DEFAULT.compute(where, key, body, fallback);
    }

    public boolean strict() {
        return strict;
    }

    /**
     * Runs {@code body} and isolates a {@link RuntimeException}: logs at most
     * one line per {@code (where, key)} in {@link #THROTTLE_MS}, runs
     * {@code onFailure} (itself isolated) and returns {@code false}. In strict
     * mode the exception is rethrown after {@code onFailure}.
     */
    public boolean execute(String where, Object key, Runnable body, Runnable onFailure) {
        try {
            body.run();
            return true;
        } catch (RuntimeException e) {
            fail(where, key, e, onFailure);
            return false;
        }
    }

    /** Value-returning variant of {@link #execute}. */
    public <T> T compute(String where, Object key, Supplier<T> body, T fallback) {
        try {
            return body.get();
        } catch (RuntimeException e) {
            fail(where, key, e, null);
            return fallback;
        }
    }

    private void fail(String where, Object key, RuntimeException e, Runnable onFailure) {
        if (onFailure != null) {
            try {
                onFailure.run();
            } catch (RuntimeException ignored) {
                // the failure handler must not escape either; the original exception is logged below
            }
        }
        if (throttle.allow(where + "@" + key)) {
            StackTraceElement[] st = e.getStackTrace();
            sink.accept("Wild Roots: " + where + " failed at " + key + ", object made inert: " + e
                    + (st.length > 0 ? " at " + st[0] : ""));
        }
        if (strict) {
            throw e;
        }
    }
}
