package io.github.slugblue.wildroots.lens;

import io.github.slugblue.wildroots.client.ClientPacketHandlers;
import io.github.slugblue.wildroots.network.WrNetwork;
import java.util.function.Supplier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;

/**
 * The lens window packet (playtest 2, item 3): S→C only. The lens adds no
 * C→S packet; a use still goes through the vanilla item use and its
 * cooldown.
 */
public final class LensNetwork {

    private LensNetwork() {
    }

    /** Registers {@link LensWindowPacket} (from {@code WrNetwork.register}). */
    public static void register() {
        WrNetwork.registerToClient(LensWindowPacket.class, LensWindowPacket::encode, LensWindowPacket::clientDecode,
                LensNetwork::onClient);
    }

    private static void onClient(LensWindowPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> ClientPacketHandlers.lensWindow(msg)));
        ctx.get().setPacketHandled(true);
    }

    /**
     * Sends the window to {@code player}; {@code false} (nothing sent) when
     * the player has no connection or the packet would pass
     * {@link LensWindowPacket#MAX_BYTES}.
     */
    public static boolean send(ServerPlayer player, LensWindowPacket packet) {
        if (player == null || player.connection == null || packet.size() > LensWindowPacket.MAX_BYTES) {
            return false;
        }
        sender.accept(player, packet);
        return true;
    }

    private static final java.util.function.BiConsumer<ServerPlayer, LensWindowPacket> CHANNEL =
            (p, w) -> WrNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> p), w);
    private static volatile java.util.function.BiConsumer<ServerPlayer, LensWindowPacket> sender = CHANNEL;

    /**
     * Replaces the sender (GameTests record the windows; the channel writes
     * to the connection itself, past a test player's listener); returns the
     * previous one. {@code null} restores the channel.
     */
    public static java.util.function.BiConsumer<ServerPlayer, LensWindowPacket> setSender(
            java.util.function.BiConsumer<ServerPlayer, LensWindowPacket> s) {
        java.util.function.BiConsumer<ServerPlayer, LensWindowPacket> old = sender;
        sender = s == null ? CHANNEL : s;
        return old;
    }
}
