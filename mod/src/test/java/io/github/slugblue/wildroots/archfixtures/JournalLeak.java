package io.github.slugblue.wildroots.archfixtures;

/**
 * Self-test fixture of SceneArchitectureTest (review m9): common code that
 * reaches the client journal. The rule must fail on it.
 */
public final class JournalLeak {

    private JournalLeak() {
    }

    public static Object leak() {
        return io.github.slugblue.wildroots.client.journal.GuideBook.EMPTY;
    }
}
