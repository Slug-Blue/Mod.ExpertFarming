package io.github.slugblue.wildroots.client.journal.page;

import io.github.slugblue.wildroots.client.journal.GuidePage;
import io.github.slugblue.wildroots.client.journal.JournalColors;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * The client page types (journal v2 §3), by the {@code type} of the JSON
 * page. {@code text} is built in (WR-181b); {@code spotlight},
 * {@code crafting}, {@code links} come with WR-181c, {@code trait_legend}
 * with WR-181d, {@code scene} with the scene engine (WR-181k), which
 * registers it in client setup:
 * {@code PageTypes.register(ScenePage.TYPE, ScenePage.class, new ScenePageType())}.
 * A type with a parser but no client type yet shows a placeholder page.
 */
@OnlyIn(Dist.CLIENT)
public final class PageTypes {

    private record Registered<D extends PageData>(Class<D> data, PageType<D> type) {

        List<LaidOutPage> layout(PageData d, GuidePage page, PageContext ctx) {
            return type.layout(data.cast(d), page, ctx);
        }
    }

    private static final Map<String, Registered<?>> TYPES = new ConcurrentHashMap<>();

    static {
        register(TextPage.TYPE, TextPage.class, PageTypes::text);
    }

    private PageTypes() {
    }

    /** Adds or replaces the client type of a page type; client setup. */
    public static <D extends PageData> void register(String type, Class<D> data, PageType<D> pageType) {
        TYPES.put(type, new Registered<>(data, pageType));
    }

    /** Lays out a page by its type; no client type → one placeholder page of the right span. */
    public static List<LaidOutPage> layout(GuidePage page, PageContext ctx) {
        Registered<?> r = TYPES.get(page.type());
        if (r == null || !r.data().isInstance(page.data())) {
            PageSpan span = page.data() instanceof ScenePage || page.data() instanceof TraitLegendPage
                    ? PageSpan.SPREAD : PageSpan.HALF;
            return List.of(new LaidOutPage(span, rect -> new Placeholder(rect, page.type()), List.of()));
        }
        return r.layout(page.data(), page, ctx);
    }

    static List<LaidOutPage> text(TextPage data, GuidePage page, PageContext ctx) {
        List<io.github.slugblue.wildroots.client.journal.book.Paginator.Block<TextLayout.Line>> blocks =
                new ArrayList<>();
        data.title().ifPresent(t -> blocks.add(TextLayout.block(ctx.font(),
                TextLayout.heading(Component.translatable(t)), PageRect.HALF_WIDTH, true, null)));
        blocks.addAll(TextLayout.paragraphs(ctx.font(), Component.translatable(data.text()), PageRect.HALF_WIDTH, null));
        return TextLayout.textPages(blocks);
    }

    /** A page whose client type is not there yet: its type name in faded ink. */
    record Placeholder(PageRect rect, String type) implements PageView {

        @Override
        public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
            g.drawString(Minecraft.getInstance().font, "[" + type + "]", rect.x(), rect.y(), JournalColors.INK_FADED,
                    false);
        }
    }
}
