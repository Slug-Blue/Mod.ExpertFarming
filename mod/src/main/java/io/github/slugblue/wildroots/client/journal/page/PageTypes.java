package io.github.slugblue.wildroots.client.journal.page;

import io.github.slugblue.wildroots.client.journal.GuidePage;
import io.github.slugblue.wildroots.client.journal.JournalColors;
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
 * page. {@code text}, {@code spotlight}, {@code crafting}, {@code links}
 * are built in (WR-181b, WR-181c), {@code trait_legend}
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
        register(TextPage.TYPE, TextPage.class, GuidePageTypes::text);
        register(SpotlightPage.TYPE, SpotlightPage.class, GuidePageTypes::spotlight);
        register(CraftingPage.TYPE, CraftingPage.class, GuidePageTypes::crafting);
        register(LinksPage.TYPE, LinksPage.class, GuidePageTypes::links);
    }

    private PageTypes() {
    }

    /** Whether a page can be drawn on this client at all: a crafting page needs its recipe (§3.2). */
    public static boolean available(io.github.slugblue.wildroots.client.journal.GuidePage page) {
        return !(page.data() instanceof CraftingPage c) || GuidePageTypes.recipeExists(c.recipe());
    }

    /** The "See also" page at the end of an entry (§2.4): open targets only; none → no page. */
    public static List<LaidOutPage> seeAlso(List<String> targets, PageContext ctx) {
        return GuidePageTypes.linkRows("wildroots.journal.ui.see_also", targets, ctx);
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

    /** A page whose client type is not there yet: its type name in faded ink. */
    record Placeholder(PageRect rect, String type) implements PageView {

        @Override
        public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
            g.drawString(Minecraft.getInstance().font, "[" + type + "]", rect.x(), rect.y(), JournalColors.INK_FADED,
                    false);
        }
    }
}
