package io.github.slugblue.wildroots.item;

import io.github.slugblue.wildroots.core.safety.NbtBudget;
import io.github.slugblue.wildroots.core.safety.NbtLimits;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

/**
 * Model of the seed pouch (GDD §5.3; docs/05-architecture.md §6.3;
 * ADR-0007 rev. 3; SEC-17): at most {@value NbtLimits#POUCH_MAX_ENTRIES}
 * entries of one seed each, LIFO, never merged. Immutable: every operation
 * returns a new state or nothing, so it either happens completely or not at
 * all. The NBT form is {@code {v: 1, e: [{l: <lot root>, c: 1b}, ...]}};
 * other keys of the stored compound are kept as they are.
 *
 * <p>The protective 17 KiB / depth 7 limit (SEC-09) is checked on the
 * result of an insertion: with valid lots it never trips, otherwise the
 * insertion is refused and the pouch stays usable.
 */
public final class PouchContents {

    public static final String KEY = "wildroots:pouch";
    public static final int CAPACITY = NbtLimits.POUCH_MAX_ENTRIES;

    private final CompoundTag base;
    private final List<CompoundTag> entries;

    private PouchContents(CompoundTag base, List<CompoundTag> entries) {
        this.base = base;
        this.entries = Collections.unmodifiableList(entries);
    }

    public static final PouchContents EMPTY = new PouchContents(new CompoundTag(), List.of());

    /** Reads a stored pouch; empty if it breaks the limits (an inert pouch accepts nothing). */
    public static Optional<PouchContents> read(Tag tag) {
        if (tag == null) {
            return Optional.of(EMPTY);
        }
        if (!(tag instanceof CompoundTag c) || !NbtLimits.pouch(c).ok()) {
            return Optional.empty();
        }
        List<CompoundTag> out = new ArrayList<>();
        ListTag e = c.getList("e", Tag.TAG_COMPOUND);
        for (int i = 0; i < e.size(); i++) {
            CompoundTag entry = e.getCompound(i);
            if (!(entry.get("l") instanceof CompoundTag lot) || entry.getByte("c") != 1) {
                return Optional.empty();
            }
            out.add(lot.copy());
        }
        CompoundTag base = c.copy();
        base.remove("e");
        return Optional.of(new PouchContents(base, out));
    }

    /** Seeds (= entries) inside. */
    public int used() {
        return entries.size();
    }

    public int free() {
        return Math.max(0, CAPACITY - entries.size());
    }

    /** Lot roots, first put in first. */
    public List<CompoundTag> entries() {
        return entries;
    }

    /** Result of an insertion: the new pouch and how many seeds moved. */
    public record Inserted(PouchContents pouch, int moved) {
    }

    /**
     * Puts {@code min(available, free)} seeds of {@code lotRoot} in, one entry
     * each; empty if nothing fits or the result breaks the NBT limit.
     */
    public Optional<Inserted> insert(CompoundTag lotRoot, int available) {
        int n = Math.min(Math.max(0, available), free());
        if (n <= 0 || lotRoot == null) {
            return Optional.empty();
        }
        List<CompoundTag> next = new ArrayList<>(entries);
        for (int i = 0; i < n; i++) {
            next.add(lotRoot.copy());
        }
        PouchContents p = new PouchContents(base, next);
        NbtBudget.Result r = NbtLimits.pouch(p.write());
        return r.ok() ? Optional.of(new Inserted(p, n)) : Optional.empty();
    }

    /** Result of an extraction: the new pouch and the lot root of the seed taken out. */
    public record Extracted(PouchContents pouch, CompoundTag lot) {
    }

    /** Takes the last seed put in (LIFO). */
    public Optional<Extracted> removeLast() {
        if (entries.isEmpty()) {
            return Optional.empty();
        }
        List<CompoundTag> next = new ArrayList<>(entries);
        CompoundTag last = next.remove(next.size() - 1);
        return Optional.of(new Extracted(new PouchContents(base, next), last.copy()));
    }

    /** NBT form; unknown keys of the stored compound are kept. */
    public CompoundTag write() {
        CompoundTag c = base.copy();
        c.putInt("v", 1);
        ListTag e = new ListTag();
        for (CompoundTag lot : entries) {
            CompoundTag entry = new CompoundTag();
            entry.put("l", lot.copy());
            entry.putByte("c", (byte) 1);
            e.add(entry);
        }
        c.put("e", e);
        return c;
    }
}
