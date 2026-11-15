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
        int[] hand = CalloutLayout.beside(p, c.x() + 10, c.y(), 16, 0, 264);
        assertFalse(c.overlaps(hand[0], hand[1], 16, 16) && hand[0] < c.x() + c.w(), "the hand covers the text");
        assertEquals(c.x() + c.w() + CalloutLayout.GAP, hand[0], "right of the callout");
        int[] free = CalloutLayout.beside(p, 200, 120, 16, 0, 264);
        assertEquals(200, free[0], "a billboard over nothing stays");
    }

    /** Review 1, m8: a callout clamped at the window's edge still leads to its anchor, as an L. */
    @Test
    void leaderReachesTheAnchorWhenTheCalloutIsClampedAtTheEdge() {
        List<CalloutLayout.Want> wants = List.of(new CalloutLayout.Want(255, 80, 60, H),
                new CalloutLayout.Want(263, 80, 60, H));
        List<CalloutLayout.Placed> p = CalloutLayout.place(wants, 0, 264, 0);
        CalloutLayout.Placed moved = p.stream().filter(CalloutLayout.Placed::moved).findFirst().orElseThrow();
        CalloutLayout.Want w = wants.get(moved.index());
        assertTrue(moved.x() + moved.w() <= 264 && w.anchorX() > moved.x() + moved.w() - 4,
                "the callout was not pushed off its anchor: " + moved);
        int[] l = CalloutLayout.leader(p, moved, w);
        assertTrue(l[0] >= moved.x() && l[0] < moved.x() + moved.w(), "the leader starts under the callout");
        assertEquals(moved.y() + moved.h(), l[1]);
        assertEquals(w.anchorY(), l[2], "the vertical ends at the anchor's height");
        assertEquals(w.anchorX(), l[3], "the horizontal ends at the anchor");
    }

    /** Review 1, m8: where a free column exists, the leader passes no other callout. */
    @Test
    void leadersAvoidOtherCalloutsWhereTheyCan() {
        // the 6.png case: the climbed callouts are wider than the one under them, so a free column exists
        List<CalloutLayout.Want> wants = List.of(new CalloutLayout.Want(100, 80, 30, H),
                new CalloutLayout.Want(104, 80, 90, H), new CalloutLayout.Want(108, 80, 120, H));
        List<CalloutLayout.Placed> p = CalloutLayout.place(wants, 0, 264, 0);
        int movedCount = 0;
        for (CalloutLayout.Placed c : p) {
            if (c.moved()) {
                movedCount++;
                int[] l = CalloutLayout.leader(p, c, wants.get(c.index()));
                assertFalse(CalloutLayout.crossesAny(p, c, l[0], l[1], l[2]), "leader of " + c + " crosses a callout");
            }
        }
        assertTrue(movedCount >= 1, "nothing climbed");
        Random r = new Random(81);
        for (int round = 0; round < 2000; round++) {
            List<CalloutLayout.Want> ws = new ArrayList<>();
            for (int i = 0; i < 1 + r.nextInt(6); i++) {
                ws.add(new CalloutLayout.Want(r.nextInt(264), 40 + r.nextInt(70), 20 + r.nextInt(80), H));
            }
            List<CalloutLayout.Placed> q = CalloutLayout.place(ws, 0, 264, 0);
            for (CalloutLayout.Placed c : q) {
                CalloutLayout.Want w = ws.get(c.index());
                int[] l = CalloutLayout.leader(q, c, w);
                assertTrue(l[0] >= c.x() && l[0] < c.x() + c.w(), "leader outside its callout");
                assertEquals(w.anchorX(), l[3]);
                assertEquals(w.anchorY(), l[2]);
            }
        }
    }

    /** Review 1, m8: the moved billboard is checked against every callout and both edges. */
    @Test
    void besideRechecksTheNewSpotAndTheEdges() {
        // two callouts side by side: right of the first is the second, so the hand goes elsewhere
        List<CalloutLayout.Placed> p = List.of(new CalloutLayout.Placed(0, 100, 50, 40, H, false),
                new CalloutLayout.Placed(1, 142, 50, 40, H, false));
        int[] hand = CalloutLayout.beside(p, 110, 50, 16, 0, 264);
        assertFalse(CalloutLayout.overlapsAny(p, hand[0], hand[1], 16, 16), "the hand still covers a callout");
        assertTrue(hand[0] >= 0 && hand[0] + 16 <= 264);
        // at the left edge, left of the callout would leave the window
        List<CalloutLayout.Placed> edge = List.of(new CalloutLayout.Placed(0, 2, 50, 240, H, false));
        int[] h2 = CalloutLayout.beside(edge, 10, 50, 16, 0, 264);
        assertTrue(h2[0] >= 0 && h2[0] + 16 <= 264, "outside the window: " + h2[0]);
        assertFalse(CalloutLayout.overlapsAny(edge, h2[0], h2[1], 16, 16));
        Random r = new Random(7);
        for (int round = 0; round < 2000; round++) {
            List<CalloutLayout.Want> ws = new ArrayList<>();
            for (int i = 0; i < 1 + r.nextInt(6); i++) {
                ws.add(new CalloutLayout.Want(r.nextInt(264), 40 + r.nextInt(70), 20 + r.nextInt(80), H));
            }
            List<CalloutLayout.Placed> q = CalloutLayout.place(ws, 0, 264, 0);
            int x = r.nextInt(248);
            int y = r.nextInt(120);
            int[] at = CalloutLayout.beside(q, x, y, 16, 0, 264);
            boolean moved = at[0] != x || at[1] != y;
            if (moved) {
                assertFalse(CalloutLayout.overlapsAny(q, at[0], at[1], 16, 16), "moved onto a callout");
                assertTrue(at[0] >= 0 && at[0] + 16 <= 264, "moved out of the window");
            }
        }
    }

    @Test
    void aCalloutWiderThanTheWindowStartsAtItsLeftEdge() {
        List<CalloutLayout.Placed> p = CalloutLayout.place(List.of(new CalloutLayout.Want(50, 80, 300, H)), 10, 264,
                0);
        assertEquals(10, p.get(0).x());
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
