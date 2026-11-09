package io.github.slugblue.wildroots.client;

import io.github.slugblue.wildroots.journal.JournalData;
import io.github.slugblue.wildroots.journal.JournalSyncPacket;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The client's copy of its own journal: exactly the entries the server sent
 * ({@code JournalSync}); nothing is computed here (docs/05-architecture.md
 * §15). Cleared when the player leaves the server.
 */
public final class ClientJournal {

    private static final Map<String, JournalData.Entry> ENTRIES = new LinkedHashMap<>();
    private static String owner = "";

    private ClientJournal() {
    }

    /** Applies a full sync (replace) or a delta (merge by id). */
    public static synchronized void apply(JournalSyncPacket p) {
        if (p.full()) {
            ENTRIES.clear();
        }
        if (!p.owner().isEmpty()) {
            owner = p.owner();
        }
        for (JournalData.Entry e : p.entries()) {
            ENTRIES.put(e.id(), e);
        }
    }

    public static synchronized List<JournalData.Entry> entries() {
        List<JournalData.Entry> l = new ArrayList<>(ENTRIES.values());
        l.sort(Comparator.comparingInt(JournalData.Entry::order));
        return l;
    }

    public static synchronized String owner() {
        return owner;
    }

    static synchronized void clear() {
        ENTRIES.clear();
        owner = "";
    }
}
