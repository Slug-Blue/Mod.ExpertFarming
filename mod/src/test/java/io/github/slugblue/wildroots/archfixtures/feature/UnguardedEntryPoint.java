package io.github.slugblue.wildroots.archfixtures.feature;

import io.github.slugblue.wildroots.core.safety.EntryPoint;

/** Violates SEC-34: an entry point without Guarded. */
public class UnguardedEntryPoint {
    @EntryPoint
    public static void onEvent(Object event) {
        event.hashCode();
    }
}
