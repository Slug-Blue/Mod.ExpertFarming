package io.github.slugblue.wildroots.archfixtures.feature;

import io.github.slugblue.wildroots.archfixtures.client.ClientHandlers;
import java.util.function.Supplier;

/** Allowed (WR-198): the {@code DistExecutor} shape, the client call inside a lambda body. */
public class ReachesClientInLambda {
    static void runWhenOnClient(Supplier<Runnable> work) {
        // DistExecutor.unsafeRunWhenOn(Dist.CLIENT, work) in the mod
    }

    void onPacket(String msg) {
        runWhenOnClient(() -> () -> ClientHandlers.handle(msg));
    }
}
