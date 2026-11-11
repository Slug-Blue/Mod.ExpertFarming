package io.github.slugblue.wildroots.archfixtures.feature;

import io.github.slugblue.wildroots.archfixtures.client.ClientHandlers;
import java.util.function.Consumer;

/** Violation (WR-198): a method reference is resolved where it is written, not when it runs. */
public class ReachesClientByMethodReference {
    Consumer<String> handler() {
        return ClientHandlers::handle;
    }
}
