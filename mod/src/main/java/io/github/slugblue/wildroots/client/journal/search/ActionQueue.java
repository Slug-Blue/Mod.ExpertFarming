package io.github.slugblue.wildroots.client.journal.search;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Objects;
import java.util.function.Consumer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * The client side of the {@code JournalAction} rate limit (journal v2
 * §5.1, §5.5; SEC-02): actions wait here and leave at most
 * {@value #PER_SECOND} a second; at most {@value #MAX_QUEUED} wait, the
 * rest are dropped. The same action for the same entry is not queued
 * twice. Keeps the server's bucket (5, refill 5/s) from ever refusing the
 * journal.
 *
 * @param <A> an action
 */
@OnlyIn(Dist.CLIENT)
public final class ActionQueue<A> {

    public static final int PER_SECOND = 4;
    public static final int MAX_QUEUED = 32;
    static final long WINDOW_MS = 1000;

    private final Deque<A> queue = new ArrayDeque<>();
    private final Deque<Long> sent = new ArrayDeque<>();

    /** Queues an action; {@code false} if it was dropped (queue full). A duplicate counts as queued. */
    public boolean offer(A action) {
        for (A a : queue) {
            if (Objects.equals(a, action)) {
                return true;
            }
        }
        if (queue.size() >= MAX_QUEUED) {
            return false;
        }
        queue.add(action);
        return true;
    }

    /** Sends what the rate allows at {@code nowMs}. */
    public int drain(long nowMs, Consumer<A> send) {
        while (!sent.isEmpty() && nowMs - sent.peekFirst() >= WINDOW_MS) {
            sent.removeFirst();
        }
        int n = 0;
        while (!queue.isEmpty() && sent.size() < PER_SECOND) {
            send.accept(queue.removeFirst());
            sent.addLast(nowMs);
            n++;
        }
        return n;
    }

    public int size() {
        return queue.size();
    }

    public void clear() {
        queue.clear();
        sent.clear();
    }
}
