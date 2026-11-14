package io.github.slugblue.wildroots.client.journal;

import io.github.slugblue.wildroots.WildRoots;
import io.github.slugblue.wildroots.client.journal.page.LaidOutPage;
import io.github.slugblue.wildroots.client.journal.page.PageTypes;
import io.github.slugblue.wildroots.client.journal.page.ScenePage;
import io.github.slugblue.wildroots.client.journal.scene.ScenePageView;
import io.github.slugblue.wildroots.client.journal.scene.SceneRegistry;
import java.util.List;
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
        // 2D frames of the scenes (WR-181d)
        GuideReloadListener.addSection(io.github.slugblue.wildroots.client.journal.page.SceneFrames.INSTANCE);
        // scene engine (WR-181k): its folder reloads with the guide, and the page type of {"type": "scene"}
        GuideReloadListener.addSection(SceneRegistry.INSTANCE);
        PageTypes.register(ScenePage.TYPE, ScenePage.class,
                (data, page, ctx) -> List.of(LaidOutPage.spread(rect -> new ScenePageView(rect, data, ctx))));
        event.registerReloadListener(GuideReloadListener.INSTANCE);
    }
}
