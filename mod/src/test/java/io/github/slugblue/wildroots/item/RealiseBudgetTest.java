package io.github.slugblue.wildroots.item;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import org.junit.jupiter.api.Test;

/** WR-135, SEC-35: at most 8 lazy realisations per player per tick. */
class RealiseBudgetTest {

    @Test
    void eightPerPlayerPerTick() {
        RealiseBudget b = new RealiseBudget();
        UUID a = new UUID(0, 1);
        UUID c = new UUID(0, 2);
        int taken = 0;
        for (int i = 0; i < 20; i++) {
            taken += b.tryTake(a, 100) ? 1 : 0;
        }
        assertEquals(8, taken);
        assertTrue(b.tryTake(c, 100), "budget is per player");
        assertFalse(b.tryTake(a, 100));
        assertTrue(b.tryTake(a, 101), "a new tick refills the budget");
        assertEquals(1, b.size(), "entries of past ticks are dropped");
    }

    @Test
    void thirtySixLotsNeedFiveTicks() {
        RealiseBudget b = new RealiseBudget();
        UUID a = new UUID(0, 1);
        int left = 36;
        int ticks = 0;
        while (left > 0) {
            ticks++;
            for (int i = 0; i < 36 && left > 0; i++) {
                if (b.tryTake(a, ticks)) {
                    left--;
                }
            }
        }
        assertEquals(5, ticks);
    }
}
