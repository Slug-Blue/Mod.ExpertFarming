package io.github.slugblue.wildroots.core.safety;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;

/**
 * At most one log line per key in a time window (SEC-34, SEC-47): shared by
 * {@link Guarded} and the packet guard. The key map is bounded; when it is
 * full it is cleared (a few repeated lines, never unbounded memory).
 */
public final class LogThrottle {

    static final int MAX_KEYS = 4096;

    private final LongSupplier clockMs;
    private final long intervalMs;
    private final Map<String, Long> last = new ConcurrentHashMap<>();

    public LogThrottle(LongSupplier clockMs, long intervalMs) {
        this.clockMs = clockMs;
        this.intervalMs = intervalMs;
    }

    /** Whether a line for {@code key} may be written now; if so, the key's window starts. */
    public boolean allow(String key) {
        long now = clockMs.getAsLong();
        Long prev = last.get(key);
        if (prev != null && now - prev < intervalMs) {
            return false;
        }
        if (last.size() >= MAX_KEYS) {
            last.clear();
        }
        last.put(key, now);
        return true;
    }
}
