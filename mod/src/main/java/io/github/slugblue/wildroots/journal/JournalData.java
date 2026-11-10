package io.github.slugblue.wildroots.journal;

import io.github.slugblue.wildroots.core.safety.DispCodec;
import io.github.slugblue.wildroots.core.safety.NbtBudget;
import io.github.slugblue.wildroots.core.safety.NbtLimits;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;

/**
 * The field journal of one player (narrative §4.1; docs/05-architecture.md
 * §6.1): the entries the server unlocked, their read and pinned marks, the
 * "journal given" flag, the toast throttle and small counters of the
 * triggers. Lives in a player capability ({@link Journals}), never in the
 * book item: a lost or new journal opens the same entries.
 *
 * <p>Pure model (JUnit): no player, no level. Limits: ≤ {@value #MAX_ENTRIES}
 * entries, ≤ {@value #MAX_ROWS} rows per entry, ≤ {@value #MAX_COUNTERS}
 * counters, the whole NBT ≤ 64 KiB and depth ≤ 4 ({@link NbtLimits#journal}).
 * A change that would break a limit is refused. A stored journal over the
 * limit or damaged is <em>inert</em>: it keeps its raw tag, writes it back
 * unchanged and takes no changes (SEC-09, as the other roots).
 */
public final class JournalData {

    public static final int CURRENT = 1;
    public static final int MAX_ENTRIES = 256;
    public static final int MAX_ROWS = 16;
    public static final int MAX_COUNTERS = 128;
    /** At most one "new entry" toast in this many ticks (30 s; narrative §4.1). */
    public static final long TOAST_INTERVAL_TICKS = 600;

    /** Entry id: kind, a dot, then the rest ({@code hint.welcome}, {@code species.wild_carrot}). */
    public static final Pattern ID = Pattern.compile("^(hint|species|cultivar|tool|note)\\.[a-z0-9_.]{1,64}$");
    /** Counter key ({@code select_quarter.wild_carrot}, {@code line.<hash>}). */
    public static final Pattern COUNTER = Pattern.compile("^[a-z0-9_.]{1,80}$");

    /** Entry flags. */
    public static final int READ = 1;
    public static final int PINNED = 2;
    /** Shown as a silhouette (a cultivar not yet bred): not unlocked, refuses actions. */
    public static final int LOCKED = 4;
    /** Unlocked since the player last opened the journal ("New" mark of narrative §9.7). */
    public static final int NEW = 8;
    static final int ALL_FLAGS = READ | PINNED | LOCKED | NEW;

    /** One journal entry; {@code args} may hold key arguments ({@link DispCodec#keyArg}). */
    public record Entry(String id, List<String> args, List<DispCodec.Line> rows, int order, int flags) {

        public Entry {
            args = List.copyOf(args);
            rows = List.copyOf(rows);
        }

        public boolean is(int flag) {
            return (flags & flag) != 0;
        }

        Entry withFlags(int f) {
            return new Entry(id, args, rows, order, f & ALL_FLAGS);
        }
    }

    /** Outcome of {@link #unlock}. */
    public enum Unlock {
        /** First time: a new entry (toast, "New" mark). */
        NEW,
        /** Already there; its arguments or rows changed (a new patch row). */
        UPDATED,
        /** Nothing changed (already there, refused, or the journal is inert). */
        NONE
    }

    private final LinkedHashMap<String, Entry> entries = new LinkedHashMap<>();
    private final LinkedHashMap<String, Long> counters = new LinkedHashMap<>();
    private boolean given;
    private long lastToast = Long.MIN_VALUE;
    private int nextOrder;
    /** Transient: number of applied changes since creation (sync and tests). */
    private int revision;
    /** Transient: the revision the client last received (full sync or delta); -1 = never. */
    private int syncedRevision = -1;
    /**
     * Transient (T1): an upper bound of the live NBT size in bytes, exact
     * right after a full measure, -1 when unknown. {@link #setCounter} keeps
     * it up to date from the exact size of a counter and measures the whole
     * journal only when the bound passes 64 KiB.
     */
    private long sizeBound = -1;
    /** Transient: number of full measures (tests). */
    private int fullMeasures;
    private CompoundTag inertRaw;
    private String inertReason;

    public JournalData() {
    }

    // ------------------------------------------------------------------ queries

    public boolean inert() {
        return inertRaw != null;
    }

    public String inertReason() {
        return inertReason;
    }

    /** Unlocked (not a locked silhouette). */
    public boolean has(String id) {
        Entry e = entries.get(id);
        return e != null && !e.is(LOCKED);
    }

    public Optional<Entry> entry(String id) {
        return Optional.ofNullable(entries.get(id));
    }

    public Collection<Entry> entries() {
        return Collections.unmodifiableCollection(entries.values());
    }

    public boolean given() {
        return given;
    }

    public int revision() {
        return revision;
    }

    /** Whether the client holds this revision (nothing changed since the last full sync or delta). */
    public boolean synced() {
        return syncedRevision == revision;
    }

    public void markSynced() {
        syncedRevision = revision;
    }

    public long counter(String key) {
        return counters.getOrDefault(key, 0L);
    }

    public static boolean validId(String id) {
        return id != null && ID.matcher(id).matches();
    }

    // ------------------------------------------------------------------ changes

    /** Marks the journal item as handed out; {@code false} if it was already (or inert). */
    public boolean markGiven() {
        if (inert() || given) {
            return false;
        }
        given = true;
        grow(SMALL_FIELD_BOUND);
        revision++;
        return true;
    }

    /**
     * Unlocks {@code id} with {@code args} and {@code rows}; an unlocked
     * entry keeps its order and marks and takes the new arguments and rows.
     * Invalid ids, args or rows, more than {@value #MAX_ENTRIES} entries or
     * a journal over 64 KiB are refused ({@link Unlock#NONE}).
     */
    public Unlock unlock(String id, List<String> args, List<DispCodec.Line> rows) {
        return put(id, args, rows, false);
    }

    /** Adds a locked silhouette ({@code cultivar.<id>}); never replaces an unlocked entry. */
    public boolean lock(String id, List<String> args) {
        return put(id, args, List.of(), true) == Unlock.NEW;
    }

    private Unlock put(String id, List<String> args, List<DispCodec.Line> rows, boolean locked) {
        if (inert() || !validId(id) || !validArgs(args) || rows.size() > MAX_ROWS
                || !rows.stream().allMatch(DispCodec::valid)) {
            return Unlock.NONE;
        }
        Entry old = entries.get(id);
        Entry next;
        Unlock result;
        if (old == null) {
            if (entries.size() >= MAX_ENTRIES) {
                return Unlock.NONE;
            }
            next = new Entry(id, args, rows, nextOrder, locked ? LOCKED : NEW);
            result = Unlock.NEW;
        } else if (locked) {
            return Unlock.NONE;
        } else if (old.is(LOCKED)) {
            next = new Entry(id, args, rows, nextOrder, NEW);
            result = Unlock.NEW;
        } else {
            if (old.args().equals(args) && old.rows().equals(rows)) {
                return Unlock.NONE;
            }
            next = new Entry(id, args, rows, old.order(), old.flags());
            result = Unlock.UPDATED;
        }
        entries.put(id, next);
        if (!fits()) {
            if (old == null) {
                entries.remove(id);
            } else {
                entries.put(id, old);
            }
            return Unlock.NONE;
        }
        if (result == Unlock.NEW) {
            nextOrder++;
        }
        revision++;
        return result;
    }

    /**
     * Shows an unlocked entry again as new (a per-line hint that fires for
     * the line once more): it moves to the top of the notes with new
     * arguments and the "New" mark. {@code false} if the entry is missing.
     */
    public boolean refire(String id, List<String> args) {
        Entry old = entries.get(id);
        if (inert() || old == null || old.is(LOCKED) || !validArgs(args)) {
            return false;
        }
        entries.put(id, new Entry(id, args, old.rows(), nextOrder, (old.flags() | NEW) & ~READ));
        if (!fits()) {
            entries.put(id, old);
            return false;
        }
        nextOrder++;
        revision++;
        return true;
    }

    static boolean validArgs(List<String> args) {
        return args.size() <= DispCodec.MAX_ARGS
                && DispCodec.valid(new DispCodec.Line(DispCodec.KEY_PREFIX + "journal", args));
    }

    /**
     * {@code JournalAction} (C→S, SEC-04/SEC-05): applies {@code action} to
     * an entry this player has unlocked. A foreign namespace, an unknown or
     * locked id, another player's entry (not in this journal) or an unknown
     * action are refused with {@code false} and change nothing.
     */
    public boolean apply(ResourceLocation entry, int action) {
        if (inert() || entry == null || !"wildroots".equals(entry.getNamespace())) {
            return false;
        }
        String id = entry.getPath();
        Entry e = entries.get(id);
        if (e == null || e.is(LOCKED)) {
            return false;
        }
        int f = switch (action) {
            case io.github.slugblue.wildroots.network.c2s.JournalActionPacket.MARK_READ -> (e.flags() | READ) & ~NEW;
            case io.github.slugblue.wildroots.network.c2s.JournalActionPacket.PIN -> e.flags() | PINNED;
            case io.github.slugblue.wildroots.network.c2s.JournalActionPacket.UNPIN -> e.flags() & ~PINNED;
            default -> -1;
        };
        if (f < 0) {
            return false;
        }
        if (f != e.flags()) {
            entries.put(id, e.withFlags(f));
            grow(SMALL_FIELD_BOUND);
            revision++;
        }
        return true;
    }

    /**
     * The "new entry" toast throttle: {@code true} (and the clock restarts)
     * if no toast was shown in the last {@value #TOAST_INTERVAL_TICKS} ticks;
     * otherwise the entry accumulates silently (narrative §4.1).
     */
    public boolean takeToast(long gameTime) {
        if (inert()) {
            return false;
        }
        if (lastToast != Long.MIN_VALUE && gameTime - lastToast < TOAST_INTERVAL_TICKS && gameTime >= lastToast) {
            return false;
        }
        lastToast = gameTime;
        grow(SMALL_FIELD_BOUND);
        return true;
    }

    /**
     * Sets a trigger counter (review 1, m3). Counters are kept in the order
     * they were last set; a new key past {@value #MAX_COUNTERS} evicts the
     * least recently set one, and so does a change that would take the
     * journal over 64 KiB, so line hints keep working however many lines a
     * player names. Refused only for a bad key, an inert journal, or a
     * counter that does not fit even alone.
     */
    public boolean setCounter(String key, long value) {
        if (inert() || key == null || !COUNTER.matcher(key).matches()) {
            return false;
        }
        boolean hadCounters = !counters.isEmpty();
        Long old = counters.remove(key);
        if (old != null) {
            shrink(counterBytes(key));
        }
        if (value == 0) {
            return true;
        }
        while (counters.size() >= MAX_COUNTERS) {
            evictOldestCounter();
        }
        if (!hadCounters) {
            grow(COUNTERS_TAG_BYTES); // the bound never takes these bytes back, so it stays an upper bound
        }
        counters.put(key, value);
        grow(counterBytes(key));
        if (sizeBound >= 0 && sizeBound <= NbtLimits.JOURNAL_MAX_BYTES) {
            return true; // T1: the bound fits, no need to serialise the journal
        }
        while (!fits()) {
            if (counters.size() <= 1) {
                counters.remove(key);
                if (old != null) {
                    counters.put(key, old);
                    if (!fits()) {
                        counters.remove(key);
                    }
                }
                sizeBound = -1;
                return false;
            }
            evictOldestCounter();
        }
        return true;
    }

    /** Exact NBT bytes of one counter: type byte, u16 key length, the ASCII key ({@link #COUNTER}), a long. */
    static long counterBytes(String key) {
        return 1 + 2 + key.length() + 8;
    }

    /** NBT bytes of the {@code c} compound itself: type byte, u16 name length, "c", TAG_End. */
    static final long COUNTERS_TAG_BYTES = 1 + 2 + 1 + 1;
    /** Upper bound of what a flag, the "given" byte or the toast time adds to the NBT. */
    static final long SMALL_FIELD_BOUND = 16;

    private void grow(long bytes) {
        if (sizeBound >= 0) {
            sizeBound += bytes;
        }
    }

    private void shrink(long bytes) {
        if (sizeBound >= 0) {
            sizeBound = Math.max(0, sizeBound - bytes);
        }
    }

    private void evictOldestCounter() {
        java.util.Iterator<String> it = counters.keySet().iterator();
        shrink(counterBytes(it.next()));
        it.remove();
    }

    /** Removes counters whose key starts with {@code prefix} (a finished day window). */
    public void clearCounters(String prefix) {
        if (!inert()) {
            counters.keySet().removeIf(k -> {
                if (k.startsWith(prefix)) {
                    shrink(counterBytes(k));
                    return true;
                }
                return false;
            });
        }
    }

    /** Copies everything from {@code other} (death: the clone keeps the journal). */
    public void copyFrom(JournalData other) {
        CompoundTag t = other.save();
        JournalData c = load(t);
        entries.clear();
        entries.putAll(c.entries);
        counters.clear();
        counters.putAll(c.counters);
        given = c.given;
        lastToast = c.lastToast;
        nextOrder = c.nextOrder;
        inertRaw = c.inertRaw;
        inertReason = c.inertReason;
        sizeBound = -1;
        revision++;
    }

    private boolean fits() {
        fullMeasures++;
        NbtBudget.Result r = NbtLimits.journal(writeLive());
        sizeBound = r.ok() ? r.bytes() : -1;
        return r.ok();
    }

    /** The running size bound, -1 = unknown (tests, T1). */
    long sizeBound() {
        return sizeBound;
    }

    /** Number of full NBT measures so far (tests, T1). */
    int fullMeasures() {
        return fullMeasures;
    }

    // ------------------------------------------------------------------ NBT

    public CompoundTag save() {
        return inert() ? inertRaw.copy() : writeLive();
    }

    private CompoundTag writeLive() {
        CompoundTag root = new CompoundTag();
        root.putInt("v", CURRENT);
        if (given) {
            root.putBoolean("given", true);
        }
        if (lastToast != Long.MIN_VALUE) {
            root.putLong("toast", lastToast);
        }
        root.putInt("next", nextOrder);
        ListTag list = new ListTag();
        for (Entry e : entries.values()) {
            CompoundTag t = new CompoundTag();
            t.putString("id", e.id());
            if (!e.args().isEmpty()) {
                ListTag a = new ListTag();
                e.args().forEach(s -> a.add(StringTag.valueOf(s)));
                t.put("a", a);
            }
            if (!e.rows().isEmpty()) {
                t.put("r", DispCodec.encode(e.rows()));
            }
            t.putInt("o", e.order());
            if (e.flags() != 0) {
                t.putByte("f", (byte) e.flags());
            }
            list.add(t);
        }
        root.put("e", list);
        if (!counters.isEmpty()) {
            CompoundTag c = new CompoundTag();
            counters.forEach(c::putLong);
            root.put("c", c);
        }
        return root;
    }

    /** Reads a stored journal; a damaged or oversized one becomes inert and keeps its raw tag. */
    public static JournalData load(CompoundTag root) {
        JournalData d = new JournalData();
        if (root == null || root.isEmpty()) {
            return d;
        }
        NbtBudget.Result b = NbtLimits.journal(root);
        if (!b.ok()) {
            return inert(root, "over limit: " + b.reason());
        }
        if (!root.contains("v", Tag.TAG_INT) || root.getInt("v") < 1) {
            return inert(root, "damaged version");
        }
        if (root.getInt("v") > CURRENT) {
            return inert(root, "version " + root.getInt("v") + " from the future");
        }
        try {
            // a key present with the wrong type is damage, not "empty" (review 1, m4)
            if (wrongType(root, "given", Tag.TAG_BYTE) || wrongType(root, "toast", Tag.TAG_LONG)
                    || wrongType(root, "next", Tag.TAG_INT) || wrongType(root, "e", Tag.TAG_LIST)
                    || wrongType(root, "c", Tag.TAG_COMPOUND) || !listOf(root, "e", Tag.TAG_COMPOUND)) {
                return inert(root, "wrong tag type");
            }
            d.given = root.getBoolean("given");
            d.lastToast = root.contains("toast", Tag.TAG_LONG) ? root.getLong("toast") : Long.MIN_VALUE;
            d.nextOrder = Math.max(0, root.getInt("next"));
            ListTag list = root.getList("e", Tag.TAG_COMPOUND);
            if (list.size() > MAX_ENTRIES) {
                return inert(root, "too many entries");
            }
            for (int i = 0; i < list.size(); i++) {
                CompoundTag t = list.getCompound(i);
                if (!t.contains("id", Tag.TAG_STRING) || wrongType(t, "a", Tag.TAG_LIST) || !listOf(t, "a", Tag.TAG_STRING)
                        || wrongType(t, "r", Tag.TAG_LIST) || !listOf(t, "r", Tag.TAG_STRING)
                        || wrongType(t, "o", Tag.TAG_INT) || wrongType(t, "f", Tag.TAG_BYTE)) {
                    return inert(root, "wrong tag type in entry " + i);
                }
                String id = t.getString("id");
                ListTag a = t.getList("a", Tag.TAG_STRING);
                List<String> args = new ArrayList<>(a.size());
                for (int k = 0; k < a.size(); k++) {
                    args.add(a.getString(k));
                }
                List<DispCodec.Line> rows = DispCodec.decode(t.get("r"));
                if (rows.size() != t.getList("r", Tag.TAG_STRING).size()) {
                    return inert(root, "bad row in entry " + i);
                }
                if (!validId(id) || !validArgs(args) || rows.size() > MAX_ROWS || d.entries.containsKey(id)) {
                    return inert(root, "bad entry " + i);
                }
                int order = t.getInt("o");
                d.entries.put(id, new Entry(id, args, rows, order, t.getByte("f") & ALL_FLAGS));
                d.nextOrder = Math.max(d.nextOrder, order + 1);
            }
            CompoundTag c = root.getCompound("c");
            if (c.size() > MAX_COUNTERS) {
                return inert(root, "too many counters");
            }
            for (String k : c.getAllKeys()) {
                if (!COUNTER.matcher(k).matches() || !c.contains(k, Tag.TAG_LONG)) {
                    return inert(root, "bad counter");
                }
                d.counters.put(k, c.getLong(k));
            }
        } catch (RuntimeException e) {
            return inert(root, "damaged: " + e.getClass().getSimpleName());
        }
        return d;
    }

    static boolean wrongType(CompoundTag t, String key, int type) {
        return t.contains(key) && !t.contains(key, type);
    }

    /** {@code key} is absent, an empty list, or a list of {@code type}. */
    static boolean listOf(CompoundTag t, String key, int type) {
        if (!(t.get(key) instanceof ListTag l)) {
            return true;
        }
        return l.isEmpty() || l.getElementType() == type;
    }

    private static JournalData inert(CompoundTag raw, String reason) {
        JournalData d = new JournalData();
        d.inertRaw = raw.copy();
        d.inertReason = reason;
        return d;
    }

    /** Entries in the order they were unlocked (silhouettes included). */
    public List<Entry> ordered() {
        List<Entry> l = new ArrayList<>(entries.values());
        l.sort(java.util.Comparator.comparingInt(Entry::order));
        return l;
    }

    /** Snapshot of the counters (tests). */
    public Map<String, Long> counters() {
        return Collections.unmodifiableMap(counters);
    }
}
