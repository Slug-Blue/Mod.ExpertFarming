package io.github.slugblue.wildroots.client.journal;

import io.github.slugblue.wildroots.WildRoots;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterClientReloadListenersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Client wiring of the journal v2 (§5.2): the guide reload listener. The
 * scene engine adds its {@link GuideSection} here (WR-181k) so that scenes
 * reload in the same pass as the book.
 */
@Mod.EventBusSubscriber(modid = WildRoots.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class JournalClientSetup {

    private JournalClientSetup() {
    }

    @SubscribeEvent
    public static void onRegisterReloadListeners(RegisterClientReloadListenersEvent event) {
        event.registerReloadListener(GuideReloadListener.INSTANCE);
    }
}
