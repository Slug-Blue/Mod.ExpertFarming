package io.github.slugblue.wildroots.item;

import io.github.slugblue.wildroots.core.safety.DispCodec;
import io.github.slugblue.wildroots.core.safety.NbtBudget;
import io.github.slugblue.wildroots.core.safety.NbtLimits;
import io.github.slugblue.wildroots.genetics.codec.GenomeRecord;
import java.util.List;
import java.util.Optional;
import net.minecraft.nbt.ByteArrayTag;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.LongArrayTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;

/**
 * NBT of a seed lot (docs/05-architecture.md §6.1; ADR-0007 rev. 2; SEC-07,
 * SEC-09, SEC-13): the compound under {@value #KEY} of the item tag.
 *
 * <pre>
 * wildroots:lot: {
 *   v: 1, id: lotId (long), var: cultivar (optional),
 *   g: {v, sp, lh, a}            mother genome; its sp is the species of the lot
 *   f: [{a, w, lh?, d?}, ...]    fathers (LotFathersNbt)
 *   po: p_out, gen, ln, fl,      generation, line name, flags (LotFlags)
 *   rs: rare source (1 chest, 2 mutation, 3 trade; absent = none),
 *   fx: 1b, e: [B; ...]          realised seedling: plants exactly g with noise e (SEC-26)
 *   disp: [...]                  tooltip lines written by the server (DispCodec)
 * }
 * lazy lot: { v: 1, tpl: {sp, cat, salt, id}, disp }   lotId assigned at creation (SEC-13)
 * </pre>
 *
 * Writing goes only through {@link LotStacks}; reading never throws and
 * gives {@link Inert} for anything damaged, oversized or from the future.
 */
public final class LotNbt {

    public static final String KEY = "wildroots:lot";
    public static final int CURRENT = 1;
    public static final int MAX_LINE_NAME = 50;
    public static final int MAX_CATEGORY = 64;

    /** Where a rare lot came from (economy §6.1, SEC-21). */
    public enum RareSource {
        NONE, CHEST, MUTATION, TRADE
    }

    /** Decoding outcome. */
    public sealed interface Result permits Lot, Lazy, Inert {
    }

    /** A realised lot. {@code noise} is present only for a realised seedling ({@code fx}). */
    public record Lot(long lotId, String species, Optional<String> cultivar, GenomeRecord mother,
                      List<LotFathersNbt.Father> fathers, float pOut, int generation, Optional<String> lineName,
                      int flags, RareSource rare, Optional<byte[]> seedlingNoise) implements Result {

        public Lot {
            fathers = List.copyOf(fathers);
            rare = rare == null ? RareSource.NONE : rare;
        }

        public boolean seedling() {
            return seedlingNoise.isPresent();
        }
    }

    /** A lazy lot (grass, chest, trade): template with the lotId of its creation (SEC-13). */
    public record Lazy(String species, String category, long salt, long lotId, RareSource rare) implements Result {
    }

    public record Inert(String reason) implements Result {
    }

    private LotNbt() {
    }

    /** The lot root of an item tag, or {@code null}. */
    public static Tag root(CompoundTag itemTag) {
        return itemTag == null ? null : itemTag.get(KEY);
    }

    public static Result read(Tag tag) {
        if (!(tag instanceof CompoundTag root)) {
            return new Inert("no lot data");
        }
        NbtBudget.Result b = NbtLimits.lot(root);
        if (!b.ok()) {
            return new Inert("over limit: " + b.reason());
        }
        if (!root.contains("v", Tag.TAG_INT) || root.getInt("v") < 1) {
            return new Inert("damaged version");
        }
        if (root.getInt("v") > CURRENT) {
            return new Inert("version " + root.getInt("v") + " from the future");
        }
        try {
            return decode(root);
        } catch (RuntimeException e) {
            return new Inert("damaged: " + e.getClass().getSimpleName());
        }
    }

    private static Result decode(CompoundTag root) {
        RareSource rare = rare(root);
        if (rare == null) {
            return new Inert("bad rare source");
        }
        if (root.contains("tpl")) {
            if (!(root.get("tpl") instanceof CompoundTag t) || !validId(t, "sp") || !t.contains("id", Tag.TAG_LONG)
                    || !t.contains("salt", Tag.TAG_LONG) || !t.contains("cat", Tag.TAG_STRING)
                    || t.getString("cat").length() > MAX_CATEGORY || root.contains("g")) {
                return new Inert("bad template");
            }
            return new Lazy(t.getString("sp"), t.getString("cat"), t.getLong("salt"), t.getLong("id"), rare);
        }
        if (!root.contains("id", Tag.TAG_LONG)) {
            return new Inert("bad id");
        }
        if (!(root.get("g") instanceof CompoundTag g) || !g.contains("v", Tag.TAG_INT) || !validId(g, "sp")
                || !g.contains("lh", Tag.TAG_LONG)
                || !g.contains("a", Tag.TAG_LONG_ARRAY)) {
            return new Inert("bad mother genome");
        }
        String sp = g.getString("sp");
        long[] a = g.getLongArray("a");
        if (a.length == 0 || a.length > NbtLimits.GENOME_MAX_LONGS) {
            return new Inert("bad mother genome");
        }
        GenomeRecord mother = new GenomeRecord(g.getInt("v"), sp, g.getLong("lh"), a);
        Optional<List<LotFathersNbt.Father>> fathers = root.contains("f")
                ? LotFathersNbt.decode(root.get("f"), mother.lh()) : Optional.of(List.of());
        if (fathers.isEmpty()) {
            return new Inert("bad fathers");
        }
        float po = root.getFloat("po");
        if (!Float.isFinite(po) || po < 0 || po > 1) {
            return new Inert("bad p_out");
        }
        int gen = root.getInt("gen");
        if (gen < 0 || gen > NbtLimits.LOT_MAX_GENERATION) {
            return new Inert("bad generation");
        }
        Optional<String> ln = Optional.empty();
        if (root.contains("ln")) {
            String s = root.getString("ln");
            if (!root.contains("ln", Tag.TAG_STRING) || s.codePointCount(0, s.length()) > MAX_LINE_NAME) {
                return new Inert("bad line name");
            }
            ln = Optional.of(s);
        }
        Optional<String> cultivar = Optional.empty();
        if (root.contains("var")) {
            if (!validId(root, "var")) {
                return new Inert("bad cultivar");
            }
            cultivar = Optional.of(root.getString("var"));
        }
        Optional<byte[]> noise = Optional.empty();
        if (root.getByte("fx") == 1) {
            if (!root.contains("e", Tag.TAG_BYTE_ARRAY)
                    || root.getByteArray("e").length > NbtLimits.BLOCK_ENTITY_MAX_NOISE) {
                return new Inert("bad seedling noise");
            }
            noise = Optional.of(root.getByteArray("e"));
        }
        return new Lot(root.getLong("id"), sp, cultivar, mother, fathers.get(), po, gen, ln, root.getByte("fl"),
                rare, noise);
    }

    private static RareSource rare(CompoundTag root) {
        int r = root.getByte("rs");
        return r >= 0 && r < RareSource.values().length ? RareSource.values()[r] : null;
    }

    private static boolean validId(CompoundTag c, String key) {
        if (!c.contains(key, Tag.TAG_STRING)) {
            return false;
        }
        String id = c.getString(key);
        return id.length() <= NbtLimits.GENOME_MAX_SPECIES_LENGTH && ResourceLocation.tryParse(id) != null;
    }

    /** Root of a realised lot. Package-private: only {@link LotStacks} writes lots. */
    static CompoundTag write(Lot lot, List<DispCodec.Line> disp) {
        CompoundTag root = new CompoundTag();
        root.putInt("v", CURRENT);
        root.putLong("id", lot.lotId());
        lot.cultivar().ifPresent(c -> root.putString("var", c));
        CompoundTag g = new CompoundTag();
        g.putInt("v", lot.mother().v());
        g.putString("sp", lot.species());
        g.putLong("lh", lot.mother().lh());
        g.put("a", new LongArrayTag(lot.mother().a()));
        root.put("g", g);
        if (!lot.fathers().isEmpty()) {
            root.put("f", LotFathersNbt.encode(lot.fathers(), lot.mother().lh()));
        }
        if (lot.pOut() != 0) {
            root.putFloat("po", lot.pOut());
        }
        if (lot.generation() != 0) {
            root.putInt("gen", lot.generation());
        }
        lot.lineName().ifPresent(n -> root.putString("ln", n));
        if (lot.flags() != 0) {
            root.putByte("fl", (byte) lot.flags());
        }
        if (lot.rare() != RareSource.NONE) {
            root.putByte("rs", (byte) lot.rare().ordinal());
        }
        lot.seedlingNoise().ifPresent(e -> {
            root.putByte("fx", (byte) 1);
            root.put("e", new ByteArrayTag(e));
        });
        root.put("disp", DispCodec.encode(disp));
        return root;
    }

    /** Root of a lazy lot. Package-private: only {@link LotStacks} writes lots. */
    static CompoundTag write(Lazy lazy, List<DispCodec.Line> disp) {
        CompoundTag root = new CompoundTag();
        root.putInt("v", CURRENT);
        CompoundTag t = new CompoundTag();
        t.putString("sp", lazy.species());
        t.putString("cat", lazy.category());
        t.putLong("salt", lazy.salt());
        t.putLong("id", lazy.lotId());
        root.put("tpl", t);
        if (lazy.rare() != RareSource.NONE) {
            root.putByte("rs", (byte) lazy.rare().ordinal());
        }
        root.put("disp", DispCodec.encode(disp));
        return root;
    }

    /** The {@code disp} lines of a lot root (display only, never trusted for logic). */
    public static List<DispCodec.Line> disp(Tag root) {
        return root instanceof CompoundTag c ? DispCodec.decode(c.get("disp")) : List.of();
    }
}
