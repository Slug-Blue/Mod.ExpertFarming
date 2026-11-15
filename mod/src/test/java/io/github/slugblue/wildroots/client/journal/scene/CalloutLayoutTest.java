package io.github.slugblue.wildroots.client.journal.scene;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;

/**
 * Owner playtest 2 (6.png): callouts of a scene step never overlap, at
 * most four show, a moved callout gets a leader line, and a billboard
 * never covers a callout.
 */
class CalloutLayoutTest {

    static final int H = 13;

    @Test
    void threeCalloutsOverOneRowDoNotOverlap() {
        // the 6.png case: stage labels over three neighbouring plants, anchors 20 px apart, 60 px wide each
        List<CalloutLayout.Want> wants = List.of(new CalloutLayout.Want(100, 80, 60, H),
                new CalloutLayout.Want(120, 80, 60, H), new CalloutLayout.Want(140, 80, 40, H));
        List<CalloutLayout.Placed> p = CalloutLayout.place(wants, 0, 264, 0);
        assertEquals(3, p.size());
        assertNoOverlap(p);
        assertTrue(p.stream().anyMatch(x -> !x.moved()), "one stays straight above its anchor");
        assertTrue(p.stream().anyMatch(CalloutLayout.Placed::moved), "the others climb and get a leader");
    }

    @Test
    void randomCalloutsNeverOverlapAndAtMostFourShow() {
        Random r = new Random(62);
        for (int round = 0; round < 2000; round++) {
            List<CalloutLayout.Want> wants = new ArrayList<>();
            for (int i = 0; i < 1 + r.nextInt(8); i++) {
                wants.add(new CalloutLayout.Want(r.nextInt(264), 30 + r.nextInt(80), 20 + r.nextInt(60), H));
            }
            List<CalloutLayout.Placed> p = CalloutLayout.place(wants, 0, 264, 0);
            assertTrue(p.size() <= CalloutLayout.MAX_SHOWN);
            assertNoOverlap(p);
            for (CalloutLayout.Placed x : p) {
                assertTrue(x.x() >= 0 && x.x() + x.w() <= 264 && x.y() >= 0, "inside the window: " + x);
            }
        }
    }

    @Test
    void aBillboardMovesBesideTheCalloutItWouldCover() {
        List<CalloutLayout.Placed> p = CalloutLayout.place(List.of(new CalloutLayout.Want(100, 80, 60, H)), 0, 264, 0);
        CalloutLayout.Placed c = p.get(0);
        int[] hand = CalloutLayout.beside(p, c.x() + 10, c.y(), 16, 264);
        assertFalse(c.overlaps(hand[0], hand[1], 16, 16) && hand[0] < c.x() + c.w(), "the hand covers the text");
        assertEquals(c.x() + c.w() + CalloutLayout.GAP, hand[0], "right of the callout");
        int[] free = CalloutLayout.beside(p, 200, 120, 16, 264);
        assertEquals(200, free[0], "a billboard over nothing stays");
    }

    static void assertNoOverlap(List<CalloutLayout.Placed> p) {
        for (int i = 0; i < p.size(); i++) {
            for (int j = i + 1; j < p.size(); j++) {
                CalloutLayout.Placed a = p.get(i);
                CalloutLayout.Placed b = p.get(j);
                boolean overlap = a.x() < b.x() + b.w() && b.x() < a.x() + a.w() && a.y() < b.y() + b.h()
                        && b.y() < a.y() + a.h();
                assertFalse(overlap, a + " overlaps " + b);
            }
        }
    }
}
