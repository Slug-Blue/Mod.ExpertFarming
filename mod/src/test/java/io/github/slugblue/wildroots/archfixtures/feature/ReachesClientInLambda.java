package io.github.slugblue.wildroots.archfixtures.feature;

import io.github.slugblue.wildroots.archfixtures.client.ClientHandlers;
import java.util.function.Supplier;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;

/** Allowed (WR-198): the DistExecutor shape, the client call inside the guarded lambda, also from a work lambda. */
public class ReachesClientInLambda {
    static void enqueueWork(Runnable work) {
    }

    void onPacket(String msg) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientHandlers.handle(msg));
    }

    void onPacketLater(String msg) {
        enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientHandlers.handle(msg)));
    }

    Supplier<Integer> unrelated() {
        return () -> 1;
    }
}
