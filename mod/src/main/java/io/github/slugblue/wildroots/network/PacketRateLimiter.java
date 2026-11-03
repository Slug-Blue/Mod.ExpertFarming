package io.github.slugblue.wildroots.network;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.IntSupplier;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

/**
 * Rate limit of C→S packets (SEC-02; docs/05-architecture.md §14 item 7).
 *
 * <ul>
 *   <li>Token bucket per (player, packet type): capacity 5, refill 5 per
 *       second (SERVER config); a type may add a slower second bucket
 *       ({@code NameSelection}: 3 per minute).</li>
 *   <li>Every dropped packet (over the limit or malformed) counts for the
 *       flood check: {@code ≥ threshold} drops within 10 s disconnect the
 *       player ({@code 0} turns it off).</li>
 *   <li>At most one log line per player in 60 s.</li>
 * </ul>
 * Lives in memory and forgets a player when he leaves. The clock is
 * replaceable (nanoseconds), so the JUnit test controls time.
 */
public final class PacketRateLimiter {

    /** Flood window (SEC-02). */
    public static final long FLOOD_WINDOW_NANOS = 10_000_000_000L;
    /** One log line per player per minute (SEC-02). */
    public static final long LOG_INTERVAL_NANOS = 60_000_000_000L;
    private static final double NANOS_PER_SECOND = 1e9;
    /** Upper bound of remembered drops per player when the flood check is off. */
    static final int MAX_TRACKED_DROPS = 10_000;

    /** Outcome for one packet. */
    public record Decision(boolean allowed, boolean disconnect, boolean log) {

        static final Decision ALLOW = new Decision(true, false, false);
    }

    /** A slower extra bucket of one packet type. */
    public record ExtraLimit(int capacity, double perSecond) {

        public static ExtraLimit perMinute(int n) {
            return new ExtraLimit(n, n / 60.0);
        }
    }

    private static final class Bucket {
        double tokens;
        long last;

        Bucket(double tokens, long now) {
            this.tokens = tokens;
            this.last = now;
        }

        boolean take(double capacity, double perSecond, long now) {
            tokens = Math.min(capacity, tokens + (now - last) / NANOS_PER_SECOND * perSecond);
            last = now;
            if (tokens >= 1) {
                tokens -= 1;
                return true;
            }
            return false;
        }
    }

    private static final class PlayerState {
        final Map<String, Bucket> buckets = new HashMap<>();
        final ArrayDeque<Long> drops = new ArrayDeque<>();
        long lastLog = Long.MIN_VALUE;
        boolean disconnected;
    }

    private final LongSupplier clock;
    private final IntSupplier capacity;
    private final IntSupplier perSecond;
    private final IntSupplier floodThreshold;
    private final Map<String, Supplier<ExtraLimit>> extra;
    private final Map<UUID, PlayerState> players = new HashMap<>();

    /**
     * @param clock          nanoseconds ({@code System::nanoTime} on the server)
     * @param capacity       bucket capacity
     * @param perSecond      refill per second
     * @param floodThreshold drops in 10 s that disconnect; 0 = never
     * @param extra          extra buckets by packet type, read at each packet (SERVER config)
     */
    public PacketRateLimiter(LongSupplier clock, IntSupplier capacity, IntSupplier perSecond, IntSupplier floodThreshold,
                             Map<String, Supplier<ExtraLimit>> extra) {
        this.clock = clock;
        this.capacity = capacity;
        this.perSecond = perSecond;
        this.floodThreshold = floodThreshold;
        this.extra = Map.copyOf(extra);
    }

    /** Takes a token for a packet of {@code type} from {@code player}. */
    public synchronized Decision tryAcquire(UUID player, String type) {
        long now = clock.getAsLong();
        PlayerState s = players.computeIfAbsent(player, k -> new PlayerState());
        int cap = Math.max(1, capacity.getAsInt());
        Bucket main = s.buckets.computeIfAbsent(type, k -> new Bucket(cap, now));
        boolean ok = main.take(cap, Math.max(1, perSecond.getAsInt()), now);
        Supplier<ExtraLimit> limitSource = extra.get(type);
        ExtraLimit limit = limitSource == null ? null : limitSource.get();
        if (ok && limit != null) {
            Bucket slow = s.buckets.computeIfAbsent(type + "#slow", k -> new Bucket(limit.capacity(), now));
            ok = slow.take(limit.capacity(), limit.perSecond(), now);
        }
        return ok ? Decision.ALLOW : drop(s, now);
    }

    /** Records a packet dropped for another reason (malformed, no sender state). */
    public synchronized Decision recordDrop(UUID player) {
        return drop(players.computeIfAbsent(player, k -> new PlayerState()), clock.getAsLong());
    }

    private Decision drop(PlayerState s, long now) {
        s.drops.addLast(now);
        while (!s.drops.isEmpty() && now - s.drops.peekFirst() >= FLOOD_WINDOW_NANOS) {
            s.drops.removeFirst();
        }
        int threshold = floodThreshold.getAsInt();
        int keep = threshold > 0 ? threshold : MAX_TRACKED_DROPS;
        while (s.drops.size() > keep) {
            s.drops.removeFirst(); // bounded memory: only the last `threshold` drops matter
        }
        boolean disconnect = threshold > 0 && !s.disconnected && s.drops.size() >= threshold;
        if (disconnect) {
            s.disconnected = true;
        }
        boolean log = s.lastLog == Long.MIN_VALUE || now - s.lastLog >= LOG_INTERVAL_NANOS;
        if (log) {
            s.lastLog = now;
        }
        return new Decision(false, disconnect, log);
    }

    /** Forgets everything about {@code player} (logout). */
    public synchronized void forget(UUID player) {
        players.remove(player);
    }

    /** Players with state (tests and diagnostics). */
    public synchronized int trackedPlayers() {
        return players.size();
    }
}
