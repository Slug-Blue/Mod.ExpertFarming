package io.github.slugblue.wildroots.client.journal.book;

import io.github.slugblue.wildroots.WildRoots;
import java.util.HashSet;
import java.util.Set;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * What the journal remembers between openings until the player leaves the
 * world (journal v2 §3.11): the last page, the history, and which entries
 * were new when it was last closed. The first opening, and an opening
 * after something new came, start on the contents.
 */
@OnlyIn(Dist.CLIENT)
@Mod.EventBusSubscriber(modid = WildRoots.MOD_ID, value = Dist.CLIENT)
public final class JournalSession {

    static BookLocation last;
    static final BookHistory<BookLocation> HISTORY = new BookHistory<>();
    static Set<String> newSeen = new HashSet<>();

    private JournalSession() {
    }

    /** Where to open: the last page, unless there is a new entry that was not new at the last close. */
    static BookLocation openAt(Set<String> newNow) {
        if (last == null || !newSeen.containsAll(newNow)) {
            return BookLocation.CONTENTS;
        }
        return last;
    }

    static void closed(BookLocation at, Set<String> newNow) {
        last = at;
        newSeen = new HashSet<>(newNow);
    }

    static void reset() {
        last = null;
        HISTORY.clear();
        newSeen = new HashSet<>();
    }

    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        reset();
    }
}
