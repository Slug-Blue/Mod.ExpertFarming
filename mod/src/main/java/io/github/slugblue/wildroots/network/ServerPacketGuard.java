package io.github.slugblue.wildroots.network;

import com.mojang.logging.LogUtils;
import io.github.slugblue.wildroots.core.config.ServerConfig;
import io.github.slugblue.wildroots.core.safety.AuditLog;
import java.util.Map;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.simple.SimpleChannel;
import org.slf4j.Logger;

/**
 * The only place where C→S packets are registered (SEC-01;
 * docs/05-architecture.md §14 items 1–3, 7; ADR-0013; ArchUnit
 * {@code packetConsumersOnlyFromGuard}).
 *
 * <p>For every packet the guard: decodes with a wrapper that turns any
 * {@link RuntimeException} or leftover bytes into a "malformed" marker
 * instance; checks that there is a sender and that he is still connected;
 * applies the rate limit ({@link PacketRateLimiter}); runs the handler with
 * {@code enqueueWork} on the server thread and isolates its exceptions; and
 * always sets {@code setPacketHandled(true)}. A dropped packet gets at most
 * one log line per player per minute (audit log); a flood disconnects.
 */
public final class ServerPacketGuard {

    private static final Logger LOGGER = LogUtils.getLogger();

    /** Disconnect reason of a packet flood. */
    public static final String FLOOD_KEY = "wildroots.message.packet_flood";

    /** The server's limiter: config values are read at each packet. */
    public static final PacketRateLimiter LIMITER = new PacketRateLimiter(System::nanoTime,
            ServerConfig.PACKET_BUCKET_CAPACITY::get, ServerConfig.PACKET_REFILL_PER_SECOND::get,
            ServerConfig.PACKET_FLOOD_DISCONNECT_THRESHOLD::get,
            Map.of(NameSelectionLimit.TYPE, PacketRateLimiter.ExtraLimit.perMinute(3)));

    /** Type names with an extra limit. */
    static final class NameSelectionLimit {
        static final String TYPE = "NameSelection";
    }

    /** What the guard needs from a network context (Forge's {@link NetworkEvent.Context} in game, a fake in JUnit). */
    public interface Context<P> {
        P sender();

        UUID id(P player);

        boolean disconnected(P player);

        void enqueueWork(Runnable work);

        void setPacketHandled(boolean handled);

        void disconnect(P player, Component reason);
    }

    private ServerPacketGuard() {
    }

    /** Decoder that never throws: a broken buffer or extra bytes give {@code malformed}. */
    public static <M> Function<FriendlyByteBuf, M> guardedDecoder(Function<FriendlyByteBuf, M> decoder, M malformed) {
        return buf -> {
            try {
                M m = decoder.apply(buf);
                if (m == null || buf.isReadable()) {
                    return malformed;
                }
                return m;
            } catch (RuntimeException e) {
                return malformed;
            }
        };
    }

    /**
     * Handles one received packet; see the class comment. {@code log}
     * receives the (throttled) log lines.
     */
    public static <M, P> void handle(String type, M msg, M malformed, Context<P> ctx, PacketRateLimiter limiter,
                                     BiConsumer<M, P> handler, Consumer<String> log) {
        try {
            P sender = ctx.sender();
            if (sender == null || ctx.disconnected(sender)) {
                return;
            }
            UUID id = ctx.id(sender);
            PacketRateLimiter.Decision d = msg == null || msg == malformed ? limiter.recordDrop(id)
                    : limiter.tryAcquire(id, type);
            if (!d.allowed()) {
                if (d.log()) {
                    log.accept(AuditLog.format("packet_dropped", "player", id, "type", type,
                            "reason", msg == null || msg == malformed ? "malformed" : "rate_limit"));
                }
                if (d.disconnect()) {
                    log.accept(AuditLog.format("packet_flood_disconnect", "player", id));
                    ctx.disconnect(sender, Component.translatable(FLOOD_KEY));
                }
                return;
            }
            ctx.enqueueWork(() -> {
                try {
                    if (!ctx.disconnected(sender)) {
                        handler.accept(msg, sender);
                    }
                } catch (RuntimeException e) {
                    log.accept("Wild Roots: handler of " + type + " failed: " + e);
                }
            });
        } catch (RuntimeException e) {
            log.accept("Wild Roots: guard of " + type + " failed: " + e);
        } finally {
            ctx.setPacketHandled(true);
        }
    }

    /** Registers a C→S packet on {@code channel}; the only call site of {@code consumer*} for C→S. */
    public static <M> void register(SimpleChannel channel, int index, Class<M> type, BiConsumer<M, FriendlyByteBuf> encoder,
                                    Function<FriendlyByteBuf, M> decoder, M malformed, BiConsumer<M, ServerPlayer> handler) {
        String name = type.getSimpleName().replace("Packet", "");
        channel.messageBuilder(type, index, NetworkDirection.PLAY_TO_SERVER)
                .encoder(encoder)
                .decoder(guardedDecoder(decoder, malformed))
                .consumerNetworkThread((M msg, java.util.function.Supplier<NetworkEvent.Context> supplier) ->
                        handle(name, msg, malformed, new ForgeContext(supplier.get()), LIMITER, handler,
                                AuditLog.logger()::info))
                .add();
    }

    /** Forgets the limiter state of a player who left (Forge bus). */
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        LIMITER.forget(event.getEntity().getUUID());
    }

    /** {@link Context} over Forge's network context. */
    private record ForgeContext(NetworkEvent.Context ctx) implements Context<ServerPlayer> {

        @Override
        public ServerPlayer sender() {
            return ctx.getSender();
        }

        @Override
        public UUID id(ServerPlayer player) {
            return player.getUUID();
        }

        @Override
        public boolean disconnected(ServerPlayer player) {
            return player.hasDisconnected();
        }

        @Override
        public void enqueueWork(Runnable work) {
            ctx.enqueueWork(work);
        }

        @Override
        public void setPacketHandled(boolean handled) {
            ctx.setPacketHandled(handled);
        }

        @Override
        public void disconnect(ServerPlayer player, Component reason) {
            LOGGER.warn("Wild Roots: disconnecting {} for a packet flood", player.getGameProfile().getName());
            ctx.enqueueWork(() -> player.connection.disconnect(reason));
        }
    }
}
