package io.github.slugblue.wildroots.data;

import java.util.concurrent.atomic.AtomicReference;

/**
 * The current definitions of the server (docs/05-architecture.md §8.2 item 6).
 * {@code apply} runs on the {@code main} thread at start-up and on the
 * {@code Server thread} on {@code /reload} (S12), so the reference is atomic.
 */
public final class Definitions {

    private static final AtomicReference<DefinitionsSnapshot> CURRENT =
            new AtomicReference<>(DefinitionsSnapshot.EMPTY);

    /** Species listener of the server; the cultivar listener reads its staged result. */
    public static final SpeciesManager SPECIES = new SpeciesManager();
    /** Cultivar listener; registered after {@link #SPECIES}, its {@code apply} publishes the snapshot. */
    public static final CultivarManager CULTIVARS = new CultivarManager(SPECIES);

    private Definitions() {
    }

    public static DefinitionsSnapshot current() {
        return CURRENT.get();
    }

    /** Replaces the snapshot in one step. */
    public static void publish(DefinitionsSnapshot snapshot) {
        CURRENT.set(snapshot);
    }
}
