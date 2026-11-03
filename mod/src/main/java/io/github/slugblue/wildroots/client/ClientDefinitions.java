package io.github.slugblue.wildroots.client;

import io.github.slugblue.wildroots.WildRoots;
import io.github.slugblue.wildroots.data.sync.SyncedDefinitions;
import java.util.List;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * The client's immutable copy of the synced definitions
 * (docs/05-architecture.md §8.3, §15): replaced by each complete sync,
 * dropped when the player leaves the server.
 */
@Mod.EventBusSubscriber(modid = WildRoots.MOD_ID, value = Dist.CLIENT)
public final class ClientDefinitions {

    private static final SyncedDefinitions EMPTY = new SyncedDefinitions(List.of(), List.of(), List.of());
    private static volatile SyncedDefinitions current = EMPTY;

    private ClientDefinitions() {
    }

    public static SyncedDefinitions get() {
        return current;
    }

    static void set(SyncedDefinitions defs) {
        current = defs;
    }

    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        current = EMPTY;
        ClientPacketHandlers.resetAssembler();
    }
}
