package io.github.slugblue.wildroots.pollination;

import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.server.level.ServerLevel;

/**
 * Budget of bee queries to {@link BloomIndex} (SEC-33; docs/05-architecture.md
 * §10.3 item 2): one bee queries at most once per {@value #BEE_INTERVAL}
 * ticks, one level answers at most {@value #PER_LEVEL_TICK} bee queries per
 * tick, and the bees over the budget are put off to the next tick.
 */
public final class BeeQueryLimiter {

    public static final int BEE_INTERVAL = 20;
    public static final int PER_LEVEL_TICK = 64;

    private static final Map<ServerLevel, BeeQueryLimiter> LEVELS = new WeakHashMap<>();

    private long tick = Long.MIN_VALUE;
    private int used;

    public static BeeQueryLimiter of(ServerLevel level) {
        return LEVELS.computeIfAbsent(level, l -> new BeeQueryLimiter());
    }

    /** Takes one query of tick {@code now} from the level budget; {@code false} when it is spent. */
    public boolean tryAcquire(long now) {
        if (now != tick) {
            tick = now;
            used = 0;
        }
        if (used >= PER_LEVEL_TICK) {
            return false;
        }
        used++;
        return true;
    }

    /** The per-bee side: the next tick this bee may query. Lives in the goal, never in a map of bees. */
    public static final class Gate {

        private long next = Long.MIN_VALUE;

        /**
         * Whether the bee may query at {@code now}: its interval has passed
         * and the level budget has room. Put off by one tick when the level
         * budget is spent, by {@value #BEE_INTERVAL} after a query.
         */
        public boolean tryQuery(long now, BeeQueryLimiter level) {
            if (now < next) {
                return false;
            }
            if (!level.tryAcquire(now)) {
                next = now + 1;
                return false;
            }
            next = now + BEE_INTERVAL;
            return true;
        }

        /** After a visit or a give-up: wait {@code ticks} before the next query. */
        public void delay(long now, int ticks) {
            next = Math.max(next, now + ticks);
        }
    }
}
