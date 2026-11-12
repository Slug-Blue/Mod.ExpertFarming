package io.github.slugblue.wildroots.client.journal.records;

import io.github.slugblue.wildroots.client.ClientDefinitions;
import io.github.slugblue.wildroots.client.journal.GuideBook;
import io.github.slugblue.wildroots.client.journal.JournalColors;
import io.github.slugblue.wildroots.client.journal.book.BookIcon;
import io.github.slugblue.wildroots.client.journal.book.Paginator;
import io.github.slugblue.wildroots.client.journal.page.LaidOutPage;
import io.github.slugblue.wildroots.client.journal.page.PageContext;
import io.github.slugblue.wildroots.client.journal.page.PageRect;
import io.github.slugblue.wildroots.client.journal.page.PageSpan;
import io.github.slugblue.wildroots.client.journal.page.PageTypes;
import io.github.slugblue.wildroots.client.journal.page.PageView;
import io.github.slugblue.wildroots.client.journal.page.PageWidgets;
import io.github.slugblue.wildroots.client.journal.page.TextLayout;
import io.github.slugblue.wildroots.client.journal.page.TextPageView;
import io.github.slugblue.wildroots.data.sync.SyncedDefinitions;
import io.github.slugblue.wildroots.journal.JournalData;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * The "My records" pages (journal v2 §3.5, §3.7, §3.12, §4.2): grids of
 * species and cultivars with counters, a page per species and per bred
 * cultivar with its picture, the lines (M1a: the cultivar rows the server
 * sends), notes several to a page with "More →" to their guide entry, and
 * the breeder's notes. All from the server's entries; the texts are the v1
 * blocks ({@link RecordBlocks}).
 */
@OnlyIn(Dist.CLIENT)
public final class RecordsLayout {

    private RecordsLayout() {
    }

    static boolean kind(JournalData.Entry e, String kind) {
        return e.id().startsWith(kind + ".");
    }

    /** The journal id rest of a data id, as {@code JournalTriggers.entry} writes it. */
    static String rest(ResourceLocation id) {
        return (id.getNamespace().equals("wildroots") ? id.getPath() : id.getNamespace() + "." + id.getPath())
                .replace('/', '.').replace('-', '_');
    }

    static Optional<SyncedDefinitions.Species> species(String rest) {
        return ClientDefinitions.get().species().stream().filter(s -> rest(s.id()).equals(rest)).findFirst();
    }

    static Optional<SyncedDefinitions.Cultivar> cultivar(String rest) {
        return ClientDefinitions.get().cultivars().stream().filter(c -> rest(c.id()).equals(rest)).findFirst();
    }

    static ItemStack stackOf(ResourceLocation id) {
        Item item = ForgeRegistries.ITEMS.getValue(id);
        return item == null || item == Items.AIR ? ItemStack.EMPTY : new ItemStack(item);
    }

    /** A species' picture: its wild root ({@code <species>_root}), else its first cultivar's produce. */
    public static ItemStack speciesStack(String rest) {
        Optional<SyncedDefinitions.Species> s = species(rest);
        if (s.isPresent()) {
            ItemStack root = stackOf(io.github.slugblue.wildroots.core.Ids.of(s.get().id().getNamespace(), s.get().id().getPath() + "_root"));
            if (!root.isEmpty()) {
                return root;
            }
            return ClientDefinitions.get().cultivars().stream().filter(c -> c.species().equals(s.get().id()))
                    .sorted(Comparator.comparingInt(SyncedDefinitions.Cultivar::tier))
                    .map(c -> c.produceItem().map(RecordsLayout::stackOf).orElse(ItemStack.EMPTY))
                    .filter(x -> !x.isEmpty()).findFirst().orElse(ItemStack.EMPTY);
        }
        return ItemStack.EMPTY;
    }

    public static ItemStack cultivarStack(String rest) {
        return cultivar(rest).flatMap(SyncedDefinitions.Cultivar::produceItem).map(RecordsLayout::stackOf)
                .orElse(ItemStack.EMPTY);
    }

    static BookIcon icon(ItemStack s, String fallback) {
        return s.isEmpty() ? BookIcon.sprite(fallback) : new BookIcon(s, null);
    }

    // ------------------------------------------------------------------ grids

    /** Species grid: open species, "Species found: N" (no "of N", §3.7). */
    public static List<LaidOutPage> speciesGrid(List<JournalData.Entry> entries, Consumer<String> openRecord) {
        List<GridPageView.Cell> cells = new ArrayList<>();
        for (JournalData.Entry e : entries) {
            if (kind(e, "species")) {
                String rest = RecordBlocks.rest(e);
                cells.add(new GridPageView.Cell(icon(speciesStack(rest), "species"),
                        Component.translatable("wildroots.species." + rest), false, e.is(JournalData.NEW),
                        () -> openRecord.accept(e.id())));
            }
        }
        return GridPageView.pages(Component.translatable("wildroots.journal.ui.found_count", cells.size()), cells);
    }

    /** Cultivar collection: bred ones with picture and name, the rest as silhouettes with "???" (§3.7). */
    public static List<LaidOutPage> cultivarGrid(List<JournalData.Entry> entries, Consumer<String> openRecord) {
        List<GridPageView.Cell> cells = new ArrayList<>();
        int bred = 0;
        for (JournalData.Entry e : entries) {
            if (!kind(e, "cultivar")) {
                continue;
            }
            String rest = RecordBlocks.rest(e);
            boolean locked = e.is(JournalData.LOCKED);
            if (!locked) {
                bred++;
            }
            cells.add(new GridPageView.Cell(icon(cultivarStack(rest), "unknown"),
                    locked ? Component.translatable("wildroots.journal.ui.locked")
                            : Component.translatable("wildroots.cultivar." + rest),
                    locked, e.is(JournalData.NEW) && !locked, () -> openRecord.accept(e.id())));
        }
        return GridPageView.pages(Component.translatable("wildroots.journal.ui.collected_count", bred, cells.size()),
                cells);
    }

    // ------------------------------------------------------------------ one record

    /** The page(s) of one species or cultivar: its picture and name, then its v1 text; species add a link to the stages. */
    public static List<LaidOutPage> record(PageContext ctx, JournalData.Entry e) {
        RecordBlocks.Block b;
        ItemStack stack;
        if (kind(e, "species")) {
            b = RecordBlocks.species(e);
            stack = speciesStack(RecordBlocks.rest(e));
        } else if (kind(e, "cultivar")) {
            b = RecordBlocks.cultivar(e);
            stack = e.is(JournalData.LOCKED) ? ItemStack.EMPTY : cultivarStack(RecordBlocks.rest(e));
        } else {
            return List.of();
        }
        List<Paginator.Block<TextLayout.Line>> blocks = new ArrayList<>();
        List<Component> lines = b.lines();
        // the first line is the name: the picture shows it
        for (int i = 1; i < lines.size(); i++) {
            blocks.add(TextLayout.block(ctx.font(), lines.get(i), PageRect.HALF_WIDTH, false, e.id()));
        }
        Component name = lines.isEmpty() ? Component.empty() : lines.get(0);
        int textTop = 48;
        int capacity = (PageRect.HEIGHT - textTop) / PageRect.LINE_HEIGHT;
        List<List<TextLayout.Line>> pages = Paginator.paginate(blocks, capacity, blank());
        List<TextLayout.Line> first = pages.get(0);
        List<TextLayout.Line> rest = new ArrayList<>();
        pages.subList(1, pages.size()).forEach(rest::addAll);
        List<FormattedCharSequence> nameLines = ctx.font().split(name, PageRect.HALF_WIDTH);
        List<LaidOutPage> out = new ArrayList<>();
        out.add(new LaidOutPage(PageSpan.HALF, rect -> new RecordView(rect, stack, nameLines, first, textTop, ctx),
                List.of(e.id())));
        if (!rest.isEmpty()) {
            out.addAll(TextLayout.textPages(List.of(Paginator.Block.paragraph(rest)), ctx));
        }
        if (kind(e, "species")) {
            out.addAll(PageTypes.seeAlso(List.of("guide.stages"), ctx));
        }
        return out;
    }

    static TextLayout.Line blank() {
        return new TextLayout.Line(FormattedCharSequence.EMPTY, null, List.of());
    }

    static final class RecordView implements PageView {

        private final PageRect rect;
        private final List<FormattedCharSequence> name;
        private final PageWidgets.ItemWidget item;
        private final TextPageView text;
        private final List<AbstractWidget> widgets = new ArrayList<>();

        RecordView(PageRect rect, ItemStack stack, List<FormattedCharSequence> name, List<TextLayout.Line> lines,
                   int textTop, PageContext ctx) {
            this.rect = rect;
            this.name = name.subList(0, Math.min(1, name.size()));
            this.item = new PageWidgets.ItemWidget(rect.x() + (rect.width() - 32) / 2, rect.y(), 32, stack, ctx);
            this.text = new TextPageView(rect, lines, ctx, rect.y() + textTop);
            widgets.add(item);
            widgets.addAll(text.widgets());
        }

        @Override
        public PageRect rect() {
            return rect;
        }

        @Override
        public List<AbstractWidget> widgets() {
            return widgets;
        }

        @Override
        public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
            var font = Minecraft.getInstance().font;
            for (FormattedCharSequence l : name) {
                g.drawString(font, l, rect.x() + (rect.width() - font.width(l)) / 2, rect.y() + 35, JournalColors.INK,
                        false);
            }
            g.hLine(rect.x(), rect.right() - 1, rect.y() + 45, JournalColors.INK_FADED);
            text.render(g, mouseX, mouseY, partialTick);
        }
    }

    // ------------------------------------------------------------------ notes, lines, breeder

    /** The guide entry a hint's "More →" leads to (§4.3); per-line hints use their base id. */
    public static Optional<String> moreLink(GuideBook book, String hintId) {
        String rest = hintId.substring("hint.".length());
        String base = rest.contains(".") ? rest.substring(0, rest.indexOf('.')) : rest;
        return book.hintLink("hint." + base);
    }

    /** Notes several to a page (§3.12): pinned first, then newest; each with "More →" if its entry is open. */
    public static List<LaidOutPage> notes(PageContext ctx, List<JournalData.Entry> entries) {
        List<Paginator.Block<TextLayout.Line>> blocks = new ArrayList<>();
        String more = Component.translatable("wildroots.journal.ui.more").getString();
        boolean safeMore = !more.contains("|") && !more.contains("]]") && !more.contains("[[");
        for (RecordBlocks.Block b : RecordBlocks.blocks(RecordBlocks.Tab.NOTES, entries)) {
            String id = b.ids().get(0);
            List<Component> lines = b.lines();
            blocks.add(TextLayout.block(ctx.font(), lines.get(0), PageRect.HALF_WIDTH, true, id));
            for (int i = 1; i < lines.size(); i++) {
                blocks.add(TextLayout.block(ctx.font(), lines.get(i), PageRect.HALF_WIDTH, false, id));
            }
            Optional<String> target = moreLink(ctx.unlocks().book(), id).filter(ctx.unlocks()::isOpen);
            if (target.isPresent() && safeMore) {
                blocks.add(TextLayout.block(ctx.font(), TextLayout.rich("[[" + target.get() + "|" + more + "]]",
                        ctx.unlocks()::isOpen), PageRect.HALF_WIDTH, false, id));
            }
        }
        return textOrEmpty(ctx, blocks);
    }

    /** Lines (M1a, §3.5): each bred cultivar with its line rows and path, as the server sends them. */
    public static List<LaidOutPage> lines(PageContext ctx, List<JournalData.Entry> entries) {
        List<Paginator.Block<TextLayout.Line>> blocks = new ArrayList<>();
        for (JournalData.Entry e : entries) {
            if (!kind(e, "cultivar") || e.is(JournalData.LOCKED)) {
                continue;
            }
            RecordBlocks.Block line = RecordBlocks.line(e);
            RecordBlocks.Block cv = RecordBlocks.cultivar(e);
            blocks.add(TextLayout.block(ctx.font(), line.lines().get(0), PageRect.HALF_WIDTH, true, null));
            for (int i = 1; i < line.lines().size(); i++) {
                blocks.add(TextLayout.block(ctx.font(), line.lines().get(i), PageRect.HALF_WIDTH, false, null));
            }
            cv.lines().stream().filter(c -> c.getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents t
                    && t.getKey().equals("wildroots.journal.cultivar.ancestry")).forEach(c -> blocks.add(
                    TextLayout.block(ctx.font(), c, PageRect.HALF_WIDTH, false, null)));
        }
        return textOrEmpty(ctx, blocks);
    }

    public static List<LaidOutPage> breeder(PageContext ctx, List<JournalData.Entry> entries) {
        List<Paginator.Block<TextLayout.Line>> blocks = new ArrayList<>();
        for (RecordBlocks.Block b : RecordBlocks.blocks(RecordBlocks.Tab.BREEDER, entries)) {
            String id = b.ids().isEmpty() ? null : b.ids().get(0);
            for (int i = 0; i < b.lines().size(); i++) {
                blocks.add(TextLayout.block(ctx.font(), b.lines().get(i), PageRect.HALF_WIDTH, i == 0, id));
            }
        }
        return textOrEmpty(ctx, blocks);
    }

    static List<LaidOutPage> textOrEmpty(PageContext ctx, List<Paginator.Block<TextLayout.Line>> blocks) {
        if (blocks.isEmpty()) {
            blocks.add(TextLayout.block(ctx.font(), Component.translatable("wildroots.journal.ui.empty"),
                    PageRect.HALF_WIDTH, false, null));
        }
        return TextLayout.textPages(blocks, ctx);
    }
}
