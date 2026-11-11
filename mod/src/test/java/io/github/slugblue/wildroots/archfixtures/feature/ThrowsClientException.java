package io.github.slugblue.wildroots.archfixtures.feature;

import io.github.slugblue.wildroots.archfixtures.client.ClientFailure;

/** Violation (WR-198): a client type in a throws clause. */
public class ThrowsClientException {
    void run() throws ClientFailure {
    }
}
