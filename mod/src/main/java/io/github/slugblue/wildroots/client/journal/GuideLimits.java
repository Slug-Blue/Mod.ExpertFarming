package io.github.slugblue.wildroots.client.journal;

import java.util.regex.Pattern;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * Limits of the guide files (journal v2 §5.2). A server resource pack may
 * replace them, so every file is read under these bounds and anything over
 * them is skipped with one log line, never a crash.
 */
@OnlyIn(Dist.CLIENT)
public final class GuideLimits {

    /** Largest single guide file (categories, entries, {@code hint_links.json}). */
    public static final int MAX_FILE_BYTES = 32 * 1024;
    /** All guide files together (scenes are counted on their own, §5.8). */
    public static final int MAX_TOTAL_BYTES = 256 * 1024;
    public static final int MAX_CATEGORIES = 16;
    public static final int MAX_ENTRIES = 256;
    public static final int MAX_PAGES = 24;
    /** {@code unlock}, {@code items}, {@code see_also} and other id lists. */
    public static final int MAX_LIST = 8;
    /** Any string value: ids, lang keys, item ids. */
    public static final int MAX_STRING = 128;
    /** Deepest JSON nesting of a guide file (checked by a strict reader before the tree is built). */
    public static final int MAX_JSON_DEPTH = 16;
    /** Pairs in {@code hint_links.json}: one per hint at most. */
    public static final int MAX_HINT_LINKS = 256;

    /** Guide, category, scene ids: {@code guide.cycle}, {@code plant}. */
    public static final Pattern ID = Pattern.compile("[a-z0-9_.]{1,64}");
    /**
     * An id of {@code unlock}: a journal entry id the server may send, or a
     * kind with a wildcard ({@code species.*}, {@code cultivar.*}).
     */
    public static final Pattern UNLOCK = Pattern.compile("[a-z0-9_.]{1,64}|[a-z0-9_]{1,32}\\.\\*");
    /** A lang key named by a page. */
    public static final Pattern LANG_KEY = Pattern.compile("[a-z0-9_.]{1,128}");

    private GuideLimits() {
    }
}
