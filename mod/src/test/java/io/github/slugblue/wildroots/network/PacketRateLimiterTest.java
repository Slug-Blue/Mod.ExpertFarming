package io.github.slugblue.wildroots.network;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

/** WR-127, SEC-02: token bucket 5 / 5 per second, flood disconnect, log throttle, forget on logout. */
class PacketRateLimiterTest {

    static final long SECOND = 1_000_000_000L;
    final AtomicLong now = new AtomicLong(42 * SECOND);
    final AtomicInteger threshold = new AtomicInteger(200);
    final PacketRateLimiter limiter = new PacketRateLimiter(now::get, () -> 5, () -> 5, threshold::get,
            Map.of("NameSelection", () -> PacketRateLimiter.ExtraLimit.perMinute(3)));
    final UUID alice = UUID.randomUUID();
    final UUID bob = UUID.randomUUID();

    int allowed(UUID p, String type, int n) {
        int ok = 0;
        for (int i = 0; i < n; i++) {
            if (limiter.tryAcquire(p, type).allowed()) {
                ok++;
            }
        }
        return ok;
    }

    @Test
    void fiveAtOnceTheSixthIsDropped() {
        assertEquals(5, allowed(alice, "JournalAction", 5));
        assertFalse(limiter.tryAcquire(alice, "JournalAction").allowed());
        assertEquals(5, allowed(alice, "Other", 5), "buckets are per type");
        assertEquals(5, allowed(bob, "JournalAction", 5), "buckets are per player");
    }

    @Test
    void refillsFivePerSecond() {
        assertEquals(5, allowed(alice, "JournalAction", 10));
        now.addAndGet(SECOND / 5); // one token
        assertEquals(1, allowed(alice, "JournalAction", 3));
        now.addAndGet(10 * SECOND); // capped at 5
        assertEquals(5, allowed(alice, "JournalAction", 10));
    }

    @Test
    void nameSelectionAtMostThreePerMinute() {
        assertEquals(3, allowed(alice, "NameSelection", 5));
        now.addAndGet(5 * SECOND);
        assertEquals(0, allowed(alice, "NameSelection", 1));
        now.addAndGet(20 * SECOND);
        assertEquals(1, allowed(alice, "NameSelection", 2));
    }

    @Test
    void twoHundredDropsInTenSecondsDisconnectOnce() {
        allowed(alice, "JournalAction", 5);
        int disconnects = 0;
        for (int i = 0; i < 199; i++) {
            if (limiter.tryAcquire(alice, "JournalAction").disconnect()) {
                disconnects++;
            }
        }
        assertEquals(0, disconnects, "199 drops do not disconnect");
        assertTrue(limiter.tryAcquire(alice, "JournalAction").disconnect(), "the 200th drop disconnects");
        assertFalse(limiter.tryAcquire(alice, "JournalAction").disconnect(), "only once");
    }

    @Test
    void dropsOutsideTheWindowDoNotCount() {
        allowed(alice, "JournalAction", 5);
        for (int i = 0; i < 199; i++) {
            limiter.tryAcquire(alice, "JournalAction");
        }
        now.addAndGet(PacketRateLimiter.FLOOD_WINDOW_NANOS);
        allowed(alice, "JournalAction", 5); // bucket refilled meanwhile
        assertFalse(limiter.tryAcquire(alice, "JournalAction").disconnect());
    }

    @Test
    void thresholdZeroNeverDisconnects() {
        threshold.set(0);
        for (int i = 0; i < 20_000; i++) {
            assertFalse(limiter.tryAcquire(alice, "JournalAction").disconnect());
        }
    }

    @Test
    void malformedPacketsCountAsDrops() {
        for (int i = 0; i < 199; i++) {
            assertFalse(limiter.recordDrop(alice).disconnect());
        }
        assertTrue(limiter.recordDrop(alice).disconnect());
    }

    @Test
    void atMostOneLogLinePerPlayerPerMinute() {
        allowed(alice, "JournalAction", 5);
        int logs = 0;
        for (int i = 0; i < 100; i++) {
            if (limiter.tryAcquire(alice, "JournalAction").log()) {
                logs++;
            }
        }
        assertEquals(1, logs);
        now.addAndGet(PacketRateLimiter.LOG_INTERVAL_NANOS);
        assertEquals(5, allowed(alice, "JournalAction", 5)); // refilled meanwhile
        assertTrue(limiter.tryAcquire(alice, "JournalAction").log(), "a new minute logs again");
        assertTrue(limiter.recordDrop(bob).log(), "throttle is per player");
    }

    @Test
    void forgetOnLogout() {
        allowed(alice, "JournalAction", 5);
        assertEquals(1, limiter.trackedPlayers());
        limiter.forget(alice);
        assertEquals(0, limiter.trackedPlayers());
        assertEquals(5, allowed(alice, "JournalAction", 5));
    }
}
