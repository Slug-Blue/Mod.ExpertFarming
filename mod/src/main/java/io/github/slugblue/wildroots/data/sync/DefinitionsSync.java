package io.github.slugblue.wildroots.data.sync;

import io.github.slugblue.wildroots.core.safety.EntryPoint;
import com.mojang.logging.LogUtils;
import io.github.slugblue.wildroots.client.ClientPacketHandlers;
import io.github.slugblue.wildroots.core.line.LineManifest;
import io.github.slugblue.wildroots.core.line.LineRegistry;
import io.github.slugblue.wildroots.core.safety.Guarded;
import io.github.slugblue.wildroots.data.Definitions;
import io.github.slugblue.wildroots.data.DefinitionsSnapshot;
import io.github.slugblue.wildroots.data.OperatorNotice;
import io.github.slugblue.wildroots.network.WrNetwork;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.OnDatapackSyncEvent;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;
import org.slf4j.Logger;

/**
 * Sends {@link DefinitionsSyncPacket}s on {@code OnDatapackSyncEvent}: to
 * the joining player, or to everyone after {@code /reload}
 * (docs/05-architecture.md §8.3; SEC-31). The packets of a snapshot are
 * built once and reused for every player.
 */
public final class DefinitionsSync {

    private static final Logger LOGGER = LogUtils.getLogger();
    /** Message to operators when data-pack species had to be left out of the sync. */
    public static final String TOO_LARGE_KEY = "wildroots.message.sync_too_large";
    /** Client disconnect message when a sync cannot be read. */
    public static final String FAILED_KEY = "wildroots.message.sync_failed";
    /** Marker the client decoder returns for a broken packet. */
    public static final DefinitionsSyncPacket BROKEN = new DefinitionsSyncPacket(-1, -1, 0, new byte[0]);

    /** Packets of one snapshot and the data-pack species left out of them. */
    public record Plan(List<DefinitionsSyncPacket> packets, List<ResourceLocation> dropped, int bytes) {
    }

    private static final AtomicInteger SYNC_IDS = new AtomicInteger();
    private static volatile DefinitionsSnapshot plannedFor;
    private static volatile Plan plan;
    /** Where packets go: a player, or everybody for {@code null}. GameTests replace it. */
    private static volatile BiConsumer<ServerPlayer, DefinitionsSyncPacket> sender = DefinitionsSync::sendOverNetwork;

    private DefinitionsSync() {
    }

    /** Registers the S→C packet on the mod channel. */
    public static void register() {
        WrNetwork.registerToClient(DefinitionsSyncPacket.class, DefinitionsSyncPacket::encode,
                DefinitionsSync::clientDecode, DefinitionsSync::onClientReceive);
    }

    /** Client decoder: never throws, a broken packet is {@link #BROKEN} (SEC-31). */
    public static DefinitionsSyncPacket clientDecode(net.minecraft.network.FriendlyByteBuf buf) {
        try {
            return DefinitionsSyncPacket.decode(buf);
        } catch (RuntimeException e) {
            return BROKEN;
        }
    }

    private static void onClientReceive(DefinitionsSyncPacket msg, java.util.function.Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> ClientPacketHandlers.definitionsSync(msg)));
        ctx.get().setPacketHandled(true);
    }

    /** Replaces the packet sender (GameTests) and returns the previous one. */
    public static BiConsumer<ServerPlayer, DefinitionsSyncPacket> setSender(BiConsumer<ServerPlayer, DefinitionsSyncPacket> s) {
        BiConsumer<ServerPlayer, DefinitionsSyncPacket> old = sender;
        sender = s;
        return old;
    }

    private static void sendOverNetwork(ServerPlayer player, DefinitionsSyncPacket p) {
        if (player != null) {
            if (player.connection != null) { // not yet (or no longer) connected: nothing to send to
                WrNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), p);
            }
        } else {
            WrNetwork.CHANNEL.send(PacketDistributor.ALL.noArg(), p);
        }
    }

    /** {@code OnDatapackSyncEvent} handler (Forge bus). */
    @EntryPoint
    public static void onDatapackSync(OnDatapackSyncEvent event) {
        Guarded.run("definitions sync", "OnDatapackSyncEvent", () -> {
            Plan p = planFor(Definitions.current());
            if (!p.dropped().isEmpty()) {
                // after /reload every operator online, on join the joining operator (as OperatorNotice)
                OperatorNotice.toOperators(event.getPlayer() == null ? event.getPlayerList().getPlayers()
                        : List.of(event.getPlayer()), Component.translatable(TOO_LARGE_KEY, p.dropped().size()));
            }
            for (DefinitionsSyncPacket packet : p.packets()) {
                sender.accept(event.getPlayer(), packet);
            }
        });
    }

    /** The packets of {@code snapshot}, built once per snapshot. */
    public static synchronized Plan planFor(DefinitionsSnapshot snapshot) {
        if (plannedFor != snapshot || plan == null) {
            List<String> lines = LineRegistry.get().enabled().stream().map(LineManifest.Line::id).toList();
            plan = plan(SyncedDefinitions.of(snapshot, lines), SYNC_IDS.incrementAndGet());
            plannedFor = snapshot;
            if (!plan.dropped().isEmpty()) {
                LOGGER.error("Wild Roots: definitions sync over {} B, {} data-pack species not sent to clients: {}",
                        DefinitionsSyncPacket.TOTAL_MAX, plan.dropped().size(), plan.dropped());
            }
        }
        return plan;
    }

    /**
     * Serialises {@code defs}; above {@value DefinitionsSyncPacket#TOTAL_MAX}
     * B drops species of data packs (not {@code wildroots}) from the end, with
     * their cultivars, until it fits.
     */
    public static Plan plan(SyncedDefinitions defs, int syncId) {
        byte[] bytes = DefinitionsSyncPacket.serialise(defs);
        List<ResourceLocation> dropped = new ArrayList<>();
        SyncedDefinitions current = defs;
        while (bytes.length > DefinitionsSyncPacket.TOTAL_MAX) {
            List<SyncedDefinitions.Species> species = new ArrayList<>(current.species());
            int excess = bytes.length - DefinitionsSyncPacket.TOTAL_MAX;
            Set<ResourceLocation> drop = new HashSet<>();
            int freed = 0;
            for (int i = species.size() - 1; i >= 0 && freed <= excess; i--) {
                SyncedDefinitions.Species s = species.get(i);
                if (s.id().getNamespace().equals("wildroots")) {
                    continue;
                }
                drop.add(s.id());
                List<SyncedDefinitions.Cultivar> own = current.cultivars().stream()
                        .filter(c -> c.species().equals(s.id())).toList();
                freed += DefinitionsSyncPacket.serialise(new SyncedDefinitions(List.of(), List.of(s), own)).length - 3;
            }
            if (drop.isEmpty()) {
                throw new IllegalStateException("built-in definitions alone exceed " + DefinitionsSyncPacket.TOTAL_MAX + " B");
            }
            species.removeIf(s -> drop.contains(s.id()));
            List<SyncedDefinitions.Cultivar> cultivars = new ArrayList<>(current.cultivars());
            cultivars.removeIf(c -> drop.contains(c.species()));
            dropped.addAll(drop);
            current = new SyncedDefinitions(current.lines(), species, cultivars);
            bytes = DefinitionsSyncPacket.serialise(current);
        }
        return new Plan(DefinitionsSyncPacket.split(syncId, bytes), List.copyOf(dropped), bytes.length);
    }
}
