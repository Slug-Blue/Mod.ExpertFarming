package io.github.slugblue.wildroots.client.journal.book;

import io.github.slugblue.wildroots.core.Ids;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * Texture paths and the UV layout of the journal atlas (journal v2 §4.5),
 * the contract with the Technical Artist (WR-181h). The atlas shipped
 * with WR-181b is a temporary drawing in this exact layout.
 *
 * <pre>
 * textures/gui/journal.png, 512 × 512
 *   (0,0)     288 × 180  spread: cover, two pages, spine
 *   (0,180)   288 × 16   navigation strip
 *   (0,200)   24 × 20 ×3 tab: normal, hovered, selected (only the left 16 px show)
 *   (72,200)  12 × 16 ×2 bookmark ribbon: normal, hovered
 *   (96,200)  4 × 4      "new" dot
 *   (104,200) 8 × 8      hover/focus frame, 9-slice, border 1
 *   (0,224)   16 × 16    buttons, one column each in {@link Button} order;
 *                        rows: y 224 normal, 240 hovered, 256 disabled
 *   (0,272)   32 × 16    search field, 9-slice, border 3
 *   (32,272)  32 × 32    scene window frame, 9-slice, border 4
 *   (64,272)  64 × 4     slider track; (128,272) 6 × 6 step dot; (136,272) 8 × 10 thumb
 *   (160,272) 8 × 8 ×4   2D frame arrows: right, down, left, up
 * textures/gui/journal_icons.png, 256 × 256: 16 × 16 cells, row by row, in {@link #ICON_SPRITES} order
 * textures/gui/journal_scene.png: scene billboards (WR-181k)
 * textures/gui/trait_bar.png: trait bar segments and flag icons (shared with the lens panel)
 * </pre>
 */
@OnlyIn(Dist.CLIENT)
public final class JournalAtlas {

    public static final ResourceLocation TEXTURE = Ids.mod("textures/gui/journal.png");
    public static final ResourceLocation ICONS = Ids.mod("textures/gui/journal_icons.png");
    public static final ResourceLocation SCENE = Ids.mod("textures/gui/journal_scene.png");
    public static final ResourceLocation TRAIT_BAR = Ids.mod("textures/gui/trait_bar.png");
    public static final int SIZE = 512;
    public static final int ICONS_SIZE = 256;

    /** A rectangle of the atlas. */
    public record Sprite(int u, int v, int width, int height) {
    }

    public static final Sprite SPREAD = new Sprite(0, 0, 288, 180);
    public static final Sprite NAV = new Sprite(0, 180, 288, 16);
    public static final Sprite TAB = new Sprite(0, 200, 24, 20);
    public static final Sprite RIBBON = new Sprite(72, 200, 12, 16);
    public static final Sprite NEW_DOT = new Sprite(96, 200, 4, 4);
    public static final Sprite FOCUS = new Sprite(104, 200, 8, 8);
    public static final Sprite SEARCH_FIELD = new Sprite(0, 272, 32, 16);
    public static final Sprite SCENE_FRAME = new Sprite(32, 272, 32, 32);
    public static final Sprite SLIDER_TRACK = new Sprite(64, 272, 64, 4);
    public static final Sprite SLIDER_DOT = new Sprite(128, 272, 6, 6);
    public static final Sprite SLIDER_THUMB = new Sprite(136, 272, 8, 10);
    public static final Sprite ARROWS = new Sprite(160, 272, 8, 8);

    /** Tab states, by column. */
    public enum TabState {
        NORMAL, HOVERED, SELECTED
    }

    /** Button states, by row. */
    public enum State {
        NORMAL, HOVERED, DISABLED
    }

    /** Buttons, by column (16 px each). */
    public enum Button {
        UP, PREV, NEXT, BACK, FORWARD, HOME, STAR_FULL, STAR_EMPTY, SEARCH,
        PLAY, PAUSE, STEP_BACK, STEP_FORWARD, RESTART, RESET_VIEW, FLAT, STEP;

        public Sprite sprite(State state) {
            return new Sprite(ordinal() * 16, 224 + state.ordinal() * 16, 16, 16);
        }
    }

    /** {@code gui:<name>} icons of categories, in cell order of {@link #ICONS}. */
    public static final java.util.List<String> ICON_SPRITES = java.util.List.of(
            "first_steps", "plant", "selection", "cultivars");

    private JournalAtlas() {
    }

    public static void blit(GuiGraphics g, Sprite s, int x, int y) {
        g.blit(TEXTURE, x, y, s.u(), s.v(), s.width(), s.height(), SIZE, SIZE);
    }

    /** Part of a sprite: its top-left {@code w × h}. */
    public static void blit(GuiGraphics g, Sprite s, int x, int y, int w, int h) {
        g.blit(TEXTURE, x, y, s.u(), s.v(), Math.min(w, s.width()), Math.min(h, s.height()), SIZE, SIZE);
    }

    public static void tab(GuiGraphics g, TabState state, int x, int y, int visibleWidth) {
        g.blit(TEXTURE, x, y, TAB.u() + state.ordinal() * TAB.width(), TAB.v(), visibleWidth, TAB.height(), SIZE, SIZE);
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
