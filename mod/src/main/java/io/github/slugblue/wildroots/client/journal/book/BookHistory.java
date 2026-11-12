package io.github.slugblue.wildroots.client.journal.book;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Optional;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * Back / forward history of the journal (journal v2 §3.11): a stack of
 * locations, at most {@value #MAX} each way; the oldest is dropped. A jump
 * (link, contents, tab, search, bookmark, icon) pushes the current
 * location; turning a page does not.
 *
 * @param <L> a location
 */
@OnlyIn(Dist.CLIENT)
public final class BookHistory<L> {

    public static final int MAX = 32;

    private final Deque<L> back = new ArrayDeque<>();
    private final Deque<L> forward = new ArrayDeque<>();

    /** Records {@code current} before a jump; clears the forward stack. */
    public void push(L current) {
        if (back.size() >= MAX) {
            back.removeLast();
        }
        back.push(current);
        forward.clear();
    }

    /** Goes back from {@code current}: returns the previous location, {@code current} goes forward. */
    public Optional<L> back(L current) {
        if (back.isEmpty()) {
            return Optional.empty();
        }
        if (forward.size() >= MAX) {
            forward.removeLast();
        }
        forward.push(current);
        return Optional.of(back.pop());
    }

    public Optional<L> forward(L current) {
        if (forward.isEmpty()) {
            return Optional.empty();
        }
        if (back.size() >= MAX) {
            back.removeLast();
        }
        back.push(current);
        return Optional.of(forward.pop());
    }

    public boolean canGoBack() {
        return !back.isEmpty();
    }

    public boolean canGoForward() {
        return !forward.isEmpty();
    }

    public int backSize() {
        return back.size();
    }

    public int forwardSize() {
        return forward.size();
    }

    public void clear() {
        back.clear();
        forward.clear();
    }
}
