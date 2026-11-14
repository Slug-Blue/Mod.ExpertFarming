package io.github.slugblue.wildroots.client.journal;

import io.github.slugblue.wildroots.WildRoots;
import io.github.slugblue.wildroots.client.journal.page.FallbackFrameView;
import io.github.slugblue.wildroots.client.journal.page.LaidOutPage;
import io.github.slugblue.wildroots.client.journal.page.PageTypes;
import io.github.slugblue.wildroots.client.journal.page.SceneFrames;
import io.github.slugblue.wildroots.client.journal.page.ScenePage;
import io.github.slugblue.wildroots.client.journal.scene.SceneFallback;
import io.github.slugblue.wildroots.client.journal.scene.ScenePageView;
import io.github.slugblue.wildroots.client.journal.scene.SceneRegistry;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterClientReloadListenersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Client wiring of the journal v2 (§5.2, §3.13): the guide reload
 * listener with its two scene sections (the 2D frames, WR-181d, and the
 * 3D scenes, WR-181k), the scene page type, and the 2D frame the scene
 * page falls back to.
 */
@net.minecraftforge.api.distmarker.OnlyIn(Dist.CLIENT)
@Mod.EventBusSubscriber(modid = WildRoots.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class JournalClientSetup {

    private JournalClientSetup() {
    }

    @SubscribeEvent
    public static void onRegisterReloadListeners(RegisterClientReloadListenersEvent event) {
        // 2D frames of the scenes (WR-181d): read from the same files, also when the 3D scene is refused
        GuideReloadListener.addSection(SceneFrames.INSTANCE);
        // scene engine (WR-181k): its folder reloads with the guide, and the page type of {"type": "scene"}
        GuideReloadListener.addSection(SceneRegistry.INSTANCE);
        PageTypes.register(ScenePage.TYPE, ScenePage.class, (data, page, ctx) -> {
            if (SceneRegistry.INSTANCE.get(data.scene()).isEmpty()) {
                // the 3D scene did not load: the whole page is its 2D frame (if that loaded)
                List<LaidOutPage> flat = FallbackFrameView.layout(data.scene(), ctx);
                if (!flat.isEmpty()) {
                    return flat;
                }
            }
            return List.of(LaidOutPage.spread(rect -> new ScenePageView(rect, data, ctx)));
        });
        // the window of a 3D scene in 2D mode is drawn by FallbackFrameView, on the same step
        SceneFallback.Install.set((rect, id, ctx) -> SceneFrames.get(id).<SceneFallback>map(f -> {
            FallbackFrameView v = new FallbackFrameView(rect, f, ctx);
            return new SceneFallback() {
                @Override
                public void setStep(int step) {
                    v.setStep(step);
                }

                @Override
                public void renderWindow(GuiGraphics g, int x, int y, int w, int h, int mouseX, int mouseY,
                                         float partialTick) {
                    v.renderWindow(g, x, y, w, h, mouseX, mouseY, partialTick);
                }

                @Override
                public boolean mouseClicked(double mouseX, double mouseY) {
                    return v.clickWindow(mouseX, mouseY);
                }

                @Override
                public int step() {
                    return v.step();
                }
            };
        }).orElse(null));
        event.registerReloadListener(GuideReloadListener.INSTANCE);
    }
}
