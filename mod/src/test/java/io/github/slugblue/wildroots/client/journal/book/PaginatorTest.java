package io.github.slugblue.wildroots.client.journal.book;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.function.ToIntFunction;
import org.junit.jupiter.api.Test;

/**
 * WR-181b: pagination of journal v2 §5.3 with a fake font width. A
 * paragraph is not cut when at most {@value Paginator#ORPHAN} lines of it
 * would stay at the bottom of a page; a heading is never last on a page;
 * nothing is lost or reordered.
 */
class PaginatorTest {

    static final String BLANK = "";
    static final int PER_PAGE = 16;

    /** Greedy word wrap with a fake width (every character 6 px, a space 4 px). */
    static List<String> wrap(String text, int width, ToIntFunction<String> w) {
        List<String> out = new ArrayList<>();
        StringBuilder line = new StringBuilder();
        for (String word : text.split(" ")) {
            String next = line.isEmpty() ? word : line + " " + word;
            if (w.applyAsInt(next) > width && !line.isEmpty()) {
                out.add(line.toString());
                line = new StringBuilder(word);
            } else {
                line = new StringBuilder(next);
            }
        }
        if (!line.isEmpty()) {
            out.add(line.toString());
        }
        return out;
    }

    static final ToIntFunction<String> FAKE = s -> s.chars().map(c -> c == ' ' ? 4 : 6).sum();

    static Paginator.Block<String> para(String tag, int lines) {
        List<String> l = new ArrayList<>();
        for (int i = 0; i < lines; i++) {
            l.add(tag + i);
        }
        return Paginator.Block.paragraph(l);
    }

    static Paginator.Block<String> head(String tag) {
        return Paginator.Block.heading(List.of(tag));
    }

    static List<List<String>> run(List<Paginator.Block<String>> blocks) {
        return Paginator.paginate(blocks, PER_PAGE, BLANK);
    }

    @Test
    void wrappedTextFillsPagesWithBlankLinesBetweenParagraphs() {
        String text = "The lens shows what the eye cannot judge: taste, hardiness, how pollination went.";
        List<String> lines = wrap(text, 116, FAKE);
        assertTrue(lines.size() > 1 && lines.stream().allMatch(l -> FAKE.applyAsInt(l) <= 116), lines::toString);
        List<List<String>> pages = run(List.of(Paginator.Block.paragraph(lines), Paginator.Block.paragraph(lines)));
        assertEquals(1, pages.size());
        assertEquals(lines.size() * 2 + 1, pages.get(0).size());
        assertEquals(BLANK, pages.get(0).get(lines.size()), "one blank line between paragraphs");
    }

    @Test
    void aParagraphMovesWholeWhenAtMostTwoLinesWouldStay() {
        // 13 lines + blank = 14 used; 2 free: a 5-line paragraph moves whole
        List<List<String>> pages = run(List.of(para("a", 13), para("b", 5)));
        assertEquals(2, pages.size());
        assertEquals(13, pages.get(0).size());
        assertEquals(List.of("b0", "b1", "b2", "b3", "b4"), pages.get(1), "no blank line at the top of a page");
        // 12 lines + blank = 13 used; 3 free: it is cut, 3 lines stay
        pages = run(List.of(para("a", 12), para("b", 5)));
        assertEquals(2, pages.size());
        assertEquals(List.of("b0", "b1", "b2"), pages.get(0).subList(13, 16));
        assertEquals(List.of("b3", "b4"), pages.get(1));
    }

    @Test
    void aParagraphLongerThanAPageIsCutAtPageEnds() {
        List<List<String>> pages = run(List.of(para("a", 40)));
        assertEquals(List.of(16, 16, 8), pages.stream().map(List::size).toList());
    }

    @Test
    void aHeadingIsNeverLastOnAPage() {
        // 14 used + blank: the heading would sit on line 16 with nothing after it
        List<List<String>> pages = run(List.of(para("a", 14), head("H"), para("b", 3)));
        assertEquals(2, pages.size());
        assertEquals("H", pages.get(1).get(0));
        // the heading fits with 2 lines of its paragraph, but those 2 lines would move: so does the heading
        pages = run(List.of(para("a", 12), head("H"), para("b", 5)));
        assertEquals("H", pages.get(1).get(0));
        assertEquals("b0", pages.get(1).get(1), "no blank line between a heading and its paragraph");
    }

    @Test
    void randomTextKeepsEveryLineInOrderAndHoldsTheRules() {
        Random r = new Random(181);
        for (int round = 0; round < 500; round++) {
            List<Paginator.Block<String>> blocks = new ArrayList<>();
            int n = 1 + r.nextInt(12);
            for (int b = 0; b < n; b++) {
                blocks.add(r.nextInt(4) == 0 ? head("h" + b + "_") : para("p" + b + "_", 1 + r.nextInt(20)));
            }
            List<List<String>> pages = run(blocks);
            List<String> all = new ArrayList<>();
            for (List<String> page : pages) {
                assertTrue(page.size() <= PER_PAGE);
                assertFalse(!page.isEmpty() && page.get(0).equals(BLANK), "no blank line at a page top");
                String last = page.isEmpty() ? null : page.get(page.size() - 1);
                boolean lastIsHeading = last != null && last.startsWith("h");
                boolean isLastPage = page == pages.get(pages.size() - 1);
                assertFalse(lastIsHeading && !isLastPage, "a heading is never last on a page: " + page);
                page.stream().filter(l -> !l.equals(BLANK)).forEach(all::add);
            }
            List<String> expected = new ArrayList<>();
            blocks.forEach(b -> expected.addAll(b.lines()));
            assertEquals(expected, all);
            // a cut paragraph leaves at least ORPHAN + 1 lines at the bottom of a page
            for (int p = 0; p + 1 < pages.size(); p++) {
                List<String> page = pages.get(p);
                List<String> next = pages.get(p + 1);
                if (page.isEmpty() || next.isEmpty()) {
                    continue;
                }
                String tail = page.get(page.size() - 1);
                String head = next.get(0);
                String block = tail.substring(0, tail.indexOf('_') + 1);
                if (tail.startsWith("p") && head.startsWith(block)) {
                    long left = page.stream().filter(l -> l.startsWith(block)).count();
                    int size = blocks.stream().filter(b -> b.lines().get(0).startsWith(block)).findFirst()
                            .orElseThrow().lines().size();
                    assertTrue(left > Paginator.ORPHAN || left == PER_PAGE || size > PER_PAGE,
                            "orphan of " + block + ": " + left + " lines on " + page);
                }
            }
        }
    }

    @Test
    void anEmptyTextIsOneEmptyPage() {
        assertEquals(List.of(List.of()), run(List.of()));
    }
}
