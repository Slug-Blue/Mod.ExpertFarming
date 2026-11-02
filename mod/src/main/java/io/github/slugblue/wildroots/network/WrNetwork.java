package io.github.slugblue.wildroots.network;

import io.github.slugblue.wildroots.core.Ids;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

/**
 * The mod's network channel {@code wildroots:main} (docs/05-architecture.md
 * §14; ADR-0013). The mod is required on both sides: a client without it,
 * or with another protocol version, does not connect. {@link #PROTOCOL} goes
 * up with every incompatible change.
 *
 * <p>S→C packets are registered here ({@link #registerToClient}); C→S
 * packets only through {@link ServerPacketGuard#register} (SEC-01). No
 * connection leaves Minecraft's own (SEC-43).
 */
public final class WrNetwork {

    public static final String PROTOCOL = "1";

    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(Ids.mod("main"), () -> PROTOCOL,
            PROTOCOL::equals, PROTOCOL::equals);

    private static int nextIndex;

    private WrNetwork() {
    }

    /** Creates the channel and registers the packets; called from the mod constructor. */
    public static void register() {
        // S→C packets are added here by the tasks that introduce them (DefinitionsSync: WR-128).
    }

    /** Index for the next message of the channel. */
    public static synchronized int nextIndex() {
        return nextIndex++;
    }

    /**
     * Registers an S→C packet; the consumer runs on the client network
     * thread and must hand work to the client side itself (§15).
     */
    public static <M> void registerToClient(Class<M> type, BiConsumer<M, FriendlyByteBuf> encoder,
                                            Function<FriendlyByteBuf, M> decoder,
                                            BiConsumer<M, Supplier<NetworkEvent.Context>> consumer) {
        CHANNEL.messageBuilder(type, nextIndex(), NetworkDirection.PLAY_TO_CLIENT)
                .encoder(encoder)
                .decoder(decoder)
                .consumerNetworkThread(consumer)
                .add();
    }
}
