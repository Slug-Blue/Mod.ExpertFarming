package io.github.slugblue.wildroots.archfixtures.feature;

import io.github.slugblue.wildroots.archfixtures.client.ClientMarker;

/** Violation (WR-198): a client annotation on a common method. */
public class AnnotatedWithClientType {
    @ClientMarker
    void run() {
    }
}
