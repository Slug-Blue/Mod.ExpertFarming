package io.github.slugblue.wildroots.client.journal;

import io.github.slugblue.wildroots.journal.JournalData;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * Which guide entries and pages the player sees (journal v2 §2.3). The one
 * rule: an entry is shown only if the server's journal holds an entry that
 * opens it ({@code unlock}); the client computes nothing about genetics.
 *
 * <ul>
 *   <li>{@code unlock: []} — always open;</li>
 *   <li>no journal entry of {@code unlock} — closed: not shown at all (no
 *       row, no lock); a category with no open entry is hidden too;</li>
 *   <li>at least one — open; {@code kind.*} matches any entry of the kind
 *       ({@code species.*});</li>
 *   <li>new — at least one journal entry that opens it carries
 *       {@code NEW}; a category is new if one of its open entries is;</li>
 *   <li>a page with its own {@code unlock} follows the same rule and is
 *       simply skipped when closed; an entry with no shown page is
 *       closed.</li>
 * </ul>
 * A silhouette ({@code LOCKED}, a cultivar not yet bred) opens nothing:
 * the player has not met that cultivar. {@code showAllGuides} (В4) opens
 * every entry and page; "new" still follows the journal.
 *
 * <p>Immutable snapshot: build a new one after each {@code JournalSync}
 * or guide reload.
 */
@OnlyIn(Dist.CLIENT)
public final class GuideUnlocks {

    private final GuideBook book;
    private final Map<String, Integer> flags = new LinkedHashMap<>();
    private final boolean showAll;
    private final Predicate<GuidePage> available;
    private final Map<String, List<GuidePage>> pages = new LinkedHashMap<>();

    /**
     * @param available pages that can be drawn at all (a crafting page whose
     *                  recipe the client lacks is not, §3.2); others are
     *                  skipped like closed ones
     */
    public GuideUnlocks(GuideBook book, Collection<JournalData.Entry> journal, boolean showAll,
                        Predicate<GuidePage> available) {
        this.book = book;
        this.showAll = showAll;
        this.available = available;
        for (JournalData.Entry e : journal) {
            if (!e.is(JournalData.LOCKED)) {
                flags.put(e.id(), e.flags());
            }
        }
        for (GuideEntry e : book.entries()) {
            pages.put(e.id(), computePages(e));
        }
    }

    public GuideUnlocks(GuideBook book, Collection<JournalData.Entry> journal, boolean showAll) {
        this(book, journal, showAll, p -> true);
    }

    /** Whether the journal holds {@code journalId} (not a silhouette), e.g. {@code tool.hand_lens}. */
    public boolean has(String journalId) {
        return flags.containsKey(journalId);
    }

    public GuideBook book() {
        return book;
    }

    static boolean matches(String unlock, String journalId) {
        if (unlock.endsWith(".*")) {
            return journalId.startsWith(unlock.substring(0, unlock.length() - 1));
        }
        return unlock.equals(journalId);
    }

    /** Journal entries (not silhouettes) that match one of {@code unlock}. */
    List<String> openers(List<String> unlock) {
        List<String> out = new ArrayList<>();
        for (String id : flags.keySet()) {
            for (String u : unlock) {
                if (matches(u, id)) {
                    out.add(id);
                    break;
                }
            }
        }
        return out;
    }

    /**
     * The rule for any {@code unlock} list (entry, page, scene step §5.8):
     * empty, {@code showAllGuides}, or a journal entry matches one of it.
     */
    public boolean opened(List<String> unlock) {
        return showAll || unlock.isEmpty() || !openers(unlock).isEmpty();
    }

    private List<GuidePage> computePages(GuideEntry e) {
        if (!opened(e.unlock())) {
            return List.of();
        }
        List<GuidePage> out = new ArrayList<>();
        for (GuidePage p : e.pages()) {
            if (opened(p.unlock()) && available.test(p)) {
                out.add(p);
            }
        }
        return List.copyOf(out);
    }

    /** The pages of an entry the player sees, in order; empty if the entry is closed. */
    public List<GuidePage> pages(GuideEntry e) {
        return pages.getOrDefault(e.id(), List.of());
    }

    public boolean isOpen(GuideEntry e) {
        return !pages(e).isEmpty();
    }

    /** Whether the entry {@code id} exists and is open (a link to anything else is plain text, §2.4). */
    public boolean isOpen(String id) {
        return book.entry(id).map(this::isOpen).orElse(false);
    }

    public Optional<GuideEntry> openEntry(String id) {
        return book.entry(id).filter(this::isOpen);
    }

    /** "New": at least one journal entry that opens it is new. An always-open entry is never new. */
    public boolean isNew(GuideEntry e) {
        return isOpen(e) && !newOpeners(e).isEmpty();
    }

    /**
     * The new journal entries that open {@code e} or one of its shown pages:
     * the client sends {@code MARK_READ} for these when the entry is shown
     * (§2.3, guide entries have no "read" of their own).
     */
    public Set<String> newOpeners(GuideEntry e) {
        Set<String> out = new LinkedHashSet<>();
        if (!isOpen(e)) {
            return out;
        }
        List<String> ids = new ArrayList<>(openers(e.unlock()));
        for (GuidePage p : pages(e)) {
            ids.addAll(openers(p.unlock()));
        }
        for (String id : ids) {
            if ((flags.get(id) & JournalData.NEW) != 0) {
                out.add(id);
            }
        }
        return out;
    }

    /** Open entries of a category, in display order. */
    public List<GuideEntry> openEntries(String categoryId) {
        return book.entriesOf(categoryId).stream().filter(this::isOpen).toList();
    }

    public boolean isOpen(GuideCategory c) {
        return !openEntries(c.id()).isEmpty();
    }

    public boolean isNew(GuideCategory c) {
        return openEntries(c.id()).stream().anyMatch(this::isNew);
    }

    /** Categories with at least one open entry, in display order. */
    public List<GuideCategory> openCategories() {
        return book.categories().stream().filter(this::isOpen).toList();
    }

    /** Open new entries, in display order. */
    public List<GuideEntry> newEntries() {
        return book.entries().stream().filter(this::isNew).toList();
    }
}
