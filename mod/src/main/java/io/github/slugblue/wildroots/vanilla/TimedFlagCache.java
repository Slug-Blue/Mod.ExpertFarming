package io.github.slugblue.wildroots.vanilla;

import java.util.HashMap;
import java.util.Map;
import java.util.function.BooleanSupplier;

/**
 * A yes/no answer per key kept for {@code ttl} ticks (the POI query of a
 * chunk, 1200 ticks, docs/05-architecture.md §11.2). Expired entries are
 * dropped when the map grows past {@value #PRUNE_AT}. Server thread only.
 * Pure (JUnit).
 */
public final class TimedFlagCache {

    static final int PRUNE_AT = 4096;

    private record Entry(long until, boolean value) {
    }

    private final long ttl;
    private final Map<Long, Entry> map = new HashMap<>();

    public TimedFlagCache(long ttl) {
        this.ttl = ttl;
    }

    /** The cached answer for {@code key} at {@code now}, computed by {@code query} when missing or expired. */
    public boolean get(long key, long now, BooleanSupplier query) {
        Entry e = map.get(key);
        if (e != null && now < e.until() && now >= e.until() - ttl) {
            return e.value();
        }
        if (map.size() >= PRUNE_AT) {
            map.values().removeIf(x -> now >= x.until() || now < x.until() - ttl);
        }
        boolean v = query.getAsBoolean();
        map.put(key, new Entry(now + ttl, v));
        return v;
    }

    public void clear() {
        map.clear();
    }

    int size() {
        return map.size();
    }
}
