package io.github.slugblue.wildroots.gametest;

import io.github.slugblue.wildroots.WildRoots;
import io.github.slugblue.wildroots.core.Ids;
import io.github.slugblue.wildroots.network.ServerPacketGuard;
import io.github.slugblue.wildroots.network.c2s.GameTestProbePacket;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

/**
 * A GameTest-only C→S channel {@code wildroots:gametest_probe} with one
 * packet ({@link GameTestProbePacket}) registered through the production
 * {@link ServerPacketGuard#register} (real guarded decoder, real
 * {@code ServerPacketGuard.LIMITER} with the SERVER config, real Forge
 * dispatch). No production C→S packet is registered yet (WR-127 ships codecs
 * only), so SEC-02 is exercised on this probe. The channel is optional on
 * both sides ({@code acceptMissingOr}), so dev runs can still join servers
 * without the GameTest source set.
 */
@Mod.EventBusSubscriber(modid = WildRoots.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class GuardProbeChannel {

    public static final ResourceLocation NAME = Ids.mod("gametest_probe");
    private static final String VERSION = "1";
    private static volatile SimpleChannel channel;
    private static final Map<UUID, Integer> HANDLED = new ConcurrentHashMap<>();

    private GuardProbeChannel() {
    }

    @SubscribeEvent
    public static void onCommonSetup(FMLCommonSetupEvent event) {
        // NetworkRegistry locks at the COMPLETE phase, after common setup
        event.enqueueWork(() -> {
            SimpleChannel c = NetworkRegistry.newSimpleChannel(NAME, () -> VERSION,
                    NetworkRegistry.acceptMissingOr(VERSION), NetworkRegistry.acceptMissingOr(VERSION));
            ServerPacketGuard.register(c, 0, GameTestProbePacket.class, GameTestProbePacket::encode,
                    GameTestProbePacket::decode, GameTestProbePacket.MALFORMED,
                    (msg, player) -> HANDLED.merge(player.getUUID(), 1, Integer::sum));
            channel = c;
        });
    }

    public static SimpleChannel channel() {
        return channel;
    }

    /** Packets of {@code player} that reached the handler. */
    public static int handled(UUID player) {
        return HANDLED.getOrDefault(player, 0);
    }
}
