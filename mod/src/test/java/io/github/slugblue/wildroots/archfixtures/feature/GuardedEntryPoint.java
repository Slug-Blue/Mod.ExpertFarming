package io.github.slugblue.wildroots.archfixtures.feature;

import io.github.slugblue.wildroots.archfixtures.core.safety.FakeGuarded;
import io.github.slugblue.wildroots.core.safety.EntryPoint;

/** Allowed: the entry point runs its logic through Guarded. */
public class GuardedEntryPoint {
    @EntryPoint
    public static void onEvent(Object event) {
        FakeGuarded.run("event", "key", event::hashCode);
    }
}
