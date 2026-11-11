package io.github.slugblue.wildroots.archfixtures.feature;

import io.github.slugblue.wildroots.archfixtures.client.ClientFailure;

/** Violation (WR-198): the verifier loads a catch type. */
public class CatchesClientException {
    void run(Runnable r) {
        try {
            r.run();
        } catch (ClientFailure e) {
            // ignored
        }
    }
}
