package io.github.slugblue.wildroots.client.journal.search;

import io.github.slugblue.wildroots.WildRoots;
import io.github.slugblue.wildroots.core.Ids;
import io.github.slugblue.wildroots.network.WrNetwork;
import io.github.slugblue.wildroots.network.c2s.JournalActionPacket;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Every {@code JournalAction} the journal sends goes through here (journal
 * v2 §5.1): queued, at most 4 a second, drained on the client tick so it
 * keeps going after the journal closes; dropped on leaving the server.
 */
@OnlyIn(Dist.CLIENT)
@Mod.EventBusSubscriber(modid = WildRoots.MOD_ID, value = Dist.CLIENT)
public final class JournalActions {

    /** One action for one journal entry. */
    record Action(String entryId, int action) {
    }

    private static final ActionQueue<Action> QUEUE = new ActionQueue<>();

    private JournalActions() {
    }

    public static void markRead(String entryId) {
        QUEUE.offer(new Action(entryId, JournalActionPacket.MARK_READ));
    }

    public static void pin(String entryId, boolean pin) {
        QUEUE.offer(new Action(entryId, pin ? JournalActionPacket.PIN : JournalActionPacket.UNPIN));
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || QUEUE.size() == 0 || Minecraft.getInstance().getConnection() == null) {
            return;
        }
        QUEUE.drain(System.currentTimeMillis(),
                a -> WrNetwork.CHANNEL.sendToServer(new JournalActionPacket(Ids.mod(a.entryId()), a.action())));
    }

    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        QUEUE.clear();
    }
}
