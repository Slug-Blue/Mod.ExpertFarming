package io.github.slugblue.wildroots.archfixtures.feature;

import io.github.slugblue.wildroots.archfixtures.client.ClientHandlers;

/** Violation (WR-198): calls a mod client class from an ordinary method. */
public class ReachesClientDirectly {
    void onPacket(String msg) {
        ClientHandlers.handle(msg);
    }
}
