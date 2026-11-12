package io.github.slugblue.wildroots.client.journal.search;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * WR-181f: the search index (journal v2 §3.9), local bookmarks (§3.10)
 * and the JournalAction queue (§5.1, §5.5).
 */
class SearchAndBookmarksTest {

    static SearchIndex.Doc<String> doc(String title, String text) {
        return new SearchIndex.Doc<>(title, "cat", text, title);
    }

    @Test
    void searchIgnoresCaseAndYoAndPutsTitlesFirst() {
        SearchIndex<String> idx = new SearchIndex<>(List.of(
                doc("Цикл двулетника", "Маточник зацветает, пчёлы приносят пыльцу."),
                doc("Пчёлы", "Улей рядом с грядкой."),
                doc("Лупа", "Стекло и медь.")));
        assertEquals(List.of("Пчёлы", "Цикл двулетника"), idx.search("ПЧЕЛЫ").stream().map(SearchIndex.Doc::title).toList(),
                "case and ё/е do not matter; the title match first");
        assertEquals(List.of("Лупа"), idx.search("  медь ").stream().map(SearchIndex.Doc::title).toList());
        assertEquals(List.of(), idx.search(""));
        assertEquals(List.of(), idx.search("кисть"));
    }

    @Test
    void onlyWhatWasIndexedIsFoundAndResultsAreCapped() {
        // the index holds the open entries only: the closed one was never added, so it cannot be found
        List<SearchIndex.Doc<String>> open = new ArrayList<>();
        for (int i = 0; i < 50; i++) {
            open.add(doc("Note " + i, "seed"));
        }
        SearchIndex<String> idx = new SearchIndex<>(open);
        assertEquals(SearchIndex.MAX_RESULTS, idx.search("seed").size());
        assertEquals(List.of(), idx.search("orange root"), "a closed entry's text is not in the index");
    }

    @Test
    void localBookmarksKeepSixteenAWorldAndSurviveAReread() {
        LocalBookmarks b = new LocalBookmarks();
        for (int i = 0; i < LocalBookmarks.MAX_PER_WORLD; i++) {
            assertTrue(b.add("sp:World", "guide.e" + i));
        }
        assertFalse(b.add("sp:World", "guide.more"), "the 17th is refused");
        assertTrue(b.add("sp:World", "guide.e3"), "an existing one is fine");
        assertTrue(b.add("mp:example.net", "guide.cycle"));
        b.remove("sp:World", "guide.e0");
        LocalBookmarks again = LocalBookmarks.read(b.write());
        assertEquals(15, again.get("sp:World").size());
        assertFalse(again.has("sp:World", "guide.e0"));
        assertEquals(List.of("guide.cycle"), again.get("mp:example.net"));
        assertTrue(b.write().length <= LocalBookmarks.MAX_FILE_BYTES);
    }

    @Test
    void theFileStaysSmallAndABrokenFileIsEmpty() {
        LocalBookmarks b = new LocalBookmarks();
        for (int w = 0; w < 100; w++) {
            for (int i = 0; i < 4; i++) {
                b.add("sp:world_number_" + w, "guide.entry_" + i);
            }
        }
        byte[] file = b.write();
        assertTrue(file.length <= LocalBookmarks.MAX_FILE_BYTES);
        LocalBookmarks back = LocalBookmarks.read(file);
        assertEquals(4, back.get("sp:world_number_99").size(), "the worlds used last are kept");
        assertEquals(List.of(), back.get("sp:world_number_0"), "the oldest went first");
        for (String bad : List.of("", "{", "[]", "{\"w\": [1, \"Guide.X\", \"guide.ok\"]}")) {
            LocalBookmarks r = LocalBookmarks.read(bad.getBytes(StandardCharsets.UTF_8));
            assertEquals(bad.contains("guide.ok") ? List.of("guide.ok") : List.of(), r.get("w"), bad);
        }
        assertEquals(List.of(), LocalBookmarks.read(new byte[LocalBookmarks.MAX_FILE_BYTES + 1]).get("w"));
    }

    @Test
    void actionsLeaveAtMostFourASecondAndTheQueueIsBounded() {
        ActionQueue<String> q = new ActionQueue<>();
        for (int i = 0; i < 40; i++) {
            boolean ok = q.offer("a" + i);
            assertEquals(i < ActionQueue.MAX_QUEUED, ok, "offer " + i);
        }
        assertTrue(q.offer("a0"), "a duplicate is already queued");
        assertEquals(ActionQueue.MAX_QUEUED, q.size());
        List<String> sent = new ArrayList<>();
        assertEquals(4, q.drain(0, sent::add));
        assertEquals(0, q.drain(500, sent::add), "the same second");
        assertEquals(0, q.drain(999, sent::add));
        assertEquals(4, q.drain(1000, sent::add));
        long t = 1000;
        while (q.size() > 0) {
            t += 50;
            q.drain(t, sent::add);
        }
        assertEquals(ActionQueue.MAX_QUEUED, sent.size());
        assertEquals("a0", sent.get(0), "in order");
        // over any 1 s window, at most 4
        assertTrue(t >= 7000, "32 actions take at least 7 s: " + t);
    }
}
