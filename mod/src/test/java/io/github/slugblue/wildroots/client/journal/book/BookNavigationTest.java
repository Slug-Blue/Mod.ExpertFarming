package io.github.slugblue.wildroots.client.journal.book;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.slugblue.wildroots.client.journal.page.PageRect;
import io.github.slugblue.wildroots.client.journal.page.PageSpan;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * WR-181b: the history stack (32 each way), spread assembly (a scene
 * starts its own spread), the remembered page, and the book geometry at
 * the smallest window (journal v2 §3.0, §3.11, §5.3).
 */
class BookNavigationTest {

    @Test
    void historyGoesBackAndForwardAndForgetsForwardOnAJump() {
        BookHistory<String> h = new BookHistory<>();
        assertEquals(Optional.empty(), h.back("a"));
        h.push("a");
        h.push("b");
        assertEquals(Optional.of("b"), h.back("c"));
        assertEquals(Optional.of("a"), h.back("b"));
        assertFalse(h.canGoBack());
        assertEquals(Optional.of("b"), h.forward("a"));
        assertEquals(Optional.of("c"), h.forward("b"));
        assertFalse(h.canGoForward());
        h.back("c");
        h.push("x");
        assertFalse(h.canGoForward(), "a jump clears the forward stack");
    }

    @Test
    void historyKeepsTheLast32() {
        BookHistory<Integer> h = new BookHistory<>();
        for (int i = 0; i < 100; i++) {
            h.push(i);
        }
        assertEquals(BookHistory.MAX, h.backSize());
        int cur = 100;
        int steps = 0;
        Optional<Integer> prev;
        while ((prev = h.back(cur)).isPresent()) {
            assertEquals(cur - 1, prev.get());
            cur = prev.get();
            steps++;
        }
        assertEquals(32, steps);
        assertEquals(68, cur, "the oldest are dropped");
        assertEquals(BookHistory.MAX, h.forwardSize());
    }

    record P(String id, PageSpan span) {
    }

    static List<String> layout(List<Spreads.Slots<P>> s) {
        return s.stream().map(x -> x.whole() != null ? "[" + x.whole().id() + "]"
                : (x.left() == null ? "-" : x.left().id()) + "|" + (x.right() == null ? "-" : x.right().id())).toList();
    }

    @Test
    void halfPagesPairUpAndASpreadPageStartsItsOwnSpread() {
        P a = new P("a", PageSpan.HALF);
        P b = new P("b", PageSpan.HALF);
        P c = new P("c", PageSpan.HALF);
        P s = new P("S", PageSpan.SPREAD);
        assertEquals(List.of("a|b", "c|-"), layout(Spreads.assemble(List.of(a, b, c), P::span)));
        assertEquals(List.of("a|-", "[S]", "b|c"), layout(Spreads.assemble(List.of(a, s, b, c), P::span)));
        assertEquals(List.of("[S]", "[S]", "a|-"), layout(Spreads.assemble(List.of(s, s, a), P::span)));
        assertEquals(List.of(), Spreads.assemble(List.<P>of(), P::span));
    }

    @Test
    void theJournalReopensWhereItWasUnlessSomethingNewCame() {
        JournalSession.reset();
        assertEquals(BookLocation.CONTENTS, JournalSession.openAt(Set.of()), "first opening: contents");
        BookLocation at = BookLocation.entry("guide.cycle").withSpread(2);
        JournalSession.closed(at, Set.of("hint.a"));
        assertEquals(at, JournalSession.openAt(Set.of("hint.a")));
        assertEquals(at, JournalSession.openAt(Set.of()), "read since: still the last page");
        assertEquals(BookLocation.CONTENTS, JournalSession.openAt(Set.of("hint.a", "hint.b")), "something new");
        JournalSession.reset();
        assertEquals(BookLocation.CONTENTS, JournalSession.openAt(Set.of()), "after leaving the world");
    }

    @Test
    void theBookFitsTheSmallestWindowAtEveryScale() {
        // Minecraft picks the GUI scale so the scaled window is never below 320 x 240
        for (int[] wh : new int[][] {{320, 240}, {480, 270}, {640, 360}, {341, 256}, {960, 540}, {427, 240}}) {
            BookGeometry g = BookGeometry.center(wh[0], wh[1]);
            assertTrue(g.tabX() >= 0 && g.x() + BookGeometry.SPREAD_W <= wh[0], "width " + wh[0]);
            assertTrue(g.y() >= 0 && g.navY() + BookGeometry.NAV_H <= wh[1], "height " + wh[1]);
            assertTrue(g.tabY(BookGeometry.MAX_TABS - 1) + BookGeometry.TAB_H <= g.y() + BookGeometry.SPREAD_H,
                    "8 tabs fit the spread's height");
            for (PageRect r : List.of(g.left(), g.right(), g.spread())) {
                assertTrue(r.x() >= g.x() && r.right() <= g.x() + BookGeometry.SPREAD_W);
                assertTrue(r.y() > g.headerY() && r.bottom() <= g.y() + BookGeometry.SPREAD_H);
            }
            assertTrue(g.left().right() <= g.x() + BookGeometry.HALF_W && g.right().x() >= g.x() + BookGeometry.HALF_W,
                    "pages stay off the spine");
        }
        assertEquals(16, PageRect.LINES);
    }
}
