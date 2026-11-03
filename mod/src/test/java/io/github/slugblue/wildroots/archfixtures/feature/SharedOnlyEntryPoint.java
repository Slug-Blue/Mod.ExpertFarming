package io.github.slugblue.wildroots.archfixtures.feature;

import io.github.slugblue.wildroots.archfixtures.core.safety.FakeGuarded;
import io.github.slugblue.wildroots.core.safety.EntryPoint;

/** Violates SEC-34: it touches Guarded (shared()) but runs its logic unwrapped. */
public class SharedOnlyEntryPoint {
    @EntryPoint
    public static void onEvent(Object event) {
        FakeGuarded.shared();
        event.hashCode();
    }
}
