package io.github.slugblue.wildroots.client.journal.book;

import java.util.ArrayList;
import java.util.List;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * Splits wrapped text into pages (journal v2 §5.3). Works on lines that
 * are already wrapped (the screen wraps with {@code Font.split}, tests with
 * a fake width), so it never measures anything itself.
 *
 * <ul>
 *   <li>Paragraphs are separated by one blank line, never at the top of a
 *       page.</li>
 *   <li>A paragraph that does not fit is not cut when at most
 *       {@value #ORPHAN} lines of it would stay at the bottom of the page:
 *       it moves to the next page whole. A paragraph longer than a page is
 *       cut at page ends.</li>
 *   <li>A heading (or headings in a row) is never the last thing on a
 *       page: it moves to the next page with its paragraph.</li>
 * </ul>
 */
@OnlyIn(Dist.CLIENT)
public final class Paginator {

    /** Most lines of a paragraph that may be left alone at the bottom of a page. */
    public static final int ORPHAN = 2;

    /** A heading or a paragraph, already wrapped. */
    public record Block<L>(List<L> lines, boolean heading) {

        public Block {
            lines = List.copyOf(lines);
        }

        public static <L> Block<L> heading(List<L> lines) {
            return new Block<>(lines, true);
        }

        public static <L> Block<L> paragraph(List<L> lines) {
            return new Block<>(lines, false);
        }
    }

    private Paginator() {
    }

    /**
     * @param perPage lines on a page, at least {@value #ORPHAN} + 2
     * @param blank   the blank line between paragraphs
     * @return pages of lines; at least one page (maybe empty)
     */
    public static <L> List<List<L>> paginate(List<Block<L>> blocks, int perPage, L blank) {
        if (perPage < ORPHAN + 2) {
            throw new IllegalArgumentException("perPage " + perPage);
        }
        List<List<L>> pages = new ArrayList<>();
        List<L> page = new ArrayList<>();
        boolean afterHeading = false;
        for (int i = 0; i < blocks.size(); i++) {
            Block<L> b = blocks.get(i);
            if (b.lines().isEmpty()) {
                continue;
            }
            boolean gap = !page.isEmpty() && !afterHeading;
            int free = perPage - page.size() - (gap ? 1 : 0);
            if (b.heading() && !afterHeading) {
                // headings in a row move together with the paragraph after them
                int group = 0;
                int j = i;
                for (; j < blocks.size() && (blocks.get(j).heading() || blocks.get(j).lines().isEmpty()); j++) {
                    group += blocks.get(j).lines().size();
                }
                Block<L> next = j < blocks.size() ? blocks.get(j) : null;
                int rest = free - group;
                boolean nextWouldMove = next != null && next.lines().size() > rest && rest <= ORPHAN;
                if (!page.isEmpty() && (rest < 0 || next != null && (rest == 0 || nextWouldMove))) {
                    pages.add(page);
                    page = new ArrayList<>();
                    gap = false;
                }
            } else if (!afterHeading && !page.isEmpty() && b.lines().size() > free && free <= ORPHAN) {
                pages.add(page);
                page = new ArrayList<>();
                gap = false;
            }
            if (gap) {
                page.add(blank);
            }
            for (L line : b.lines()) {
                if (page.size() >= perPage) {
                    pages.add(page);
                    page = new ArrayList<>();
                }
                page.add(line);
            }
            afterHeading = b.heading();
        }
        if (!page.isEmpty() || pages.isEmpty()) {
            pages.add(page);
        }
        return pages;
    }

}
