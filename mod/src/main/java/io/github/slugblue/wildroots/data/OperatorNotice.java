package io.github.slugblue.wildroots.data;

import io.github.slugblue.wildroots.core.safety.EntryPoint;
import io.github.slugblue.wildroots.core.safety.Guarded;
import java.util.List;
import java.util.function.BiConsumer;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.OnDatapackSyncEvent;

/**
 * One chat message to operators when the last data load had errors
 * (docs/05-architecture.md §8.2 item 5; SEC-27; WR-126): on join (the
 * joining operator) and after {@code /reload} (every operator online).
 * Players without {@code permission ≥ 2} get nothing. The details stay in
 * the server log.
 */
public final class OperatorNotice {

    /** Translation key of the message: errors, skipped species, skipped cultivars. */
    public static final String KEY = "wildroots.message.load_problems";
    /** Permission level of an operator. */
    public static final int OPERATOR = 2;

    /** Where messages go; GameTests replace it to observe them. */
    private static volatile BiConsumer<ServerPlayer, Component> sink = ServerPlayer::sendSystemMessage;

    private OperatorNotice() {
    }

    /** Replaces the message sink (GameTests) and returns the previous one. */
    public static BiConsumer<ServerPlayer, Component> setSink(BiConsumer<ServerPlayer, Component> s) {
        BiConsumer<ServerPlayer, Component> old = sink;
        sink = s;
        return old;
    }

    /** The message for {@code stats}. */
    public static Component message(DefinitionsSnapshot.Stats stats) {
        return Component.translatable(KEY, stats.errors(), stats.speciesSkipped(), stats.cultivarsSkipped());
    }

    /** {@code OnDatapackSyncEvent} handler (Forge bus): join has a player, {@code /reload} has none. */
    @EntryPoint
    public static void onDatapackSync(OnDatapackSyncEvent event) {
        Guarded.run("operator notice", "OnDatapackSyncEvent",
                () -> notify(event.getPlayerList().getPlayers(), event.getPlayer(), Definitions.current().stats()));
    }

    /** Sends the message to {@code joining}, or to every {@code online} player when it is {@code null}, if they are operators. */
    public static void notify(List<ServerPlayer> online, ServerPlayer joining, DefinitionsSnapshot.Stats stats) {
        if (stats.errors() == 0) {
            return;
        }
        Component msg = message(stats);
        List<ServerPlayer> targets = joining != null ? List.of(joining) : online;
        for (ServerPlayer p : targets) {
            if (p.hasPermissions(OPERATOR)) {
                sink.accept(p, msg);
            }
        }
    }

    /** Sends {@code message} to every operator in {@code online}. */
    public static void toOperators(List<ServerPlayer> online, Component message) {
        for (ServerPlayer p : online) {
            if (p.hasPermissions(OPERATOR)) {
                sink.accept(p, message);
            }
        }
    }
}
