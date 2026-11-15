package io.github.slugblue.wildroots.client.journal.page;

import io.github.slugblue.wildroots.client.journal.GuidePage;
import io.github.slugblue.wildroots.client.journal.JournalColors;
import io.github.slugblue.wildroots.client.journal.book.BookIcon;
import io.github.slugblue.wildroots.client.journal.book.BookWidgets;
import io.github.slugblue.wildroots.client.journal.book.JournalAtlas;
import io.github.slugblue.wildroots.client.journal.book.Paginator;
import io.github.slugblue.wildroots.item.ProduceStacks;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * The guide page types of WR-181c (journal v2 §2.4, §3.1, §3.2):
 * {@code text} with links, {@code spotlight}, {@code crafting},
 * {@code links}. Registered by {@link PageTypes}.
 */
@OnlyIn(Dist.CLIENT)
final class GuidePageTypes {

    /** Most text lines under a spotlight item or a recipe before the rest goes to a text page (§3.1). */
    static final int SPOTLIGHT_LINES = 8;
    static final int CRAFTING_LINES = 5;

    private GuidePageTypes() {
    }

    // ------------------------------------------------------------------ text

    static List<LaidOutPage> text(TextPage data, GuidePage page, PageContext ctx) {
        List<Paginator.Block<TextLayout.Line>> blocks = new ArrayList<>();
        data.title().ifPresent(t -> blocks.add(TextLayout.block(ctx.font(),
                TextLayout.heading(Component.translatable(t)), PageRect.HALF_WIDTH, true, null)));
        blocks.addAll(TextLayout.guideParagraphs(ctx.font(), data.text(), PageRect.HALF_WIDTH, ctx.unlocks()::isOpen));
        return TextLayout.textPages(blocks, ctx);
    }

    /** Lines of {@code key} as one flat list (blank between paragraphs), for a page part under a picture. */
    static List<TextLayout.Line> flatLines(PageContext ctx, String key) {
        List<TextLayout.Line> out = new ArrayList<>();
        for (Paginator.Block<TextLayout.Line> b : TextLayout.guideParagraphs(ctx.font(), key, PageRect.HALF_WIDTH,
                ctx.unlocks()::isOpen)) {
            if (!out.isEmpty()) {
                out.add(TextLayout.Line.BLANK);
            }
            out.addAll(b.lines());
        }
        return out;
    }

    /** The first {@code max} lines stay on the picture page; the rest become text pages. */
    static List<LaidOutPage> withOverflow(LaidOutPage first, List<TextLayout.Line> rest, PageContext ctx) {
        List<LaidOutPage> out = new ArrayList<>();
        out.add(first);
        while (!rest.isEmpty() && rest.get(0) == TextLayout.Line.BLANK) {
            rest = rest.subList(1, rest.size());
        }
        if (!rest.isEmpty()) {
            out.addAll(TextLayout.textPages(List.of(Paginator.Block.paragraph(rest)), ctx));
        }
        return out;
    }

    // ------------------------------------------------------------------ spotlight

    /** The stack a spotlight shows: the item, or a display variant built here and never sent anywhere (§3.1). */
    static ItemStack displayStack(ResourceLocation id, Optional<String> variant) {
        Item item = ForgeRegistries.ITEMS.getValue(id);
        if (item == null || item == Items.AIR) {
            return ItemStack.EMPTY;
        }
        if (variant.isPresent()) {
            int colour = variant.get().equals("colour=purple") ? ProduceStacks.PURPLE
                    : variant.get().equals("colour=red") ? ProduceStacks.RED : ProduceStacks.YELLOW;
            return ProduceStacks.create(item, 1, false, colour, 1);
        }
        return new ItemStack(item);
    }

    /** "You have it": the item is in the inventory, or the journal holds {@code tool.<path>}. */
    static boolean owned(ResourceLocation id, PageContext ctx) {
        if (ctx.unlocks().has("tool." + id.getPath())) {
            return true;
        }
        var player = Minecraft.getInstance().player;
        Item item = ForgeRegistries.ITEMS.getValue(id);
        return player != null && item != null && player.getInventory().hasAnyMatching(s -> s.is(item));
    }

    static List<LaidOutPage> spotlight(SpotlightPage data, GuidePage page, PageContext ctx) {
        ItemStack stack = displayStack(data.item(), data.variant());
        if (stack.isEmpty()) {
            return List.of();
        }
        Font font = ctx.font();
        boolean owned = data.ownedBadge() && owned(data.item(), ctx);
        Optional<FormattedCharSequence> title = data.title().map(t -> TextLayout.heading(Component.translatable(t))
                .getVisualOrderText());
        int top = title.isPresent() ? 12 : 2;
        int nameY = top + 34;
        int ruleY = nameY + (owned ? 20 : 11);
        int textY = ruleY + 4;
        int capacity = Math.min(SPOTLIGHT_LINES, (PageRect.HEIGHT - textY) / PageRect.LINE_HEIGHT);
        List<TextLayout.Line> lines = data.text().map(k -> flatLines(ctx, k)).orElse(List.of());
        List<TextLayout.Line> here = lines.subList(0, Math.min(capacity, lines.size()));
        List<TextLayout.Line> rest = lines.subList(here.size(), lines.size());
        FormattedCharSequence name = stack.getHoverName().getVisualOrderText();
        LaidOutPage first = LaidOutPage.half(rect -> new SpotlightView(rect, stack, title, name, font.width(name),
                owned, top, nameY, ruleY, textY, here, ctx));
        return withOverflow(first, rest, ctx);
    }

    static final class SpotlightView implements PageView {

        private final PageRect rect;
        private final Optional<FormattedCharSequence> title;
        private final FormattedCharSequence name;
        private final int nameWidth;
        private final boolean owned;
        private final int nameY;
        private final int ruleY;
        private final TextPageView text;
        private final PageWidgets.ItemWidget item;
        private final List<AbstractWidget> widgets = new ArrayList<>();

        SpotlightView(PageRect rect, ItemStack stack, Optional<FormattedCharSequence> title, FormattedCharSequence name,
                      int nameWidth, boolean owned, int top, int nameY, int ruleY, int textY,
                      List<TextLayout.Line> lines, PageContext ctx) {
            this.rect = rect;
            this.title = title;
            this.name = name;
            this.nameWidth = nameWidth;
            this.owned = owned;
            this.nameY = rect.y() + nameY;
            this.ruleY = rect.y() + ruleY;
            this.item = new PageWidgets.ItemWidget(rect.x() + (rect.width() - 32) / 2, rect.y() + top, 32, stack, ctx);
            this.text = new TextPageView(rect, lines, ctx, rect.y() + textY);
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
            Font font = Minecraft.getInstance().font;
            title.ifPresent(t -> g.drawString(font, t, rect.x(), rect.y(), JournalColors.INK, false));
            g.drawString(font, name, rect.x() + (rect.width() - nameWidth) / 2, nameY, JournalColors.INK, false);
            if (owned) {
                g.drawString(font, Component.literal("✓ ").append(Component.translatable("wildroots.journal.ui.owned")),
                        rect.x() + 4, nameY + 10, JournalColors.NEW, false);
            }
            g.hLine(rect.x(), rect.right() - 1, ruleY, JournalColors.INK_FADED);
            text.render(g, mouseX, mouseY, partialTick);
        }
    }

    // ------------------------------------------------------------------ crafting

    static Optional<? extends Recipe<?>> recipe(ResourceLocation id) {
        var level = Minecraft.getInstance().level;
        return level == null ? Optional.empty() : level.getRecipeManager().byKey(id);
    }

    /** Whether a crafting page can be drawn: the client has the recipe (§3.2). */
    static boolean recipeExists(ResourceLocation id) {
        return recipe(id).isPresent();
    }

    static final int SLOT = 18;

    static List<LaidOutPage> crafting(CraftingPage data, GuidePage page, PageContext ctx) {
        Optional<? extends Recipe<?>> r = recipe(data.recipe());
        var level = Minecraft.getInstance().level;
        if (r.isEmpty() || level == null) {
            return List.of();
        }
        Recipe<?> recipe = r.get();
        ItemStack result = recipe.getResultItem(level.registryAccess());
        List<List<ItemStack>> grid = new ArrayList<>();
        for (int i = 0; i < 9; i++) {
            grid.add(List.of());
        }
        List<Ingredient> ings = recipe.getIngredients();
        int w = recipe instanceof ShapedRecipe s ? s.getWidth() : 3;
        for (int i = 0; i < ings.size() && i < 9; i++) {
            int col = i % w;
            int row = i / w;
            if (col < 3 && row < 3) {
                grid.set(row * 3 + col, Arrays.asList(ings.get(i).getItems()));
            }
        }
        Component station = recipe.getType() == RecipeType.CRAFTING
                ? Component.translatable("block.minecraft.crafting_table") : Component.empty();
        FormattedCharSequence title = TextLayout.heading(result.getHoverName()).getVisualOrderText();
        int gridY = 12;
        int stationY = gridY + 3 * SLOT + 3;
        int ruleY = stationY + 11;
        int textY = ruleY + 4;
        int capacity = Math.min(CRAFTING_LINES, (PageRect.HEIGHT - textY) / PageRect.LINE_HEIGHT);
        List<TextLayout.Line> lines = data.text().map(k -> flatLines(ctx, k)).orElse(List.of());
        List<TextLayout.Line> here = lines.subList(0, Math.min(capacity, lines.size()));
        List<TextLayout.Line> rest = lines.subList(here.size(), lines.size());
        LaidOutPage first = LaidOutPage.half(rect -> new CraftingView(rect, title, grid, result, station.getVisualOrderText(),
                gridY, stationY, ruleY, textY, here, ctx));
        return withOverflow(first, rest, ctx);
    }

    static final class CraftingView implements PageView {

        private final PageRect rect;
        private final FormattedCharSequence title;
        private final FormattedCharSequence station;
        private final int gridX;
        private final int gridY;
        private final int stationY;
        private final int ruleY;
        private final List<PageWidgets.ItemWidget> slots = new ArrayList<>();
        private final PageWidgets.ItemWidget result;
        private final TextPageView text;
        private final List<AbstractWidget> widgets = new ArrayList<>();
        private int ticks;

        CraftingView(PageRect rect, FormattedCharSequence title, List<List<ItemStack>> grid, ItemStack result,
                     FormattedCharSequence station, int gridY, int stationY, int ruleY, int textY,
                     List<TextLayout.Line> lines, PageContext ctx) {
            this.rect = rect;
            this.title = title;
            this.station = station;
            this.gridX = rect.x() + 6;
            this.gridY = rect.y() + gridY;
            this.stationY = rect.y() + stationY;
            this.ruleY = rect.y() + ruleY;
            for (int i = 0; i < 9; i++) {
                PageWidgets.ItemWidget w = new PageWidgets.ItemWidget(gridX + (i % 3) * SLOT + 1,
                        this.gridY + (i / 3) * SLOT + 1, 16, grid.get(i), ctx);
                slots.add(w);
            }
            this.result = new PageWidgets.ItemWidget(gridX + 3 * SLOT + 30, this.gridY + SLOT + 1, 16, result, ctx);
            this.text = new TextPageView(rect, lines, ctx, rect.y() + textY);
            widgets.addAll(slots);
            widgets.add(this.result);
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
        public void tick() {
            ticks++;
            if (ticks % 20 == 0) {
                for (PageWidgets.ItemWidget s : slots) {
                    s.cycle(ticks / 20);
                }
            }
        }

        @Override
        public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
            Font font = Minecraft.getInstance().font;
            g.drawString(font, title, rect.x(), rect.y(), JournalColors.INK, false);
            for (int i = 0; i < 9; i++) {
                int x = gridX + (i % 3) * SLOT;
                int y = gridY + (i / 3) * SLOT;
                g.renderOutline(x, y, SLOT, SLOT, JournalColors.INK_FADED);
            }
            int ax = gridX + 3 * SLOT + 10;
            JournalAtlas.blit(g, JournalAtlas.Arrow.RIGHT.sprite(), ax, gridY + SLOT + 5);
            g.renderOutline(result.getX() - 1, result.getY() - 1, SLOT, SLOT, JournalColors.INK);
            g.drawString(font, station, gridX, stationY, JournalColors.INK_FADED, false);
            g.hLine(rect.x(), rect.right() - 1, ruleY, JournalColors.INK_FADED);
            text.render(g, mouseX, mouseY, partialTick);
        }
    }

    // ------------------------------------------------------------------ links

    /** Prefix of a link to a records category ({@code records.cultivars}) instead of a guide entry. */
    static final String RECORDS = "records.";

    static List<LaidOutPage> links(LinksPage data, GuidePage page, PageContext ctx) {
        return linkRows(data.title().orElse("wildroots.journal.ui.see_also"), data.links(), ctx);
    }

    /** A half page: a heading, then one row per open target; nothing open → no page. */
    static List<LaidOutPage> linkRows(String titleKey, List<String> targets, PageContext ctx) {
        List<String> open = targets.stream().filter(t -> t.startsWith(RECORDS) || ctx.unlocks().isOpen(t)).toList();
        if (open.isEmpty()) {
            return List.of();
        }
        List<FormattedCharSequence> heading = ctx.font().split(TextLayout.rich(Component.translatable(titleKey)
                .getString(), ctx.unlocks()::isOpen), PageRect.HALF_WIDTH);
        return List.of(LaidOutPage.half(rect -> new LinksView(rect, heading, open, ctx)));
    }

    static final class LinksView implements PageView {

        private final PageRect rect;
        private final List<FormattedCharSequence> heading;
        private final List<BookWidgets.RowButton> rows = new ArrayList<>();

        LinksView(PageRect rect, List<FormattedCharSequence> heading, List<String> targets, PageContext ctx) {
            this.rect = rect;
            this.heading = heading;
            int y = rect.y() + heading.size() * PageRect.LINE_HEIGHT + 4;
            for (String t : targets) {
                if (y + BookWidgets.RowButton.HEIGHT > rect.bottom()) {
                    break;
                }
                BookIcon icon;
                Component label;
                if (t.startsWith(RECORDS)) {
                    icon = BookIcon.sprite(t.substring(RECORDS.length()));
                    label = Component.translatable("wildroots.journal.tab." + t.substring(RECORDS.length()));
                } else {
                    var e = ctx.unlocks().book().entry(t).orElseThrow();
                    icon = BookIcon.of(e.icon());
                    label = Component.translatable(e.titleKey());
                }
                rows.add(new BookWidgets.RowButton(rect.x(), y, rect.width(), icon, label, Component.empty(), false,
                        false, () -> ctx.open(t)));
                y += BookWidgets.RowButton.HEIGHT;
            }
        }

        @Override
        public PageRect rect() {
            return rect;
        }

        @Override
        public List<BookWidgets.RowButton> widgets() {
            return rows;
        }

        @Override
        public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
            Font font = Minecraft.getInstance().font;
            int y = rect.y();
            for (FormattedCharSequence l : heading) {
                g.drawString(font, l, rect.x(), y, JournalColors.INK, false);
                y += PageRect.LINE_HEIGHT;
            }
        }
    }
}
