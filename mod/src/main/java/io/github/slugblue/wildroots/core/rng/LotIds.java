package io.github.slugblue.wildroots.core.rng;

import io.github.slugblue.wildroots.genetics.random.Hash64;
import java.util.concurrent.atomic.AtomicLong;

/**
 * {@code lotId}: 64 bits, assigned when the item is created, lazy lots
 * included ({@code tpl.lotId}), never when it is realised (SEC-13;
 * docs/05-architecture.md §4.3). Splitting a stack never changes it, so both
 * halves of a lazy stack realise the same genome.
 *
 * <p>{@code Hash64(secret, gameTime, counter)}: the counter makes ids of one
 * tick distinct, the secret makes them unpredictable for clients.
 */
public final class LotIds {

    private static final AtomicLong COUNTER = new AtomicLong();

    private LotIds() {
    }

    /** {@code Hash64(secret, gameTime, counter)}. */
    public static long derive(long secret, long gameTime, long counter) {
        return Hash64.of(secret, gameTime, counter);
    }

    /** A fresh id for an item created at {@code gameTime}. */
    public static long next(long secret, long gameTime) {
        return derive(secret, gameTime, COUNTER.incrementAndGet());
    }
}
