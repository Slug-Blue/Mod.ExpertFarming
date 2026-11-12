package io.github.slugblue.wildroots.client.journal.book;

import io.github.slugblue.wildroots.client.JournalPages;
import io.github.slugblue.wildroots.client.journal.GuideCategory;
import io.github.slugblue.wildroots.client.journal.GuideEntry;
import io.github.slugblue.wildroots.client.journal.GuideUnlocks;
import io.github.slugblue.wildroots.client.journal.page.LaidOutPage;
import io.github.slugblue.wildroots.client.journal.page.PageRect;
import io.github.slugblue.wildroots.client.journal.page.PageView;
import io.github.slugblue.wildroots.client.journal.page.TextLayout;
import io.github.slugblue.wildroots.journal.JournalData;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * Pages the book builds by code (journal v2 §3.8, §2.2): the contents,
 * the entry list of a guide category, and (until WR-181e) the records
 * categories as v1 text blocks.
 */
@OnlyIn(Dist.CLIENT)
final class BookPages {

    /** Most rows of the "New" block of the contents (§3.8). */
    static final int MAX_NEW = 5;

    private BookPages() {
    }

    static Component heading(String key) {
        return TextLayout.heading(Component.translatable(key));
    }

    /** An empty half page (the free side of a spread). */
    static LaidOutPage blank() {
        return LaidOutPage.half(Blank::new);
    }

    record Blank(PageRect rect) implements PageView {

        @Override
        public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        }
    }

    /**
     * The contents spread: the guide on the left (categories, then the new
     * entries), my records on the right (categories with counts, then the
     * pinned ones). If a side runs over, both sides go on in step.
     */
    static List<LaidOutPage> contents(GuideUnlocks unlocks, List<JournalData.Entry> entries,
                                      Consumer<BookLocation> jump) {
        List<ListPageView.Item> left = new ArrayList<>();
        left.add(new ListPageView.Heading(heading("wildroots.journal.group.guide")));
        for (GuideCategory c : unlocks.openCategories()) {
            left.add(new ListPageView.Row(BookIcon.of(c.icon()), Component.translatable(c.titleKey()), Component.empty(),
                    unlocks.isNew(c), false, () -> jump.accept(BookLocation.category(c.id()))));
        }
        List<ListPageView.Row> news = new ArrayList<>();
        for (GuideEntry e : unlocks.newEntries()) {
            news.add(new ListPageView.Row(BookIcon.NONE, Component.translatable(e.titleKey()), Component.empty(), false,
                    true, () -> jump.accept(BookLocation.entry(e.id()))));
        }
        entries.stream().filter(e -> e.is(JournalData.NEW) && !e.is(JournalData.LOCKED))
                .sorted(Comparator.comparingInt(JournalData.Entry::order).reversed())
                .forEach(e -> RecordsCategory.of(e.id()).ifPresent(rc -> news.add(new ListPageView.Row(BookIcon.NONE,
                        recordTitle(e), Component.empty(), false, true, () -> jump.accept(BookLocation.records(rc))))));
        if (!news.isEmpty()) {
            left.add(new ListPageView.Gap());
            left.add(new ListPageView.Heading(heading("wildroots.journal.ui.new_list")));
            left.addAll(news.subList(0, Math.min(MAX_NEW, news.size())));
        }

        List<ListPageView.Item> right = new ArrayList<>();
        right.add(new ListPageView.Heading(heading("wildroots.journal.group.records")));
        boolean anyRecords = false;
        for (RecordsCategory rc : RecordsCategory.values()) {
            if (!rc.visible(entries)) {
                continue;
            }
            anyRecords = true;
            RecordsCategory.Count n = rc.count(entries);
            Component count = n.total().<Component>map(t -> Component.translatable("wildroots.journal.ui.count_of",
                    n.shown(), t)).orElse(Component.literal(Integer.toString(n.shown())));
            right.add(new ListPageView.Row(BookIcon.sprite(rc.icon), Component.translatable(rc.titleKey()), count,
                    rc.hasNew(entries), false, () -> jump.accept(BookLocation.records(rc))));
        }
        if (!anyRecords) {
            right.add(new ListPageView.Row(BookIcon.NONE, Component.translatable("wildroots.journal.ui.empty"),
                    Component.empty(), false, false, () -> { }));
        }
        List<ListPageView.Row> pinned = new ArrayList<>();
        entries.stream().filter(e -> e.is(JournalData.PINNED)).forEach(e -> RecordsCategory.of(e.id())
                .ifPresent(rc -> pinned.add(new ListPageView.Row(BookIcon.NONE, recordTitle(e), Component.empty(), false,
                        true, () -> jump.accept(BookLocation.records(rc))))));
        if (!pinned.isEmpty()) {
            right.add(new ListPageView.Gap());
            right.add(new ListPageView.Heading(heading("wildroots.journal.ui.pinned")));
            right.addAll(pinned);
        }
        List<LaidOutPage> l = ListPageView.pages(left);
        List<LaidOutPage> r = ListPageView.pages(right);
        List<LaidOutPage> out = new ArrayList<>();
        for (int i = 0; i < Math.max(l.size(), r.size()); i++) {
            out.add(i < l.size() ? l.get(i) : blank());
            out.add(i < r.size() ? r.get(i) : blank());
        }
        return out;
    }

    /** The one-line name of a journal entry, as its v1 page heads it. */
    static Component recordTitle(JournalData.Entry e) {
        String id = e.id();
        String rest = id.substring(id.indexOf('.') + 1);
        if (id.startsWith("hint.")) {
            String base = rest.contains(".") ? rest.substring(0, rest.indexOf('.')) : rest;
            return Component.translatable("wildroots.journal.hint." + base + ".title");
        }
        if (id.startsWith("species.")) {
            return Component.translatable("wildroots.species." + rest);
        }
        if (id.startsWith("cultivar.")) {
            return Component.translatable("wildroots.cultivar." + rest);
        }
        if (id.startsWith("note.")) {
            return Component.translatable("wildroots.journal.note." + rest + ".title");
        }
        return Component.translatable("item.wildroots." + rest);
    }

    /** The entry list of a guide category. */
    static List<LaidOutPage> category(GuideUnlocks unlocks, GuideCategory c, Consumer<BookLocation> jump) {
        List<ListPageView.Item> items = new ArrayList<>();
        for (GuideEntry e : unlocks.openEntries(c.id())) {
            items.add(new ListPageView.Row(BookIcon.of(e.icon()), Component.translatable(e.titleKey()), Component.empty(),
                    unlocks.isNew(e), false, () -> jump.accept(BookLocation.entry(e.id()))));
        }
        return ListPageView.pages(items);
    }

    /**
     * A records category, until WR-181e: the v1 blocks of its tab laid out
     * as text (each block a heading and paragraphs), tagged with the entry
     * they show so that showing them marks them read.
     */
    static List<LaidOutPage> records(Font font, RecordsCategory rc, List<JournalData.Entry> entries) {
        List<io.github.slugblue.wildroots.client.journal.book.Paginator.Block<TextLayout.Line>> blocks =
                new ArrayList<>();
        for (JournalPages.Block b : JournalPages.blocks(rc.tab, entries)) {
            String id = b.ids().isEmpty() ? null : b.ids().get(0);
            for (int i = 0; i < b.lines().size(); i++) {
                blocks.add(TextLayout.block(font, b.lines().get(i), PageRect.HALF_WIDTH, i == 0, id));
            }
        }
        if (blocks.isEmpty()) {
            blocks.add(TextLayout.block(font, Component.translatable("wildroots.journal.ui.empty"), PageRect.HALF_WIDTH,
                    false, null));
        }
        return TextLayout.textPages(blocks);
    }
}
