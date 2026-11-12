package io.github.slugblue.wildroots.client.journal.page;

import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * One laid-out page of the open journal (journal v2 §3): a half page or a
 * page over the whole spread. A view is created once per layout (open,
 * {@code JournalSync}, guide reload, window resize) by its
 * {@link PageType} with its final {@link PageRect}, and then only draws
 * and reacts; it never lays itself out again per frame (§5.3).
 *
 * <p><b>Lifecycle.</b> All views of an entry are created together, but at
 * most one spread is visible: {@link #shown()} when its spread becomes
 * visible, {@link #hidden()} when the reader turns away, jumps elsewhere,
 * the layout is rebuilt or the screen closes. A view that holds GPU or
 * other native resources allocates them in {@code shown()} (or lazily in
 * {@code render}) and frees them in {@code hidden()}; {@code hidden()} may
 * come without a prior {@code shown()} and more than once.
 *
 * <p><b>Time.</b> {@link #tick()} runs 20 times a second, only while the
 * view is visible; {@code render}'s {@code partialTick} interpolates
 * between ticks.
 *
 * <p><b>Input</b> (coordinates are GUI pixels of the screen; a method
 * returns {@code true} when it consumed the event):
 * <ul>
 *   <li>mouse click, drag and release: the book's own widgets first, then
 *       the visible view whose rect holds the pointer; the view that
 *       consumed a click receives the drags and the release of that
 *       button, and takes the keyboard focus;</li>
 *   <li>mouse wheel: the view under the pointer first; if it does not
 *       consume, the book turns the page (a scene window consumes it to
 *       zoom, §3.11);</li>
 *   <li>keys: Esc always closes the book; then the focused view; then the
 *       book's keys (§3.11: arrows, PgUp/PgDn, A/D, Backspace, Alt+arrows,
 *       Home, Tab); then every visible view in order, so a scene can take
 *       {@code Space [ ] R V + -} without focus while the arrows stay with
 *       the book unless the scene has focus;</li>
 *   <li>{@link #widgets()}: the view's buttons, added to the screen while
 *       the view is visible, so they get Tab focus, Enter and narration.
 *       The screen draws them after {@link #render}.</li>
 * </ul>
 */
@OnlyIn(Dist.CLIENT)
public interface PageView {

    PageRect rect();

    /** Draws the page. Called every frame; must not lay out text or rebuild meshes here. */
    void render(GuiGraphics g, int mouseX, int mouseY, float partialTick);

    /** Screen tick (20/s) while visible. */
    default void tick() {
    }

    /** Its spread became visible. */
    default void shown() {
    }

    /** Its spread is no longer visible, or the layout or screen is gone: free native resources. */
    default void hidden() {
    }

    /** Focusable widgets of this view (created once with the view). */
    default List<? extends AbstractWidget> widgets() {
        return List.of();
    }

    default boolean mouseClicked(double mouseX, double mouseY, int button) {
        return false;
    }

    default boolean mouseReleased(double mouseX, double mouseY, int button) {
        return false;
    }

    default boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        return false;
    }

    default boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        return false;
    }

    /**
     * @param focused whether this view holds the keyboard focus (it consumed
     *                the last click); an unfocused view should only take
     *                keys the book does not use
     */
    default boolean keyPressed(int keyCode, int scanCode, int modifiers, boolean focused) {
        return false;
    }

    default boolean charTyped(char c, int modifiers) {
        return false;
    }
}
