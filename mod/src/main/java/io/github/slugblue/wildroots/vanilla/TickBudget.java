package io.github.slugblue.wildroots.vanilla;

/**
 * At most {@code max} takes per game tick (SEC-35: ≤ 16 vanilla-crop
 * conversions per level per tick, docs/05-architecture.md §11.2). One
 * instance per level; the server thread only. Pure (JUnit).
 */
public final class TickBudget {

    private final int max;
    private long tick = Long.MIN_VALUE;
    private int used;

    public TickBudget(int max) {
        this.max = max;
    }

    /** Takes one unit for {@code gameTime}; {@code false} when this tick's budget is spent. */
    public boolean tryTake(long gameTime) {
        if (gameTime != tick) {
            tick = gameTime;
            used = 0;
        }
        if (used >= max) {
            return false;
        }
        used++;
        return true;
    }
}
