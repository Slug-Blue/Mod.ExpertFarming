package io.github.slugblue.wildroots.client;

import com.mojang.logging.LogUtils;
import io.github.slugblue.wildroots.data.sync.DefinitionsSync;
import io.github.slugblue.wildroots.data.sync.DefinitionsSyncPacket;
import io.github.slugblue.wildroots.data.sync.SyncAssembler;
import io.github.slugblue.wildroots.data.sync.SyncedDefinitions;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.chat.Component;
import org.slf4j.Logger;

/**
 * Client handlers of S→C packets (docs/05-architecture.md §15): reached
 * only through {@code DistExecutor} from the common packet classes.
 */
public final class ClientPacketHandlers {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final SyncAssembler ASSEMBLER = new SyncAssembler();

    private ClientPacketHandlers() {
    }

    /** A part of the definitions sync; a broken sync disconnects with a message instead of crashing (SEC-31). */
    public static void definitionsSync(DefinitionsSyncPacket packet) {
        SyncAssembler.Step step = packet == DefinitionsSync.BROKEN
                ? new SyncAssembler.Failed("packet does not decode") : ASSEMBLER.accept(packet);
        if (step instanceof SyncAssembler.Done done) {
            ClientDefinitions.set(done.definitions());
        } else if (step instanceof SyncAssembler.Failed failed) {
            LOGGER.error("Wild Roots: definitions sync rejected: {}", failed.reason());
            ClientPacketListener connection = Minecraft.getInstance().getConnection();
            if (connection != null) {
                connection.getConnection().disconnect(Component.translatable(DefinitionsSync.FAILED_KEY));
            }
        }
    }

    /** {@code JournalSync}: store the entries; open the screen if the journal was used (WR-181). */
    public static void journalSync(io.github.slugblue.wildroots.journal.JournalSyncPacket packet) {
        if (packet == io.github.slugblue.wildroots.journal.JournalSyncPacket.BROKEN) {
            LOGGER.warn("Wild Roots: a journal sync did not decode; kept the previous copy (a later open repairs it)");
            return;
        }
        ClientJournal.apply(packet);
        if (packet.open()) {
            JournalScreen.openOrRefresh();
        } else if (Minecraft.getInstance().screen instanceof JournalScreen s) {
            s.rebuild();
        }
    }

    /** {@code Toast}: a system toast with the server's title and body lines (WR-182). */
    public static void toast(io.github.slugblue.wildroots.journal.ToastPacket packet) {
        if (packet == io.github.slugblue.wildroots.journal.ToastPacket.BROKEN) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        net.minecraft.client.gui.components.toasts.SystemToast.add(mc.getToasts(),
                net.minecraft.client.gui.components.toasts.SystemToast.SystemToastIds.PERIODIC_NOTIFICATION,
                io.github.slugblue.wildroots.core.safety.DispCodec.toComponent(packet.title()),
                io.github.slugblue.wildroots.core.safety.DispCodec.toComponent(packet.body()));
    }

    /** Drops a partial sync; called when the player leaves the server. */
    static void resetAssembler() {
        ASSEMBLER.reset();
    }

    /** Current client view, for code that runs only on the client. */
    public static SyncedDefinitions definitions() {
        return ClientDefinitions.get();
    }
}
