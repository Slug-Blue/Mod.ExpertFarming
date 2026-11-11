package io.github.slugblue.wildroots.archfixtures.feature;

import io.github.slugblue.wildroots.archfixtures.client.ClientHandlers;

/** Violation (WR-198): a lambda that is not guarded by DistExecutor runs on the server too. */
public class ReachesClientInPlainLambda {
    static void enqueueWork(Runnable work) {
        work.run();
    }

    void onPacket(String msg) {
        enqueueWork(() -> ClientHandlers.handle(msg));
    }
}
