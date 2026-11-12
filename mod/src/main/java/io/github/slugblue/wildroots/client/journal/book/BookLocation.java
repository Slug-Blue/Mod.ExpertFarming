package io.github.slugblue.wildroots.client.journal.book;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * A place in the book (journal v2 §3.11): what is open and which spread of
 * it. {@code id} is the category id ({@link Kind#CATEGORY}), the entry id
 * ({@link Kind#ENTRY}), the records category name ({@link Kind#RECORDS},
 * {@link RecordsCategory}) or empty ({@link Kind#CONTENTS}).
 */
@OnlyIn(Dist.CLIENT)
public record BookLocation(Kind kind, String id, int spread) {

    public enum Kind {
        /** The contents spread: guide categories, records categories, new, pinned (§3.8). */
        CONTENTS,
        /** A guide category: its list of entries. */
        CATEGORY,
        /** A guide entry. */
        ENTRY,
        /** A records category (species, cultivars, lines, notes, breeder's notes). */
        RECORDS,
        /** One species or cultivar record; {@code id} is its journal entry id. */
        RECORD,
        /** Search results; {@code id} is the query (§3.9). */
        SEARCH
    }

    public static final BookLocation CONTENTS = new BookLocation(Kind.CONTENTS, "", 0);

    public BookLocation {
        spread = Math.max(0, spread);
    }

    public static BookLocation category(String id) {
        return new BookLocation(Kind.CATEGORY, id, 0);
    }

    public static BookLocation entry(String id) {
        return new BookLocation(Kind.ENTRY, id, 0);
    }

    public static BookLocation records(RecordsCategory c) {
        return new BookLocation(Kind.RECORDS, c.name(), 0);
    }

    public static BookLocation search(String query) {
        return new BookLocation(Kind.SEARCH, query, 0);
    }

    public static BookLocation record(String journalId) {
        return new BookLocation(Kind.RECORD, journalId, 0);
    }

    public BookLocation withSpread(int s) {
        return new BookLocation(kind, id, s);
    }

    /** Same thing open, maybe another spread. */
    public boolean sameTarget(BookLocation o) {
        return o != null && kind == o.kind && id.equals(o.id);
    }
}
