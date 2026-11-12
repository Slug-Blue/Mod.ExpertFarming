package io.github.slugblue.wildroots.client.journal.page;

import io.github.slugblue.wildroots.client.journal.GuidePage;
import java.util.List;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * Lays out one page type on the client (journal v2 §3). Called once per
 * layout for each shown page of the open entry: wrap text, look up the
 * recipe or scene, decide how many pages the content needs. Returns the
 * pages in order; an empty list skips the page (a recipe the client lacks,
 * §3.2). Must not throw: the book catches anything thrown, logs one line
 * and skips the page.
 *
 * @param <D> the page data its {@link PageParser} builds
 */
@OnlyIn(Dist.CLIENT)
@FunctionalInterface
public interface PageType<D extends PageData> {

    List<LaidOutPage> layout(D data, GuidePage page, PageContext ctx);
}
