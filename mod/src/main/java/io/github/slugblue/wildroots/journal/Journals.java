package io.github.slugblue.wildroots.journal;

import io.github.slugblue.wildroots.client.ClientPacketHandlers;
import io.github.slugblue.wildroots.core.safety.DispCodec;
import io.github.slugblue.wildroots.core.safety.EntryPoint;
import io.github.slugblue.wildroots.core.safety.Guarded;
import io.github.slugblue.wildroots.network.WrNetwork;
import io.github.slugblue.wildroots.network.c2s.JournalActionPacket;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.Supplier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.capabilities.RegisterCapabilitiesEvent;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;

/**
 * Server side of the field journal (narrative §4.1; docs/05-architecture.md
 * §6.1, §14): the capability, unlocking entries, {@code JournalSync} to the
 * owner and the guarded {@code JournalAction} handler. The server decides
 * what an entry says; the client only draws it.
 */
public final class Journals {

    /** Where S→C journal packets go; GameTests replace it (players without a connection). */
    public interface Sender {
        void sync(ServerPlayer player, JournalSyncPacket packet);

        void toast(ServerPlayer player, ToastPacket packet);
    }

    private static final Sender NETWORK = new Sender() {
        @Override
        public void sync(ServerPlayer player, JournalSyncPacket packet) {
            if (player.connection != null) {
                WrNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), packet);
            }
        }

        @Override
        public void toast(ServerPlayer player, ToastPacket packet) {
            if (player.connection != null) {
                WrNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), packet);
            }
        }
    };

    private static volatile Sender sender = NETWORK;

    private Journals() {
    }

    /** Replaces the sender (tests) and returns the previous one. */
    public static Sender setSender(Sender s) {
        Sender old = sender;
        sender = s == null ? NETWORK : s;
        return old;
    }

    static Sender sender() {
        return sender;
    }

    // ------------------------------------------------------------------ network

    /** S→C packets of the journal; called from {@link WrNetwork#register}. */
    public static void registerPackets() {
        WrNetwork.registerToClient(JournalSyncPacket.class, JournalSyncPacket::encode, JournalSyncPacket::clientDecode,
                Journals::onClientSync);
    }

    private static void onClientSync(JournalSyncPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> ClientPacketHandlers.journalSync(msg)));
        ctx.get().setPacketHandled(true);
    }

    /**
     * {@code JournalAction} from a client (registered by
     * {@code network.C2sPackets} through {@code ServerPacketGuard}, SEC-01):
     * only an entry this player has unlocked, only a known action; a refused
     * packet changes nothing and is not answered.
     */
    @EntryPoint
    public static void onAction(JournalActionPacket msg, ServerPlayer player) {
        Guarded.run("journal action", player.getUUID(), () -> of(player).ifPresent(j -> {
            int before = j.revision();
            if (j.apply(msg.entry(), msg.action()) && j.revision() != before) {
                j.entry(msg.entry().getPath()).ifPresent(e -> sender.sync(player,
                        new JournalSyncPacket(false, false, owner(player), List.of(e))));
            }
        }));
    }

    // ------------------------------------------------------------------ capability

    /** Mod bus. */
    public static void onRegisterCapabilities(RegisterCapabilitiesEvent event) {
        event.register(JournalData.class);
    }

    /** Forge bus: every player carries a journal. */
    @EntryPoint
    public static void onAttach(AttachCapabilitiesEvent<Entity> event) {
        if (event.getObject() instanceof Player) {
            Guarded.run("journal attach", "player",
                    () -> event.addCapability(JournalCapability.ID, new JournalCapability.Provider()));
        }
    }

    /** Forge bus: the journal survives death and the return from the End (narrative §4.1). */
    @EntryPoint
    public static void onClone(PlayerEvent.Clone event) {
        Guarded.run("journal clone", event.getEntity().getUUID(), () -> {
            Player old = event.getOriginal();
            old.reviveCaps();
            try {
                Optional<JournalData> from = of(old);
                Optional<JournalData> to = of(event.getEntity());
                if (from.isPresent() && to.isPresent()) {
                    to.get().copyFrom(from.get());
                }
            } finally {
                old.invalidateCaps();
            }
        });
    }

    /** Forge bus: a full sync on login. */
    @EntryPoint
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer p) {
            Guarded.run("journal login", p.getUUID(), () -> syncFull(p, false));
        }
    }

    public static Optional<JournalData> of(Player player) {
        return player.getCapability(JournalCapability.CAPABILITY).resolve();
    }

    /** The online player {@code id} of {@code server} (the planter of a plant), if any. */
    public static Optional<ServerPlayer> online(MinecraftServer server, UUID id) {
        return id == null || server == null ? Optional.empty()
                : Optional.ofNullable(server.getPlayerList().getPlayer(id));
    }

    static String owner(ServerPlayer player) {
        String n = player.getGameProfile().getName();
        return n == null ? "" : n.length() > JournalSyncPacket.MAX_OWNER ? n.substring(0, JournalSyncPacket.MAX_OWNER) : n;
    }

    // ------------------------------------------------------------------ entries

    /** Sends the whole journal; {@code open} opens the screen (the item was used). */
    public static void syncFull(ServerPlayer player, boolean open) {
        of(player).ifPresent(j -> sender.sync(player, new JournalSyncPacket(true, open, owner(player), j.ordered())));
    }

    /**
     * Unlocks {@code id}; a new or changed entry goes to the client as a
     * delta. Returns the outcome ({@link JournalData.Unlock#NONE} when
     * nothing changed or the journal refuses).
     */
    public static JournalData.Unlock unlock(ServerPlayer player, String id, List<String> args,
                                            List<DispCodec.Line> rows) {
        Optional<JournalData> j = of(player);
        if (j.isEmpty()) {
            return JournalData.Unlock.NONE;
        }
        JournalData.Unlock u = j.get().unlock(id, args, rows);
        if (u != JournalData.Unlock.NONE) {
            j.get().entry(id).ifPresent(e -> sender.sync(player,
                    new JournalSyncPacket(false, false, owner(player), List.of(e))));
        }
        return u;
    }

    public static JournalData.Unlock unlock(ServerPlayer player, String id, String... args) {
        return unlock(player, id, List.of(args), List.of());
    }

    /** Test helper sender that records into two consumers. */
    public static Sender recording(BiConsumer<ServerPlayer, JournalSyncPacket> syncs,
                                   BiConsumer<ServerPlayer, ToastPacket> toasts) {
        return new Sender() {
            @Override
            public void sync(ServerPlayer player, JournalSyncPacket packet) {
                syncs.accept(player, packet);
            }

            @Override
            public void toast(ServerPlayer player, ToastPacket packet) {
                toasts.accept(player, packet);
            }
        };
    }
}
