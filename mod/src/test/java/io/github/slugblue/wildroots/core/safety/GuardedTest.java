package io.github.slugblue.wildroots.core.safety;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

/** WR-121, SEC-34: isolation, throttled log, strict mode. */
class GuardedTest {

    private final AtomicLong clock = new AtomicLong(1_000_000);
    private final List<String> lines = new ArrayList<>();

    @Test
    void exceptionIsIsolatedAndObjectMadeInert() {
        Guarded g = new Guarded(false, clock::get, lines::add);
        AtomicBoolean inert = new AtomicBoolean();
        boolean ok = g.execute("randomTick", "0,64,0", () -> {
            throw new IllegalStateException("broken genome");
        }, () -> inert.set(true));
        assertFalse(ok);
        assertTrue(inert.get());
        assertEquals(1, lines.size());
        assertTrue(lines.get(0).contains("randomTick") && lines.get(0).contains("broken genome"), lines.get(0));
        assertFalse(lines.get(0).contains("\n"));
    }

    @Test
    void oneLinePerKeyInFiveMinutes() {
        Guarded g = new Guarded(false, clock::get, lines::add);
        Runnable boom = () -> {
            throw new RuntimeException("x");
        };
        for (int i = 0; i < 100; i++) {
            g.execute("useOn", "pos-a", boom, null);
        }
        g.execute("useOn", "pos-b", boom, null);
        assertEquals(2, lines.size());
        clock.addAndGet(Guarded.THROTTLE_MS - 1);
        g.execute("useOn", "pos-a", boom, null);
        assertEquals(2, lines.size());
        clock.addAndGet(1);
        g.execute("useOn", "pos-a", boom, null);
        assertEquals(3, lines.size());
    }

    @Test
    void strictModeRethrowsAfterMarkingInert() {
        Guarded g = new Guarded(true, clock::get, lines::add);
        IllegalStateException e = new IllegalStateException("bug");
        AtomicBoolean inert = new AtomicBoolean();
        assertSame(e, assertThrows(IllegalStateException.class, () -> g.execute("tick", "k", () -> {
            throw e;
        }, () -> inert.set(true))));
        assertTrue(inert.get());
    }

    @Test
    void errorsAreNotCaught() {
        Guarded g = new Guarded(false, clock::get, lines::add);
        assertThrows(OutOfMemoryError.class, () -> g.execute("tick", "k", () -> {
            throw new OutOfMemoryError("not ours to hide");
        }, null));
    }

    @Test
    void computeReturnsFallbackAndFailingHandlerDoesNotEscape() {
        Guarded g = new Guarded(false, clock::get, lines::add);
        assertEquals(7, g.compute("calc", "k", () -> 7, -1));
        assertEquals(-1, g.compute("calc", "k", () -> {
            throw new ArithmeticException();
        }, -1));
        assertFalse(g.execute("tick", "k2", () -> {
            throw new RuntimeException("a");
        }, () -> {
            throw new RuntimeException("handler");
        }));
    }

    @Test
    void sharedInstanceFollowsTheJvmProperty() {
        assertEquals(Boolean.getBoolean(Guarded.STRICT_PROPERTY), Guarded.shared().strict());
    }
}
