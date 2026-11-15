package io.github.slugblue.wildroots.client;

import io.github.slugblue.wildroots.WildRoots;
import io.github.slugblue.wildroots.core.safety.EntryPoint;
import io.github.slugblue.wildroots.core.safety.Guarded;
import io.github.slugblue.wildroots.item.PouchTooltip;
import io.github.slugblue.wildroots.lens.LensPanel;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterClientTooltipComponentFactoriesEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Every client tooltip component factory of the mod, in one listener (mod
 * bus, client only).
 *
 * <p>Playtest 2, item 5: the lens panel and the pouch tooltip each had a
 * nested {@code Registration.onRegister(RegisterClientTooltipComponentFactoriesEvent)}.
 * Forge's event bus names the generated listener class after the package,
 * the <em>simple</em> class name, the method and the event
 * ({@code IEventListenerFactory#getUniqueName}), so both listeners became the
 * same class and one factory was never registered: the pouch's in dev, the
 * lens panel's in the owner's game ("Unknown TooltipComponent" every frame,
 * swallowed by {@code Guarded}). One listener here, and
 * {@code ProductionArchitectureTest#subscriberWrapperNamesAreUnique} keeps
 * such names unique.
 */
@Mod.EventBusSubscriber(modid = WildRoots.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class ClientTooltipFactories {

    private ClientTooltipFactories() {
    }

    @SubscribeEvent
    @EntryPoint
    public static void onRegisterTooltipFactories(RegisterClientTooltipComponentFactoriesEvent event) {
        Guarded.run("lens panel factory", "client", () -> event.register(LensPanel.class, ClientLensPanel::new));
        Guarded.run("pouch tooltip factory", "client", () ->
                event.register(PouchTooltip.class, ClientPouchTooltip::new));
    }
}
