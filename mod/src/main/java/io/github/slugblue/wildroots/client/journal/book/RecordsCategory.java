package io.github.slugblue.wildroots.client.journal.book;

import io.github.slugblue.wildroots.client.JournalPages;
import io.github.slugblue.wildroots.core.Ids;
import io.github.slugblue.wildroots.journal.JournalData;
import java.util.List;
import java.util.Optional;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * The "My records" categories (journal v2 §2.2, §4.2): built by code from
 * the server's entries, shown by the conditions of narrative §4.2 (the
 * same as v1). There is no Tools category (В5): tools are guide entries.
 * Until WR-181e their pages are the v1 blocks laid out as text.
 */
@OnlyIn(Dist.CLIENT)
public enum RecordsCategory {
    SPECIES(JournalPages.Tab.SPECIES, "species", "wild_carrot_root"),
    CULTIVARS(JournalPages.Tab.CULTIVARS, "cultivar", "eastern_carrot"),
    LINES(JournalPages.Tab.LINES, "cultivar", "carrot"),
    NOTES(JournalPages.Tab.NOTES, "hint", "field_journal"),
    BREEDER(JournalPages.Tab.BREEDER, "note", "seed_lot");

    public final JournalPages.Tab tab;
    private final String kind;
    public final ResourceLocation icon;

    RecordsCategory(JournalPages.Tab tab, String kind, String icon) {
        this.tab = tab;
        this.kind = kind;
        this.icon = Ids.mod(icon);
    }

    public String titleKey() {
        return tab.key;
    }

    public boolean visible(List<JournalData.Entry> entries) {
        return JournalPages.visible(tab, entries);
    }

    public boolean hasNew(List<JournalData.Entry> entries) {
        return JournalPages.hasNew(tab, entries);
    }

    /** The count shown in the contents: shown entries, and for cultivars also all of them ("1 of 2", §3.7). */
    public Count count(List<JournalData.Entry> entries) {
        List<JournalData.Entry> mine = entries.stream().filter(e -> e.id().startsWith(kind + ".")).toList();
        return switch (this) {
            case CULTIVARS -> new Count((int) mine.stream().filter(e -> !e.is(JournalData.LOCKED)).count(),
                    Optional.of(mine.size()));
            case LINES -> new Count((int) mine.stream().filter(e -> !e.is(JournalData.LOCKED)).count(), Optional.empty());
            default -> new Count(mine.size(), Optional.empty());
        };
    }

    /** {@code shown}, or {@code shown of total}. */
    public record Count(int shown, Optional<Integer> total) {
    }

    public static Optional<RecordsCategory> byName(String name) {
        for (RecordsCategory c : values()) {
            if (c.name().equals(name)) {
                return Optional.of(c);
            }
        }
        return Optional.empty();
    }

    /** The category that shows a journal entry id ({@code hint.x} → NOTES), if any. */
    public static Optional<RecordsCategory> of(String entryId) {
        if (entryId.startsWith("species.")) {
            return Optional.of(SPECIES);
        }
        if (entryId.startsWith("cultivar.")) {
            return Optional.of(CULTIVARS);
        }
        if (entryId.startsWith("hint.")) {
            return Optional.of(NOTES);
        }
        if (entryId.startsWith("note.")) {
            return Optional.of(BREEDER);
        }
        return Optional.empty();
    }
}
