package io.github.slugblue.wildroots.client.journal;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * The guide the client loaded last (journal v2 §5.2): replaced as a whole
 * by each resource reload, so a reader always sees one consistent book.
 */
@OnlyIn(Dist.CLIENT)
public final class GuideBooks {

    private static volatile GuideBook current = GuideBook.EMPTY;
    private static final List<Runnable> LISTENERS = new CopyOnWriteArrayList<>();

    private GuideBooks() {
    }

    public static GuideBook current() {
        return current;
    }

    /** Sets the book; render thread. Listeners run afterwards (an open journal rebuilds its layout). */
    static void set(GuideBook book) {
        current = book;
    }

    /** Called on the render thread after each reload, once the book and every {@link GuideSection} are applied. */
    public static void addReloadListener(Runnable r) {
        LISTENERS.add(r);
    }

    static void fireReloaded() {
        for (Runnable r : LISTENERS) {
            r.run();
        }
    }
}
