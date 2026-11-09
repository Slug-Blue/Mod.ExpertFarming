package io.github.slugblue.wildroots.client;

import io.github.slugblue.wildroots.core.Ids;
import io.github.slugblue.wildroots.journal.JournalData;
import io.github.slugblue.wildroots.network.WrNetwork;
import io.github.slugblue.wildroots.network.c2s.JournalActionPacket;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.BookViewScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * The field journal screen (narrative §4.2–4.4, §9.7; WR-181): a title
 * page, then the tabs that the entries allow, each paged like a book. It
 * draws only what {@link ClientJournal} holds, as the server sent it; the
 * only thing it sends back is {@code JournalAction} (mark read, pin).
 */
@OnlyIn(Dist.CLIENT)
public final class JournalScreen extends Screen {

    static final int BOOK_W = 192;
    static final int BOOK_H = 192;
    static final int TEXT_X = 36;
    static final int TEXT_Y = 30;
    static final int TEXT_W = 114;
    static final int LINES_PER_PAGE = 14;
    static final int TEXT_COLOR = 0x000000;

    /** {@code null} = the title page. */
    private JournalPages.Tab tab;
    private int page;
    private final Set<String> markedRead = new HashSet<>();
    private List<Page> pages = List.of();

    /** One page: wrapped lines plus the pinnable entry shown at its top, if any. */
    record Page(List<FormattedCharSequence> lines, List<String> ids, String pinnable, boolean pinned) {
    }

    public JournalScreen() {
        super(Component.translatable("item.wildroots.field_journal"));
    }

    /** Opens the screen, or refreshes it if it is already open. */
    public static void openOrRefresh() {
        var mc = net.minecraft.client.Minecraft.getInstance();
        if (mc.screen instanceof JournalScreen s) {
            s.rebuild();
        } else {
            mc.setScreen(new JournalScreen());
        }
    }

    @Override
    protected void init() {
        rebuild();
    }

    void rebuild() {
        clearWidgets();
        List<JournalData.Entry> entries = ClientJournal.entries();
        int left = (width - BOOK_W) / 2;
        int y = 4;
        addRenderableWidget(Button.builder(Component.translatable("item.wildroots.field_journal"), b -> show(null, 0))
                .bounds(left - 84, y, 80, 18).build());
        for (JournalPages.Tab t : JournalPages.Tab.values()) {
            if (!JournalPages.visible(t, entries)) {
                continue;
            }
            y += 20;
            Component label = Component.translatable(t.key);
            if (JournalPages.hasNew(t, entries)) {
                label = label.copy().append(" *");
            }
            JournalPages.Tab target = t;
            addRenderableWidget(Button.builder(label, b -> show(target, 0)).bounds(left - 84, y, 80, 18).build());
        }
        pages = paginate(entries);
        page = Math.max(0, Math.min(page, pages.size() - 1));
        int by = BOOK_H + 4;
        addRenderableWidget(Button.builder(Component.literal("<"), b -> turn(-1)).bounds(left + 40, by, 20, 20).build());
        addRenderableWidget(Button.builder(Component.literal(">"), b -> turn(1)).bounds(left + BOOK_W - 60, by, 20, 20).build());
        Page p = pages.isEmpty() ? null : pages.get(page);
        if (p != null && p.pinnable() != null) {
            String id = p.pinnable();
            boolean pinned = p.pinned();
            addRenderableWidget(Button.builder(Component.literal(pinned ? "-" : "+"),
                    b -> send(id, pinned ? JournalActionPacket.UNPIN : JournalActionPacket.PIN))
                    .bounds(left + BOOK_W / 2 - 10, by, 20, 20).build());
        }
        markShownRead();
    }

    void show(JournalPages.Tab t, int p) {
        tab = t;
        page = p;
        rebuild();
    }

    void turn(int d) {
        int next = page + d;
        if (next >= 0 && next < pages.size()) {
            page = next;
            rebuild();
        }
    }

    List<Page> paginate(List<JournalData.Entry> entries) {
        List<Page> out = new ArrayList<>();
        if (tab == null) {
            List<FormattedCharSequence> l = new ArrayList<>(font.split(
                    Component.translatable("wildroots.journal.title_page", ClientJournal.owner()), TEXT_W));
            out.add(new Page(l, List.of(), null, false));
            return out;
        }
        List<JournalPages.Block> blocks = JournalPages.blocks(tab, entries);
        if (blocks.isEmpty()) {
            out.add(new Page(font.split(Component.translatable("wildroots.journal.ui.empty"), TEXT_W), List.of(), null,
                    false));
            return out;
        }
        for (JournalPages.Block b : blocks) {
            // every block starts on a new page; a long block runs over several pages
            List<FormattedCharSequence> wrapped = new ArrayList<>();
            for (Component c : b.lines()) {
                wrapped.addAll(font.split(c, TEXT_W));
            }
            for (int i = 0; i < wrapped.size(); i += LINES_PER_PAGE) {
                boolean first = i == 0;
                out.add(new Page(wrapped.subList(i, Math.min(wrapped.size(), i + LINES_PER_PAGE)),
                        first ? b.ids() : List.of(), first ? b.pinnable().orElse(null) : null, b.pinned()));
            }
        }
        return out;
    }

    /** Entries on the shown page are marked read once per screen (JournalAction MARK_READ). */
    void markShownRead() {
        if (pages.isEmpty()) {
            return;
        }
        for (String id : pages.get(page).ids()) {
            boolean isNew = ClientJournal.entries().stream().anyMatch(e -> e.id().equals(id) && e.is(JournalData.NEW));
            if (isNew && markedRead.add(id)) {
                send(id, JournalActionPacket.MARK_READ);
            }
        }
    }

    static void send(String id, int action) {
        WrNetwork.CHANNEL.sendToServer(new JournalActionPacket(Ids.mod(id), action));
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g);
        int left = (width - BOOK_W) / 2;
        g.blit(BookViewScreen.BOOK_LOCATION, left, 2, 0, 0, BOOK_W, BOOK_H);
        if (!pages.isEmpty()) {
            Page p = pages.get(page);
            int y = TEXT_Y;
            for (FormattedCharSequence line : p.lines()) {
                g.drawString(font, line, left + TEXT_X, y, TEXT_COLOR, false);
                y += font.lineHeight;
            }
            Component num = Component.translatable("wildroots.journal.ui.page", page + 1, pages.size());
            g.drawString(font, num, left + BOOK_W - 44 - font.width(num), 18, TEXT_COLOR, false);
        }
        super.render(g, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
