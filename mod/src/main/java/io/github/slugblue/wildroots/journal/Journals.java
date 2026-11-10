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
        WrNetwork.registerToClient(ToastPacket.class, ToastPacket::encode, ToastPacket::clientDecode,
                Journals::onClientToast);
    }

    private static void onClientToast(ToastPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> ClientPacketHandlers.toast(msg)));
        ctx.get().setPacketHandled(true);
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
                j.entry(msg.entry().getPath()).ifPresent(e -> delta(player, j, e));
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
            Guarded.run("journal attach", "player", () -> {
                JournalCapability.Provider provider = new JournalCapability.Provider();
                event.addCapability(JournalCapability.ID, provider);
                event.addListener(provider::invalidate);
            });
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

    /** Tests: players the planter lookup also finds (GameTest players are not in the player list). */
    private static volatile java.util.function.Function<UUID, ServerPlayer> extraOnline = id -> null;

    /** Replaces the extra lookup (tests) and returns the previous one; {@code null} restores none. */
    public static java.util.function.Function<UUID, ServerPlayer> setExtraOnline(
            java.util.function.Function<UUID, ServerPlayer> f) {
        java.util.function.Function<UUID, ServerPlayer> old = extraOnline;
        extraOnline = f == null ? id -> null : f;
        return old;
    }

    /** The online player {@code id} of {@code server} (the planter of a plant), if any. */
    public static Optional<ServerPlayer> online(MinecraftServer server, UUID id) {
        if (id == null || server == null) {
            return Optional.empty();
        }
        ServerPlayer p = server.getPlayerList().getPlayer(id);
        return Optional.ofNullable(p != null ? p : extraOnline.apply(id));
    }

    static String owner(ServerPlayer player) {
        String n = player.getGameProfile().getName();
        if (n == null) {
            return "";
        }
        // cut by code points, never inside a surrogate pair
        return n.codePointCount(0, n.length()) > JournalSyncPacket.MAX_OWNER
                ? n.substring(0, n.offsetByCodePoints(0, JournalSyncPacket.MAX_OWNER)) : n;
    }

    /** Server tick for the sync repair interval (T3). */
    static long now(ServerPlayer player) {
        return player.getServer() == null ? 0 : player.getServer().getTickCount();
    }

    // ------------------------------------------------------------------ entries

    /** Sends the whole journal; {@code open} opens the screen. */
    public static void syncFull(ServerPlayer player, boolean open) {
        of(player).ifPresent(j -> {
            sender.sync(player, new JournalSyncPacket(true, open, owner(player), j.ordered()));
            j.markFullSynced(now(player));
        });
    }

    /**
     * The journal was used (review 1, M2): the client already holds every
     * entry (full sync at login, a delta for every change), so only "open"
     * goes out, an empty delta; a full sync only if something changed
     * without reaching the client, or (T3) to repair a client copy that may
     * have dropped a delta, at most once per
     * {@value JournalData#REPAIR_INTERVAL_TICKS} ticks
     * ({@link JournalData#openNeedsFull}).
     */
    public static void open(ServerPlayer player) {
        // (cooldown in FieldJournalItem)
        of(player).ifPresent(j -> {
            if (j.openNeedsFull(now(player))) {
                syncFull(player, true);
            } else {
                sender.sync(player, new JournalSyncPacket(false, true, owner(player), List.of()));
            }
        });
    }

    /** One changed entry to the client; the client is then up to date with this revision. */
    static void delta(ServerPlayer player, JournalData j, JournalData.Entry e) {
        // every visible change goes out as such a delta, so the client holds this revision afterwards
        sender.sync(player, new JournalSyncPacket(false, false, owner(player), List.of(e)));
        j.markDeltaSynced();
    }

    /**
     * Unlocks {@code id}; a new or changed entry goes to the client as a
     * delta. Returns the outcome ({@link JournalData.Unlock#NONE} when
     * nothing changed or the journal refuses).
     */
    public static JournalData.Unlock unlock(ServerPlayer player, String id, List<String> args,
                                            List<DispCodec.Line> rows) {
        return unlock(player, id, args, rows, player.level().getGameTime());
    }

    /**
     * {@link #unlock} at {@code gameTime}: a hint of a line or feature that
     * is not in this build is refused (WR-182); a new entry shows the "New
     * journal entry" toast at most once in 30 s, the others accumulate
     * silently (narrative §4.1).
     */
    public static JournalData.Unlock unlock(ServerPlayer player, String id, List<String> args,
                                            List<DispCodec.Line> rows, long gameTime) {
        Optional<JournalData> j = of(player);
        if (j.isEmpty() || !JournalHints.enabled(id)) {
            return JournalData.Unlock.NONE;
        }
        JournalData.Unlock u = j.get().unlock(id, args, rows);
        if (u != JournalData.Unlock.NONE) {
            j.get().entry(id).ifPresent(e -> delta(player, j.get(), e));
        }
        if (u == JournalData.Unlock.NEW) {
            newEntryToast(player, j.get(), id, gameTime);
        }
        return u;
    }

    /**
     * The "new entry" toast; runs after the entry is committed and can never
     * undo it (review 1, m2): a title too long for an argument falls back to
     * the generic one, and a failing send is isolated.
     */
    static void newEntryToast(ServerPlayer player, JournalData j, String id, long gameTime) {
        if (j.takeToast(gameTime)) {
            DispCodec.Line title = DispCodec.Line.of(NEW_ENTRY_KEY, DispCodec.keyArg(titleKey(id)));
            if (!DispCodec.valid(title)) {
                title = DispCodec.Line.of(NEW_ENTRY_KEY, DispCodec.keyArg("wildroots.journal.tab.notes"));
            }
            ToastPacket t = new ToastPacket(title, DispCodec.Line.of(NEW_ENTRY_BODY_KEY));
            Guarded.run("journal toast", player.getUUID(), () -> sender.toast(player, t));
        }
    }

    public static final String NEW_ENTRY_KEY = "wildroots.message.journal.new_entry";
    public static final String NEW_ENTRY_BODY_KEY = "wildroots.message.journal.new_entry.body";

    /** Title key of an entry for the toast: hint title, species or cultivar name, tool item name. */
    static String titleKey(String id) {
        String kind = id.substring(0, id.indexOf('.'));
        String rest = id.substring(id.indexOf('.') + 1);
        return switch (kind) {
            case "hint" -> "wildroots.journal.hint." + (rest.contains(".") ? rest.substring(0, rest.indexOf('.')) : rest)
                    + ".title";
            case "species" -> "wildroots.species." + rest;
            case "cultivar" -> "wildroots.cultivar." + rest;
            case "tool" -> "wildroots.journal.tab.tools";
            default -> "wildroots.journal.tab.breeder";
        };
    }

    /**
     * A per-line hint ({@code stagnation}, {@code missing_allele}): once
     * for the line, again no sooner than three generations later; the line
     * name is the argument.
     */
    public static boolean unlockForLine(ServerPlayer player, String hint, String lineName, int generation) {
        Optional<JournalData> j = of(player);
        String key = LineProgress.lineKey(lineName);
        String id = "hint." + hint + "." + key;
        if (j.isEmpty() || !JournalHints.PER_LINE.contains(hint) || !JournalHints.enabled(id)
                || !LineProgress.mayFire(j.get(), hint, key, generation)) {
            return false;
        }
        List<String> args = List.of(lineName.length() > DispCodec.MAX_ARG_LENGTH
                ? lineName.substring(0, DispCodec.MAX_ARG_LENGTH) : lineName);
        long now = player.level().getGameTime();
        if (j.get().has(id)) {
            if (!j.get().refire(id, args)) {
                return false;
            }
            j.get().entry(id).ifPresent(e -> delta(player, j.get(), e));
            newEntryToast(player, j.get(), id, now);
            return true;
        }
        return unlock(player, id, args, List.of(), now) == JournalData.Unlock.NEW;
    }

    /** A toast that is not throttled (a bred cultivar, GDD §6.2 item 4). */
    public static void toast(ServerPlayer player, ToastPacket packet) {
        sender.toast(player, packet);
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
