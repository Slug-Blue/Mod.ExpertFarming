package io.github.slugblue.wildroots.network;

import com.mojang.logging.LogUtils;
import io.github.slugblue.wildroots.core.config.ServerConfig;
import io.github.slugblue.wildroots.core.safety.AuditLog;
import io.github.slugblue.wildroots.core.safety.LogThrottle;
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
            Map.of(ServerPacketGuard.NAME_SELECTION, () -> PacketRateLimiter.ExtraLimit.perMinute(
                    ServerConfig.NAME_SELECTION_PER_MINUTE.get())));

    /**
     * Limiter of menu button clicks ({@code clickMenuButton}, SEC-02): per
     * (player, menu) {@code menuButtonsPerSecond} from the SERVER config; the
     * menus of the analyser and the lab use it (WR-184, WR-185).
     */
    public static final PacketRateLimiter MENU_BUTTONS = new PacketRateLimiter(System::nanoTime,
            ServerConfig.MENU_BUTTONS_PER_SECOND::get, ServerConfig.MENU_BUTTONS_PER_SECOND::get, () -> 0, Map.of());

    /** Type name of {@code NameSelectionPacket}, the one type with a slower extra bucket (SEC-02). */
    public static final String NAME_SELECTION = "NameSelection";

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
                    failure(type, ctx.id(sender), "handler", e, log);
                }
            });
        } catch (RuntimeException e) {
            P sender = null;
            try {
                sender = ctx.sender();
            } catch (RuntimeException ignored) {
                // no sender to name in the line
            }
            failure(type, sender == null ? null : ctx.id(sender), "guard", e, log);
        } finally {
            ctx.setPacketHandled(true);
        }
    }

    /** At most one failure line per (packet type, player) in this window (as {@link io.github.slugblue.wildroots.core.safety.Guarded}). */
    public static final long FAILURE_LOG_MS = io.github.slugblue.wildroots.core.safety.Guarded.THROTTLE_MS;
    private static volatile LogThrottle failureThrottle = new LogThrottle(System::currentTimeMillis, FAILURE_LOG_MS);

    /** Replaces the failure throttle (tests: own clock) and returns the previous one. */
    public static LogThrottle setFailureThrottle(LogThrottle t) {
        LogThrottle old = failureThrottle;
        failureThrottle = t;
        return old;
    }

    /**
     * A handler or guard failure as an escaped audit line (SEC-47): the
     * exception text comes from the packet and may carry line breaks or bidi
     * characters; it is escaped and cut by {@link AuditLog#format}.
     */
    private static void failure(String type, UUID player, String stage, RuntimeException e, Consumer<String> log) {
        if (failureThrottle.allow(type + "@" + player)) {
            log.accept(AuditLog.format("packet_failed", "player", player, "type", type, "stage", stage,
                    "error", e.getClass().getSimpleName(), "message", String.valueOf(e.getMessage())));
        }
    }

    /** Package of C→S packet classes: the only types {@link #register} accepts (SEC-04). */
    public static final String C2S_PACKAGE = "io.github.slugblue.wildroots.network.c2s";

    /** Throws unless {@code type} is a C→S packet class of {@link #C2S_PACKAGE}. */
    public static void requireC2sType(Class<?> type) {
        if (type == null || !type.getPackageName().equals(C2S_PACKAGE)) {
            throw new IllegalArgumentException("C→S packets live in " + C2S_PACKAGE + ": " + type);
        }
    }

    /**
     * Registers a C→S packet on {@code channel}; the only call site of
     * {@code consumer*} for C→S. Only classes of {@link #C2S_PACKAGE} are
     * accepted, whose fields ArchUnit limits to intents (SEC-04).
     */
    public static <M> void register(SimpleChannel channel, int index, Class<M> type, BiConsumer<M, FriendlyByteBuf> encoder,
                                    Function<FriendlyByteBuf, M> decoder, M malformed, BiConsumer<M, ServerPlayer> handler) {
        requireC2sType(type);
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
