package io.github.slugblue.wildroots.client.journal.page;

import java.util.List;
import java.util.function.Function;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * A page that a {@link PageType} laid out, before the book has placed it:
 * its span and the factory that creates its view once the book knows the
 * rect (left half, right half or the whole spread). {@code readIds} are
 * journal entries to mark read (MARK_READ) when the page is first shown;
 * guide pages leave it empty (the entry marks its openers, §2.3).
 */
@OnlyIn(Dist.CLIENT)
public record LaidOutPage(PageSpan span, Function<PageRect, PageView> view, List<String> readIds) {

    public LaidOutPage {
        readIds = List.copyOf(readIds);
    }

    public static LaidOutPage half(Function<PageRect, PageView> view) {
        return new LaidOutPage(PageSpan.HALF, view, List.of());
    }

    public static LaidOutPage spread(Function<PageRect, PageView> view) {
        return new LaidOutPage(PageSpan.SPREAD, view, List.of());
    }
}
