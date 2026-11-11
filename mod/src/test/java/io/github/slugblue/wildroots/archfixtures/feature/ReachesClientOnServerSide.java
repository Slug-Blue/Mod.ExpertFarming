package io.github.slugblue.wildroots.archfixtures.feature;

import io.github.slugblue.wildroots.archfixtures.client.ClientHandlers;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;

/** Violation (WR-198): the DistExecutor shape with the server side runs the client call on the server. */
public class ReachesClientOnServerSide {
    void onPacket(String msg) {
        DistExecutor.unsafeRunWhenOn(Dist.DEDICATED_SERVER, () -> () -> ClientHandlers.handle(msg));
    }
}
