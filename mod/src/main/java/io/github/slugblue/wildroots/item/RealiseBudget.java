package io.github.slugblue.wildroots.item;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Budget of lazy-lot realisations in {@code inventoryTick} (SEC-35): at most
 * {@value #PER_TICK} per player per game tick; the rest wait for the next
 * tick. Server thread only. One entry per player who realised something in
 * the current tick; older entries are dropped when the tick changes.
 */
public final class RealiseBudget {

    public static final int PER_TICK = 8;

    private final Map<UUID, Integer> used = new HashMap<>();
    private long tick = Long.MIN_VALUE;

    /** Takes one realisation for {@code player} in {@code gameTime}; {@code false} when the budget is spent. */
    public boolean tryTake(UUID player, long gameTime) {
        if (gameTime != tick) {
            tick = gameTime;
            used.clear();
        }
        int n = used.getOrDefault(player, 0);
        if (n >= PER_TICK) {
            return false;
        }
        used.put(player, n + 1);
        return true;
    }

    /** Players with an entry (tests: the map does not grow beyond one tick). */
    int size() {
        return used.size();
    }
}
