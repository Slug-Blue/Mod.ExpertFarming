package io.github.slugblue.wildroots.client.journal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.slugblue.wildroots.client.journal.page.TextPage;
import io.github.slugblue.wildroots.journal.JournalData;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * WR-181a: the opening rules of journal v2 §2.3 on sets of journal
 * entries: always, closed, open, new, a page with its own {@code unlock},
 * wildcards, silhouettes and {@code showAllGuides}.
 */
class GuideUnlocksTest {

    static GuidePage page(String text, String... unlock) {
        return new GuidePage("text", List.of(unlock), new TextPage(Optional.empty(), text));
    }

    static GuideEntry entry(String id, String cat, int order, List<String> unlock, GuidePage... pages) {
        return new GuideEntry(id, cat, GuideIcon.NONE, order, unlock, List.of(), List.of(), List.of(pages));
    }

    static final GuideEntry START = entry("guide.start", "first", 0, List.of(), page("a"));
    static final GuideEntry CYCLE = entry("guide.cycle", "plant", 0, List.of("hint.mother_plant"), page("b"));
    static final GuideEntry STAGES = entry("guide.stages", "plant", 1, List.of("species.*"), page("c"));
    static final GuideEntry COLORS = entry("guide.colors", "lens", 0, List.of("hint.yellow_root", "hint.purple_root", "hint.red_root", "hint.orange_root"),
            page("yellow", "hint.yellow_root"), page("purple", "hint.purple_root"), page("orange", "hint.orange_root"),
            page("red", "hint.red_root"), page("props", "hint.orange_root", "hint.red_root"),
            page("double", "hint.orange_root", "hint.red_root"), page("ratios", "hint.orange_root", "hint.red_root"));
    static final GuideEntry CULTIVARS = entry("guide.cultivars", "lens", 1, List.of("cultivar.*"), page("d"));

    static final GuideBook BOOK = new GuideBook(
            List.of(new GuideCategory("first", GuideIcon.NONE, 0), new GuideCategory("plant", GuideIcon.NONE, 1),
                    new GuideCategory("lens", GuideIcon.NONE, 2)),
            List.of(START, CYCLE, STAGES, COLORS, CULTIVARS), Map.of());

    static JournalData.Entry j(String id, int flags) {
        return new JournalData.Entry(id, List.of(), List.of(), 0, flags);
    }

    static List<String> open(GuideUnlocks u) {
        return BOOK.entries().stream().filter(u::isOpen).map(GuideEntry::id).toList();
    }

    @Test
    void anEmptyJournalShowsOnlyTheAlwaysOpenEntries() {
        GuideUnlocks u = new GuideUnlocks(BOOK, List.of(), false);
        assertEquals(List.of("guide.start"), open(u));
        assertFalse(u.isNew(START), "an always-open entry is never new");
        assertEquals(List.of("first"), u.openCategories().stream().map(GuideCategory::id).toList(),
                "a category without an open entry is hidden");
        assertFalse(u.isOpen("guide.cycle"));
        assertFalse(u.isOpen("guide.nope"), "an unknown id is closed");
        assertEquals(List.of(), u.pages(CYCLE), "a closed entry shows no page");
    }

    @Test
    void anEntryOpensWithAnyOfItsUnlockIdsAndIsNewWhileTheOpenerIs() {
        GuideUnlocks u = new GuideUnlocks(BOOK, List.of(j("hint.mother_plant", JournalData.NEW)), false);
        assertTrue(u.isOpen(CYCLE));
        assertTrue(u.isNew(CYCLE));
        assertTrue(u.isNew(BOOK.category("plant").orElseThrow()), "the category carries the new mark");
        assertEquals(Set.of("hint.mother_plant"), u.newOpeners(CYCLE), "what MARK_READ is sent for");
        assertEquals(List.of(CYCLE), u.newEntries());

        u = new GuideUnlocks(BOOK, List.of(j("hint.mother_plant", JournalData.READ)), false);
        assertTrue(u.isOpen(CYCLE));
        assertFalse(u.isNew(CYCLE), "read opener: open, not new");
        assertEquals(Set.of(), u.newOpeners(CYCLE));
    }

    @Test
    void aWildcardMatchesAnyEntryOfTheKind() {
        GuideUnlocks u = new GuideUnlocks(BOOK, List.of(j("species.wild_carrot", 0)), false);
        assertTrue(u.isOpen(STAGES));
        assertFalse(new GuideUnlocks(BOOK, List.of(j("speciesx.a", 0)), false).isOpen(STAGES),
                "the kind ends at the dot");
        assertFalse(new GuideUnlocks(BOOK, List.of(j("hint.species", 0)), false).isOpen(STAGES));
    }

    /** Review M3: opening "Growth Stages" (unlock species.*) does not mark the new species records read. */
    @Test
    void aWildcardOpenerIsNotMarkedRead() {
        GuideUnlocks u = new GuideUnlocks(BOOK, List.of(j("species.wild_carrot", JournalData.NEW),
                j("species.wild_beet", JournalData.NEW)), false);
        assertTrue(u.isOpen(STAGES));
        assertEquals(Set.of(), u.newOpeners(STAGES), "no MARK_READ for records matched by a wildcard");
        assertTrue(u.isNew(STAGES), "the entry still shows new while the record is new");
        GuideUnlocks h = new GuideUnlocks(BOOK, List.of(j("hint.mother_plant", JournalData.NEW)), false);
        assertEquals(Set.of("hint.mother_plant"), h.newOpeners(CYCLE), "an exact id is read with its entry");
    }

    @Test
    void aSilhouetteOpensNothing() {
        assertFalse(new GuideUnlocks(BOOK, List.of(j("cultivar.carrot", JournalData.LOCKED | JournalData.NEW)), false)
                .isOpen(CULTIVARS), "a cultivar not yet bred is not met");
        assertTrue(new GuideUnlocks(BOOK, List.of(j("cultivar.carrot", 0)), false).isOpen(CULTIVARS));
    }

    @Test
    void aPageWithItsOwnUnlockIsSkippedWhileClosed() {
        GuideUnlocks u = new GuideUnlocks(BOOK, List.of(j("hint.yellow_root", JournalData.READ)), false);
        assertEquals(List.of("yellow"), u.pages(COLORS).stream().map(p -> ((TextPage) p.data()).text()).toList());
        assertFalse(u.isNew(COLORS));

        u = new GuideUnlocks(BOOK, List.of(j("hint.yellow_root", JournalData.READ), j("hint.orange_root", JournalData.NEW)),
                false);
        assertEquals(List.of("yellow", "orange", "props", "double", "ratios"),
                u.pages(COLORS).stream().map(p -> ((TextPage) p.data()).text()).toList());
        assertTrue(u.isNew(COLORS), "a new page makes its entry new");
        assertEquals(Set.of("hint.orange_root"), u.newOpeners(COLORS));

        // only the red hint: the red page and the three article pages behind orange or red, no purple or orange
        u = new GuideUnlocks(BOOK, List.of(j("hint.red_root", JournalData.READ)), false);
        assertEquals(List.of("red", "props", "double", "ratios"),
                u.pages(COLORS).stream().map(p -> ((TextPage) p.data()).text()).toList());

        u = new GuideUnlocks(BOOK, List.of(j("hint.white_cold", JournalData.NEW)), false);
        assertFalse(u.isOpen(COLORS), "a hint outside the entry's unlock list does not open it");
        assertEquals(Set.of(), u.newOpeners(COLORS));
    }

    @Test
    void anEntryWithNoShownPageIsClosed() {
        GuideEntry onlyLocked = entry("guide.only", "first", 5, List.of(), page("x", "hint.a"));
        GuideBook book = new GuideBook(BOOK.categories(), List.of(onlyLocked), Map.of());
        assertFalse(new GuideUnlocks(book, List.of(), false).isOpen(onlyLocked));
        assertTrue(new GuideUnlocks(book, List.of(j("hint.a", 0)), false).isOpen(onlyLocked));
        assertFalse(new GuideUnlocks(book, List.of(j("hint.a", 0)), false, p -> false).isOpen(onlyLocked),
                "a page that cannot be drawn (no recipe) counts as closed");
    }

    @Test
    void aToolRecordOpensItsGuideEntryAndMakesItNew() {
        GuideEntry pouch = entry("guide.tool.seed_pouch", "first", 1, List.of("hint.never"), page("p"));
        GuideEntry journal = entry("guide.tool.field_journal", "first", 2, List.of(), page("j"));
        GuideEntry lens = entry("guide.lens", "first", 3, List.of(), page("l"));
        GuideBook book = new GuideBook(BOOK.categories(), List.of(pouch, journal, lens), Map.of());
        GuideUnlocks none = new GuideUnlocks(book, List.of(), false);
        assertFalse(none.isOpen(pouch));
        assertFalse(none.isNew(journal));
        GuideUnlocks u = new GuideUnlocks(book, List.of(j("tool.seed_pouch", JournalData.NEW),
                j("tool.field_journal", JournalData.NEW), j("tool.hand_lens", JournalData.NEW)), false);
        assertTrue(u.isOpen(pouch), "opened early by the tool record");
        assertTrue(u.isNew(pouch) && u.isNew(journal) && u.isNew(lens), "always-open entries get the new mark too");
        assertEquals(Set.of("tool.hand_lens"), u.newOpeners(lens));
        assertTrue(u.has("tool.seed_pouch"));
    }

    @Test
    void showAllGuidesOpensEverythingButNewFollowsTheJournal() {
        GuideUnlocks u = new GuideUnlocks(BOOK, List.of(j("hint.purple_root", JournalData.NEW)), true);
        assertEquals(BOOK.entries().stream().map(GuideEntry::id).toList(), open(u));
        assertEquals(7, u.pages(COLORS).size());
        assertEquals(List.of(COLORS), u.newEntries());
        assertEquals(3, u.openCategories().size());
    }
}
