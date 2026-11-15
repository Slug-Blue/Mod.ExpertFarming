package io.github.slugblue.wildroots.client.journal.book;

import io.github.slugblue.wildroots.core.Ids;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * Texture paths and UVs of the journal art (WR-181h), as fixed by
 * {@code docs/design/journal-v2-art.md}: four atlases in
 * {@code textures/gui/journal/}. The symbolic names are the API the pages
 * and the scene engine use; the numbers follow the art doc.
 */
@OnlyIn(Dist.CLIENT)
public final class JournalAtlas {

    public static final ResourceLocation TEXTURE = Ids.mod("textures/gui/journal/journal.png");
    public static final ResourceLocation ICONS = Ids.mod("textures/gui/journal/journal_icons.png");
    public static final ResourceLocation SCENE = Ids.mod("textures/gui/journal/journal_scene.png");
    public static final ResourceLocation TRAIT_BAR = Ids.mod("textures/gui/journal/trait_bar.png");
    /** {@link #TEXTURE} is {@value} × {@value}. */
    public static final int SIZE = 512;
    /** {@link #ICONS} is {@value} × {@value}. */
    public static final int ICONS_SIZE = 64;
    public static final int SCENE_W = 64;
    public static final int SCENE_H = 32;
    public static final int TRAIT_BAR_W = 64;
    public static final int TRAIT_BAR_H = 32;

    /** A rectangle of {@link #TEXTURE}. */
    public record Sprite(int u, int v, int width, int height) {

        /** The same size, {@code du, dv} further: the next state of a multi-state sprite. */
        public Sprite offset(int du, int dv) {
            return new Sprite(u + du, v + dv, width, height);
        }
    }

    public static final Sprite SPREAD = new Sprite(0, 0, 288, 180);
    public static final Sprite NAV = new Sprite(0, 184, 288, 16);
    /**
     * Tab, normal; hovered and selected are the next cells to the right
     * (+34 u). 34 wide: 22 px stick out of the book, so the 16 px icon sits
     * wholly outside the page (owner playtest 2); 12 px go under the cover.
     */
    public static final Sprite TAB = new Sprite(0, 212, 34, 20);
    /** Bookmark ribbon, normal; hovered +12 u. */
    public static final Sprite RIBBON = new Sprite(364, 0, 12, 16);
    public static final Sprite NEW_DOT = new Sprite(388, 0, 7, 7);
    /** Hover and keyboard focus ring, 9-slice, border {@value #FOCUS_BORDER}. */
    public static final Sprite FOCUS = new Sprite(382, 176, 6, 6);
    public static final int FOCUS_BORDER = 2;
    /** Search field, 9-slice, border 3; focused +24 u; text at +4, +3. */
    public static final Sprite SEARCH_FIELD = new Sprite(328, 72, 24, 14);
    public static final Sprite SEARCH_FIELD_FOCUSED = new Sprite(352, 72, 24, 14);
    /** Scene window frame, 9-slice, border {@value #SCENE_FRAME_BORDER}, drawn on the window grown by 4. */
    public static final Sprite SCENE_FRAME = new Sprite(292, 176, 16, 16);
    public static final int SCENE_FRAME_BORDER = 4;
    /** Optional sky behind a scene; stretched. */
    public static final Sprite SCENE_BACKDROP = new Sprite(312, 176, 16, 16);
    /** Slider track, tiled horizontally: empty (ahead) and filled (passed). */
    public static final Sprite SLIDER_TRACK = new Sprite(292, 164, 8, 3);
    public static final Sprite SLIDER_TRACK_FILLED = new Sprite(300, 164, 8, 3);
    /** Step dots, 7 × 7, centre at +3, +3. */
    public static final Sprite SLIDER_DOT_PAST = new Sprite(310, 162, 7, 7);
    public static final Sprite SLIDER_DOT = new Sprite(318, 162, 7, 7);
    public static final Sprite SLIDER_DOT_FUTURE = new Sprite(326, 162, 7, 7);
    public static final Sprite SLIDER_DOT_HOVER = new Sprite(334, 162, 7, 7);
    /** Slider handle 5 × 9, centre x at +2; hovered +6 u, disabled +12 u. */
    public static final Sprite SLIDER_THUMB = new Sprite(344, 161, 5, 9);
    /** 2D frame arrows 8 × 8, ink: right; down, left, up, loop are the next cells (+8 u each). */
    public static final Sprite ARROWS = new Sprite(332, 176, 8, 8);
    /** Current step in the 2D frame, 9-slice, border 2. */
    public static final Sprite FRAME_HIGHLIGHT = new Sprite(372, 176, 8, 8);

    /** Tab states, by column. */
    public enum TabState {
        NORMAL, HOVERED, SELECTED
    }

    /** Button states, by row. {@link #ACTIVE} exists only for the scene controls (a toggle that is on). */
    public enum State {
        NORMAL, HOVERED, DISABLED, ACTIVE
    }

    /** The 2D frame arrows, in atlas order. */
    public enum Arrow {
        RIGHT, DOWN, LEFT, UP, LOOP;

        public Sprite sprite() {
            return ARROWS.offset(ordinal() * 8, 0);
        }
    }

    /**
     * Buttons: navigation 16 × 16 (rows 16 apart), header buttons 12 × 12
     * (rows 12 apart), scene controls 12 × 12 (rows 12 apart, with an
     * {@code ACTIVE} row). {@link #STEP} is the current-step dot of the
     * slider, one cell for every state.
     */
    public enum Button {
        BACK(292, 24, 16, 16, false), PREV(308, 24, 16, 16, false), HOME(324, 24, 16, 16, false),
        NEXT(340, 24, 16, 16, false), FORWARD(356, 24, 16, 16, false), SEARCH(372, 24, 16, 16, false),
        UP(292, 72, 12, 12, false), STAR_EMPTY(304, 72, 12, 12, false), STAR_FULL(316, 72, 12, 12, false),
        STEP_BACK(292, 112, 12, 12, true), PLAY(304, 112, 12, 12, true), PAUSE(316, 112, 12, 12, true),
        STEP_FORWARD(328, 112, 12, 12, true), RESTART(340, 112, 12, 12, true), RESET_VIEW(352, 112, 12, 12, true),
        FLAT(364, 112, 12, 12, true), STEP(318, 162, 7, 0, false);

        private final int u;
        private final int v;
        public final int size;
        private final int rowStep;
        private final boolean hasActive;

        Button(int u, int v, int size, int rowStep, boolean hasActive) {
            this.u = u;
            this.v = v;
            this.size = size;
            this.rowStep = rowStep;
            this.hasActive = hasActive;
        }

        public Sprite sprite(State state) {
            State s = state == State.ACTIVE && !hasActive ? State.NORMAL : state;
            return new Sprite(u, v + s.ordinal() * rowStep, size, size);
        }
    }

    /** {@code gui:<name>} icons of {@link #ICONS}, 16 × 16, row by row (4 a row). */
    public static final List<String> ICON_SPRITES = List.of(
            "first_steps", "plant", "traits", "pollination",
            "cultivars", "species", "lines", "notes",
            "contents", "bookmarks", "search", "unknown");

    private JournalAtlas() {
    }

    public static void blit(GuiGraphics g, Sprite s, int x, int y) {
        g.blit(TEXTURE, x, y, s.u(), s.v(), s.width(), s.height(), SIZE, SIZE);
    }

    /** Part of a sprite: its top-left {@code w × h}. */
    public static void blit(GuiGraphics g, Sprite s, int x, int y, int w, int h) {
        g.blit(TEXTURE, x, y, s.u(), s.v(), Math.min(w, s.width()), Math.min(h, s.height()), SIZE, SIZE);
    }

    public static void tab(GuiGraphics g, TabState state, int x, int y) {
        blit(g, TAB.offset(state.ordinal() * TAB.width(), 0), x, y);
    }

    /** Stretches a 9-slice sprite with a border of {@code b} px over {@code w × h}. */
    public static void nineSlice(GuiGraphics g, Sprite s, int b, int x, int y, int w, int h) {
        // GuiGraphics.blitNineSliced assumes a 256 × 256 texture; this atlas is 512 × 512
        int cw = s.width() - 2 * b;
        int ch = s.height() - 2 * b;
        int iw = Math.max(0, w - 2 * b);
        int ih = Math.max(0, h - 2 * b);
        int[] dx = {x, x + b, x + w - b};
        int[] dw = {b, iw, b};
        int[] su = {s.u(), s.u() + b, s.u() + s.width() - b};
        int[] sw = {b, cw, b};
        int[] dy = {y, y + b, y + h - b};
        int[] dh = {b, ih, b};
        int[] sv = {s.v(), s.v() + b, s.v() + s.height() - b};
        int[] sh = {b, ch, b};
        for (int r = 0; r < 3; r++) {
            for (int c = 0; c < 3; c++) {
                if (dw[c] > 0 && dh[r] > 0) {
                    g.blit(TEXTURE, dx[c], dy[r], dw[c], dh[r], su[c], sv[r], sw[c], sh[r], SIZE, SIZE);
                }
            }
        }
    }

    /** Draws a {@code gui:} icon (16 × 16); an unknown name draws nothing. */
    public static void icon(GuiGraphics g, String sprite, int x, int y) {
        int i = ICON_SPRITES.indexOf(sprite);
        if (i < 0) {
            return;
        }
        int perRow = ICONS_SIZE / 16;
        g.blit(ICONS, x, y, (i % perRow) * 16, (i / perRow) * 16, 16, 16, ICONS_SIZE, ICONS_SIZE);
    }
}
