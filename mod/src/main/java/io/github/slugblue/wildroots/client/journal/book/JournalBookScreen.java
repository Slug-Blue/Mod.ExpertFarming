package io.github.slugblue.wildroots.client.journal.book;

import com.mojang.logging.LogUtils;
import io.github.slugblue.wildroots.client.ClientJournal;
import io.github.slugblue.wildroots.client.journal.GuideBooks;
import io.github.slugblue.wildroots.client.journal.GuideCategory;
import io.github.slugblue.wildroots.client.journal.GuideEntry;
import io.github.slugblue.wildroots.client.journal.GuidePage;
import io.github.slugblue.wildroots.client.journal.GuideUnlocks;
import io.github.slugblue.wildroots.client.journal.JournalColors;
import io.github.slugblue.wildroots.client.journal.page.LaidOutPage;
import io.github.slugblue.wildroots.client.journal.page.PageContext;
import io.github.slugblue.wildroots.client.journal.page.PageRect;
import io.github.slugblue.wildroots.client.journal.page.PageTypes;
import io.github.slugblue.wildroots.client.journal.page.PageView;
import io.github.slugblue.wildroots.core.Ids;
import io.github.slugblue.wildroots.core.config.ClientConfig;
import io.github.slugblue.wildroots.core.safety.LogThrottle;
import io.github.slugblue.wildroots.journal.JournalData;
import io.github.slugblue.wildroots.network.WrNetwork;
import io.github.slugblue.wildroots.network.c2s.JournalActionPacket;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;

/**
 * The field journal v2 (journal v2 §3.0, §3.8, §3.11; WR-181b): one
 * spread with tabs on the left and a navigation strip under it. Shows the
 * contents, a guide category, a guide entry or a records category, a
 * spread at a time; history back/forward (32), every key of §3.11, Tab
 * focus, and the last page until the player leaves the world.
 *
 * <p>The layout (wrapping, pages, views) is built in {@link #rebuild()}:
 * on open, {@code JournalSync}, guide reload, window resize, language
 * change, and when another entry is opened. A frame only draws.
 */
@OnlyIn(Dist.CLIENT)
public final class JournalBookScreen extends Screen implements PageContext {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final LogThrottle WARN = new LogThrottle(System::currentTimeMillis, 5 * 60 * 1000L);
    /** Layout builds since the game started (debug overlay, §5.3 "not per frame"). */
    static int layoutBuilds;

    static {
        GuideBooks.addReloadListener(JournalBookScreen::refreshIfOpen);
    }

    /** One spread: its views and the journal entries its pages show. */
    record Spread(List<PageView> views, List<String> readIds) {
    }

    private BookGeometry geo;
    private BookLocation location;
    private GuideUnlocks unlocks;
    private List<JournalData.Entry> entries = List.of();
    private List<Spread> spreads = List.of();
    private int shown = -1;
    private FormattedCharSequence titleLine = FormattedCharSequence.EMPTY;
    private Component pageNumber = Component.empty();
    private final Set<String> markedRead = new HashSet<>();
    private PageView focusedView;
    private PageView dragView;
    private int dragButton;
    private ItemStack pendingItemTooltip = ItemStack.EMPTY;

    public JournalBookScreen() {
        super(Component.translatable("item.wildroots.field_journal"));
    }

    /** Opens the journal, or rebuilds it if it is open (a {@code JournalSync}). */
    public static void openOrRefresh() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof JournalBookScreen s) {
            s.rebuild();
        } else {
            mc.setScreen(new JournalBookScreen());
        }
    }

    /** Rebuilds an open journal (a sync without {@code open}, or a guide reload). */
    public static void refreshIfOpen() {
        if (Minecraft.getInstance().screen instanceof JournalBookScreen s) {
            s.rebuild();
        }
    }

    static Set<String> newIds(List<JournalData.Entry> entries) {
        return entries.stream().filter(e -> e.is(JournalData.NEW)).map(JournalData.Entry::id)
                .collect(Collectors.toSet());
    }

    // ------------------------------------------------------------------ layout

    @Override
    protected void init() {
        geo = BookGeometry.center(width, height);
        if (location == null) {
            location = JournalSession.openAt(newIds(ClientJournal.entries()));
        }
        rebuild();
    }

    /** Builds the whole layout of the current location and shows its spread. */
    public void rebuild() {
        if (geo == null) {
            return;
        }
        hideShown();
        entries = ClientJournal.entries();
        unlocks = new GuideUnlocks(GuideBooks.current(), entries, ClientConfig.get(ClientConfig.SHOW_ALL_GUIDES));
        location = valid(location);
        List<LaidOutPage> pages = build(location);
        List<Spreads.Slots<LaidOutPage>> slots = Spreads.assemble(pages, LaidOutPage::span);
        List<Spread> out = new ArrayList<>();
        for (Spreads.Slots<LaidOutPage> s : slots) {
            List<PageView> views = new ArrayList<>();
            List<String> ids = new ArrayList<>();
            place(s.left(), geo.left(), views, ids);
            place(s.right(), geo.right(), views, ids);
            place(s.whole(), geo.spread(), views, ids);
            out.add(new Spread(views, ids));
        }
        if (out.isEmpty()) {
            out.add(new Spread(List.of(), List.of()));
        }
        spreads = out;
        int maxTitle = PageRect.HALF_WIDTH - 14 - (location.kind() == BookLocation.Kind.CONTENTS ? 0 : 14);
        titleLine = fit(titleOf(location), maxTitle);
        layoutBuilds++;
        location = location.withSpread(Math.min(location.spread(), spreads.size() - 1));
        showSpread(location.spread());
    }

    private void place(LaidOutPage p, PageRect rect, List<PageView> views, List<String> ids) {
        if (p == null) {
            return;
        }
        try {
            views.add(p.view().apply(rect));
            ids.addAll(p.readIds());
        } catch (RuntimeException e) {
            warn("a page view could not be created", e);
        }
    }

    private BookLocation valid(BookLocation l) {
        boolean ok = switch (l.kind()) {
            case CONTENTS -> true;
            case CATEGORY -> unlocks.book().category(l.id()).map(unlocks::isOpen).orElse(false);
            case ENTRY -> unlocks.isOpen(l.id());
            case RECORDS -> RecordsCategory.byName(l.id()).map(rc -> rc.visible(entries)).orElse(false);
        };
        return ok ? l : BookLocation.CONTENTS;
    }

    private List<LaidOutPage> build(BookLocation l) {
        return switch (l.kind()) {
            case CONTENTS -> BookPages.contents(unlocks, entries, this::jump);
            case CATEGORY -> BookPages.category(unlocks, unlocks.book().category(l.id()).orElseThrow(), this::jump);
            case RECORDS -> BookPages.records(font, RecordsCategory.byName(l.id()).orElseThrow(), entries);
            case ENTRY -> entryPages(unlocks.book().entry(l.id()).orElseThrow());
        };
    }

    private List<LaidOutPage> entryPages(GuideEntry e) {
        List<LaidOutPage> out = new ArrayList<>();
        for (GuidePage p : unlocks.pages(e)) {
            try {
                out.addAll(PageTypes.layout(p, this));
            } catch (RuntimeException ex) {
                warn("page " + p.type() + " of " + e.id() + " could not be laid out", ex);
            }
        }
        return out;
    }

    private Component titleOf(BookLocation l) {
        return switch (l.kind()) {
            case CONTENTS -> Component.translatable("wildroots.journal.title_page", ClientJournal.owner());
            case CATEGORY -> unlocks.book().category(l.id()).map(c -> Component.translatable(c.titleKey()))
                    .orElse(Component.empty());
            case ENTRY -> unlocks.book().entry(l.id()).map(e -> Component.translatable(e.titleKey()))
                    .orElse(Component.empty());
            case RECORDS -> RecordsCategory.byName(l.id()).map(rc -> Component.translatable(rc.titleKey()))
                    .orElse(Component.empty());
        };
    }

    private void warn(String what, Throwable e) {
        if (WARN.allow(what)) {
            LOGGER.warn("Wild Roots journal: {}: {}", what, e.toString());
        }
    }

    // ------------------------------------------------------------------ spreads and navigation

    private void hideShown() {
        if (shown >= 0 && shown < spreads.size()) {
            for (PageView v : spreads.get(shown).views()) {
                try {
                    v.hidden();
                } catch (RuntimeException e) {
                    warn("a page view failed to hide", e);
                }
            }
        }
        shown = -1;
        focusedView = null;
        dragView = null;
    }

    private void showSpread(int i) {
        hideShown();
        shown = i;
        location = location.withSpread(i);
        clearWidgets();
        addChrome();
        Spread s = spreads.get(i);
        for (PageView v : s.views()) {
            v.widgets().forEach(this::addRenderableWidget);
            try {
                v.shown();
            } catch (RuntimeException e) {
                warn("a page view failed to show", e);
            }
        }
        pageNumber = spreads.size() > 1
                ? Component.translatable("wildroots.journal.ui.page", i + 1, spreads.size()) : Component.empty();
        markRead(s);
    }

    private void addChrome() {
        // tabs: open guide categories, then records categories, at most 8
        int t = 0;
        String selectedCategory = switch (location.kind()) {
            case CATEGORY -> location.id();
            case ENTRY -> unlocks.book().entry(location.id()).map(GuideEntry::category).orElse("");
            default -> "";
        };
        for (GuideCategory c : unlocks.openCategories()) {
            if (t >= BookGeometry.MAX_TABS) {
                break;
            }
            addRenderableWidget(new BookWidgets.TabButton(geo.tabX(), geo.tabY(t++), BookIcon.of(c.icon()),
                    Component.translatable(c.titleKey()), c.id().equals(selectedCategory), unlocks.isNew(c),
                    () -> jump(BookLocation.category(c.id()))));
        }
        for (RecordsCategory rc : RecordsCategory.values()) {
            if (t >= BookGeometry.MAX_TABS) {
                break;
            }
            if (rc.visible(entries)) {
                boolean sel = location.kind() == BookLocation.Kind.RECORDS && location.id().equals(rc.name());
                addRenderableWidget(new BookWidgets.TabButton(geo.tabX(), geo.tabY(t++), BookIcon.item(rc.icon),
                        Component.translatable(rc.titleKey()), sel, rc.hasNew(entries),
                        () -> jump(BookLocation.records(rc))));
            }
        }
        if (location.kind() != BookLocation.Kind.CONTENTS) {
            addRenderableWidget(new BookWidgets.SpriteButton(geo.x() + BookGeometry.INSET - 4, geo.headerY() - 4,
                    JournalAtlas.Button.UP, Component.translatable("wildroots.journal.ui.up"), true, this::up));
        }
        int ny = geo.navY();
        int x = geo.x();
        int w = BookGeometry.SPREAD_W;
        addRenderableWidget(new BookWidgets.SpriteButton(x + 4, ny, JournalAtlas.Button.BACK,
                Component.translatable("wildroots.journal.ui.back"), JournalSession.HISTORY.canGoBack(), this::back));
        addRenderableWidget(new BookWidgets.SpriteButton(x + 24, ny, JournalAtlas.Button.PREV,
                Component.translatable("wildroots.journal.ui.page_prev"), shown > 0, () -> turn(-1)));
        addRenderableWidget(new BookWidgets.SpriteButton(x + 44, ny, JournalAtlas.Button.HOME,
                Component.translatable("wildroots.journal.ui.contents"), location.kind() != BookLocation.Kind.CONTENTS,
                this::home));
        addRenderableWidget(new BookWidgets.SpriteButton(x + w - 40, ny, JournalAtlas.Button.NEXT,
                Component.translatable("wildroots.journal.ui.page_next"), shown < spreads.size() - 1, () -> turn(1)));
        addRenderableWidget(new BookWidgets.SpriteButton(x + w - 20, ny, JournalAtlas.Button.FORWARD,
                Component.translatable("wildroots.journal.ui.forward"), JournalSession.HISTORY.canGoForward(),
                this::forward));
    }

    /** Opens another place, pushing the current one to history. */
    void jump(BookLocation target) {
        if (target.sameTarget(location) && target.spread() == location.spread()) {
            return;
        }
        JournalSession.HISTORY.push(location);
        location = target;
        rebuild();
    }

    void turn(int d) {
        int next = shown + d;
        if (next >= 0 && next < spreads.size()) {
            showSpread(next);
        }
    }

    void back() {
        JournalSession.HISTORY.back(location).ifPresent(l -> {
            location = l;
            rebuild();
        });
    }

    void forward() {
        JournalSession.HISTORY.forward(location).ifPresent(l -> {
            location = l;
            rebuild();
        });
    }

    void home() {
        jump(BookLocation.CONTENTS);
    }

    /** To the parent: entry → its category, anything else → contents. */
    void up() {
        if (location.kind() == BookLocation.Kind.ENTRY) {
            Optional<GuideEntry> e = unlocks.book().entry(location.id());
            if (e.isPresent()) {
                jump(BookLocation.category(e.get().category()));
                return;
            }
        }
        home();
    }

    /** MARK_READ for new entries the spread shows, once per screen (§2.3, §5.1). */
    private void markRead(Spread s) {
        List<String> ids = new ArrayList<>(s.readIds());
        if (location.kind() == BookLocation.Kind.ENTRY) {
            unlocks.book().entry(location.id()).ifPresent(e -> ids.addAll(unlocks.newOpeners(e)));
        }
        Set<String> isNew = newIds(entries);
        for (String id : ids) {
            if (isNew.contains(id) && markedRead.add(id)) {
                WrNetwork.CHANNEL.sendToServer(new JournalActionPacket(Ids.mod(id), JournalActionPacket.MARK_READ));
            }
        }
    }

    private List<PageView> visible() {
        return shown >= 0 && shown < spreads.size() ? spreads.get(shown).views() : List.of();
    }

    // ------------------------------------------------------------------ frame

    @Override
    public void tick() {
        for (PageView v : visible()) {
            try {
                v.tick();
            } catch (RuntimeException e) {
                warn("a page view failed to tick", e);
            }
        }
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g);
        JournalAtlas.blit(g, JournalAtlas.SPREAD, geo.x(), geo.y());
        JournalAtlas.blit(g, JournalAtlas.NAV, geo.x(), geo.navY());
        int tx = geo.x() + BookGeometry.INSET + (location.kind() == BookLocation.Kind.CONTENTS ? 0 : 14);
        g.drawString(font, titleLine, tx, geo.headerY(), JournalColors.INK, false);
        if (spreads.size() > 1) {
            g.drawString(font, pageNumber, geo.right().right() - font.width(pageNumber), geo.headerY(),
                    JournalColors.INK_FADED, false);
        }
        pendingItemTooltip = ItemStack.EMPTY;
        for (PageView v : visible()) {
            try {
                v.render(g, mouseX, mouseY, partialTick);
            } catch (RuntimeException e) {
                warn("a page view failed to draw", e);
            }
        }
        super.render(g, mouseX, mouseY, partialTick);
        if (!pendingItemTooltip.isEmpty()) {
            g.renderTooltip(font, pendingItemTooltip, mouseX, mouseY);
        }
        if (debug()) {
            g.drawString(font, "journal: layouts " + layoutBuilds + ", spread " + (shown + 1) + "/" + spreads.size()
                    + ", views " + visible().size(), 2, 2, 0xFFFFFFFF, true);
        }
    }

    /** {@code c} cut with an ellipsis to {@code max} px. */
    private FormattedCharSequence fit(Component c, int max) {
        if (font.width(c) <= max) {
            return c.getVisualOrderText();
        }
        FormattedText cut = font.substrByWidth(c, Math.max(0, max - font.width("…")));
        return net.minecraft.locale.Language.getInstance().getVisualOrder(FormattedText.composite(cut,
                FormattedText.of("…")));
    }

    @Override
    public void removed() {
        hideShown();
        JournalSession.closed(location == null ? BookLocation.CONTENTS : location, newIds(ClientJournal.entries()));
        super.removed();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    // ------------------------------------------------------------------ input

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (key == GLFW.GLFW_KEY_ESCAPE) {
            onClose();
            return true;
        }
        if (focusedView != null && focusedView.keyPressed(key, scan, mods, true)) {
            return true;
        }
        boolean enter = key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER || key == GLFW.GLFW_KEY_SPACE;
        if (enter && getFocused() != null && super.keyPressed(key, scan, mods)) {
            dropStaleFocus();
            return true;
        }
        if (bookKey(key, scan, mods)) {
            return true;
        }
        for (PageView v : visible()) {
            if (v != focusedView && v.keyPressed(key, scan, mods, false)) {
                return true;
            }
        }
        return super.keyPressed(key, scan, mods);
    }

    /**
     * A button that opened another page was replaced by the new layout, but
     * the screen still holds it as focused: focus its twin (the same
     * navigation button) so Enter keeps working from the keyboard, or
     * nothing.
     */
    private void dropStaleFocus() {
        if (getFocused() == null || children().contains(getFocused())) {
            return;
        }
        GuiEventListener stale = getFocused();
        setFocused(null);
        if (stale instanceof BookWidgets.SpriteButton old) {
            for (GuiEventListener l : children()) {
                if (l instanceof BookWidgets.SpriteButton b && b.sprite() == old.sprite() && b.active) {
                    setFocused(b);
                    return;
                }
            }
        }
    }

    /** The keys of §3.11 that belong to the book. */
    private boolean bookKey(int key, int scan, int mods) {
        boolean alt = (mods & GLFW.GLFW_MOD_ALT) != 0;
        boolean ctrl = (mods & GLFW.GLFW_MOD_CONTROL) != 0;
        if (alt && key == GLFW.GLFW_KEY_LEFT || key == GLFW.GLFW_KEY_BACKSPACE) {
            back();
            return true;
        }
        if (alt && key == GLFW.GLFW_KEY_RIGHT) {
            forward();
            return true;
        }
        if (ctrl) {
            return false;
        }
        switch (key) {
            case GLFW.GLFW_KEY_LEFT, GLFW.GLFW_KEY_PAGE_UP, GLFW.GLFW_KEY_A -> {
                turn(-1);
                return true;
            }
            case GLFW.GLFW_KEY_RIGHT, GLFW.GLFW_KEY_PAGE_DOWN, GLFW.GLFW_KEY_D -> {
                turn(1);
                return true;
            }
            case GLFW.GLFW_KEY_HOME -> {
                home();
                return true;
            }
            default -> {
            }
        }
        if (minecraft != null && minecraft.options.keyInventory.matches(key, scan)) {
            onClose();
            return true;
        }
        return false;
    }

    @Override
    public boolean charTyped(char c, int mods) {
        if (focusedView != null && focusedView.charTyped(c, mods)) {
            return true;
        }
        return super.charTyped(c, mods);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button == GLFW.GLFW_MOUSE_BUTTON_4) {
            back();
            return true;
        }
        if (button == GLFW.GLFW_MOUSE_BUTTON_5) {
            forward();
            return true;
        }
        if (super.mouseClicked(mx, my, button)) {
            focusedView = null;
            dropStaleFocus();
            return true;
        }
        for (PageView v : visible()) {
            if (v.rect().contains(mx, my) && v.mouseClicked(mx, my, button)) {
                focusedView = v;
                dragView = v;
                dragButton = button;
                setFocused(null);
                return true;
            }
        }
        focusedView = null;
        return false;
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        if (dragView != null && button == dragButton) {
            PageView v = dragView;
            dragView = null;
            return v.mouseReleased(mx, my, button);
        }
        return super.mouseReleased(mx, my, button);
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (dragView != null && button == dragButton) {
            return dragView.mouseDragged(mx, my, button, dx, dy);
        }
        return super.mouseDragged(mx, my, button, dx, dy);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        for (PageView v : visible()) {
            if (v.rect().contains(mx, my) && v.mouseScrolled(mx, my, delta)) {
                return true;
            }
        }
        if (geo.overBook(mx, my) && delta != 0) {
            turn(delta > 0 ? -1 : 1);
            return true;
        }
        return super.mouseScrolled(mx, my, delta);
    }

    // ------------------------------------------------------------------ PageContext

    @Override
    public net.minecraft.client.gui.Font font() {
        return font;
    }

    @Override
    public GuideUnlocks unlocks() {
        return unlocks;
    }

    @Override
    public void open(String entryId) {
        if (unlocks.isOpen(entryId)) {
            jump(BookLocation.entry(entryId));
        }
    }

    @Override
    public boolean openItem(ResourceLocation item) {
        Optional<GuideEntry> e = unlocks.book().entriesForItem(item).stream().filter(unlocks::isOpen).findFirst();
        e.ifPresent(x -> jump(BookLocation.entry(x.id())));
        return e.isPresent();
    }

    @Override
    public boolean hasEntryFor(ResourceLocation item) {
        return unlocks.book().entriesForItem(item).stream().anyMatch(unlocks::isOpen);
    }

    @Override
    public void tooltip(List<Component> lines) {
        setTooltipForNextRenderPass(lines.stream().map(Component::getVisualOrderText).toList());
    }

    @Override
    public void itemTooltip(ItemStack stack) {
        pendingItemTooltip = stack;
    }

    @Override
    public boolean scenesEnabled() {
        return ClientConfig.get(ClientConfig.JOURNAL_SCENES);
    }

    @Override
    public boolean debug() {
        return ClientConfig.get(ClientConfig.DEBUG_JOURNAL);
    }
}
