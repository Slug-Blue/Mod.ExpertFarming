package io.github.slugblue.wildroots.plant;

import io.github.slugblue.wildroots.core.safety.NbtBudget;
import io.github.slugblue.wildroots.core.safety.NbtLimits;
import io.github.slugblue.wildroots.genetics.codec.GenomeRecord;
import io.github.slugblue.wildroots.genetics.pollination.Brush;
import io.github.slugblue.wildroots.item.LotFathersNbt;
import java.util.List;
import java.util.Optional;
import net.minecraft.nbt.ByteArrayTag;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.LongArrayTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;

/**
 * NBT of {@link PlantBlockEntity} (docs/05-architecture.md §5.2, §6.1;
 * ADR-0003; SEC-09). The block entity tag holds one root compound under
 * {@value #ROOT}:
 *
 * <pre>
 * wr: {
 *   v: 1,                       format version of this root
 *   sp: "wildroots:wild_carrot",
 *   g: {v, sp, lh, a},          genome (absent while a wild plant is untouched)
 *   e: [B; ...],                noise, one byte per quantitative trait (at most 48)
 *   pp: {o, at, c, r, s, lr, vp, hc},   passport (absent = player plant)
 *   um, ul: long,               planter UUID
 *   cv, ct, ci,                 cultivar status: id, tier, inherited
 *   ln, gen, fl,                line name, generation, flags of the ancestor lot
 *   pf,                         plant flags: mother-planted, mutant, rareClaimed
 *   pl: {f, po, br, bee}        pending lot (at most 4 fathers + 4 donors)
 * }
 * </pre>
 *
 * The decoder is type-safe and never throws: a damaged, oversized or future
 * root gives {@link Inert} and the caller keeps the raw tag (§5.3, §6.1).
 */
public final class PlantNbt {

    public static final String ROOT = "wr";
    public static final int CURRENT = 1;
    /** Longest line name in code points (SEC-07, {@code 09-security.md} §1.6.2). */
    public static final int MAX_LINE_NAME = 50;

    /** Decoding outcome. */
    public sealed interface Result permits Ok, Inert {
    }

    public record Ok(PlantState state) implements Result {
    }

    /** {@code future}: written by a newer version of the mod. */
    public record Inert(String reason, boolean future) implements Result {
    }

    private PlantNbt() {
    }

    /** Budget and fields of {@code tag}; never throws. */
    public static Result read(Tag tag) {
        if (!(tag instanceof CompoundTag root)) {
            return new Inert("no plant data", false);
        }
        NbtBudget.Result budget = NbtLimits.blockEntity(root);
        if (!budget.ok()) {
            return new Inert("over limit: " + budget.reason(), false);
        }
        if (!root.contains("v", Tag.TAG_INT) || root.getInt("v") < 1) {
            return new Inert("damaged version", false);
        }
        if (root.getInt("v") > CURRENT) {
            return new Inert("version " + root.getInt("v") + " from the future", true);
        }
        try {
            return decode(root);
        } catch (RuntimeException e) {
            return new Inert("damaged: " + e.getClass().getSimpleName(), false);
        }
    }

    private static Result decode(CompoundTag root) {
        PlantState s = new PlantState();
        if (!validId(root, "sp")) {
            return new Inert("bad species id", false);
        }
        s.species = root.getString("sp");
        if (root.contains("g")) {
            if (!(root.get("g") instanceof CompoundTag g) || !g.contains("v", Tag.TAG_INT) || !validId(g, "sp")
                    || !g.contains("lh", Tag.TAG_LONG) || !g.contains("a", Tag.TAG_LONG_ARRAY)) {
                return new Inert("bad genome", false);
            }
            long[] a = g.getLongArray("a");
            if (a.length == 0 || a.length > NbtLimits.GENOME_MAX_LONGS || !g.getString("sp").equals(s.species)) {
                return new Inert("bad genome", false);
            }
            if (!root.contains("e", Tag.TAG_BYTE_ARRAY)
                    || root.getByteArray("e").length > NbtLimits.BLOCK_ENTITY_MAX_NOISE) {
                return new Inert("bad noise", false);
            }
            s.genome = new GenomeRecord(g.getInt("v"), g.getString("sp"), g.getLong("lh"), a);
            s.noise = root.getByteArray("e");
        } else if (root.contains("e")) {
            return new Inert("noise without genome", false);
        }
        if (root.contains("pp")) {
            Passport p = passport(root.get("pp"));
            if (p == null) {
                return new Inert("bad passport", false);
            }
            s.passport = p;
        }
        if (s.genome == null && s.passport.origin() == Passport.Origin.PLAYER) {
            return new Inert("player plant without genome", false);
        }
        if (root.contains("um") || root.contains("ul")) {
            if (!root.contains("um", Tag.TAG_LONG) || !root.contains("ul", Tag.TAG_LONG)) {
                return new Inert("bad planter", false);
            }
            s.planterMost = root.getLong("um");
            s.planterLeast = root.getLong("ul");
        }
        if (root.contains("cv")) {
            if (!validId(root, "cv")) {
                return new Inert("bad cultivar", false);
            }
            s.cultivar = root.getString("cv");
        }
        s.tier = root.getByte("ct");
        if (s.tier < 0 || s.tier > 2) {
            return new Inert("bad tier", false);
        }
        s.inherited = root.getByte("ci") == 1;
        if (root.contains("ln")) {
            if (!root.contains("ln", Tag.TAG_STRING)) {
                return new Inert("bad line name", false);
            }
            String ln = root.getString("ln");
            if (ln.codePointCount(0, ln.length()) > MAX_LINE_NAME) {
                return new Inert("line name too long", false);
            }
            // T2: a name that is not clean player text is cosmetic damage only; it
            // is read as absent (and so not written back), the plant stays live
            if (io.github.slugblue.wildroots.core.safety.PlayerText.isClean(ln, MAX_LINE_NAME)) {
                s.lineName = ln;
            }
        }
        s.generation = root.getInt("gen");
        if (s.generation < 0 || s.generation > NbtLimits.LOT_MAX_GENERATION) {
            return new Inert("bad generation", false);
        }
        s.lotFlags = root.getByte("fl");
        s.plantFlags = root.getByte("pf");
        if (root.contains("pl")) {
            if (s.genome == null || !(root.get("pl") instanceof CompoundTag pl)) {
                return new Inert("bad pending lot", false);
            }
            Optional<List<LotFathersNbt.Father>> f = LotFathersNbt.decode(pl.get("f"), s.genome.lh());
            float po = pl.getFloat("po");
            int br = pl.getByte("br");
            if (f.isEmpty() || !Float.isFinite(po) || po < 0 || po > 1 || br < 0 || br >= Brush.values().length) {
                return new Inert("bad pending lot", false);
            }
            s.pending = new PendingLot(f.get(), po, Brush.values()[br], pl.getByte("bee") == 1);
        }
        return new Ok(s);
    }

    private static boolean validId(CompoundTag c, String key) {
        if (!c.contains(key, Tag.TAG_STRING)) {
            return false;
        }
        String id = c.getString(key);
        return id.length() <= NbtLimits.GENOME_MAX_SPECIES_LENGTH && ResourceLocation.tryParse(id) != null;
    }

    private static Passport passport(Tag tag) {
        if (!(tag instanceof CompoundTag c)) {
            return null;
        }
        Passport.Origin o = Passport.Origin.of(c.getByte("o"));
        if (o == null || (c.contains("c") && !c.contains("c", Tag.TAG_STRING))
                || (c.contains("lr") && !c.contains("lr", Tag.TAG_STRING))
                || c.getString("c").length() > NbtLimits.GENOME_MAX_SPECIES_LENGTH
                || c.getString("lr").length() > NbtLimits.GENOME_MAX_SPECIES_LENGTH || c.getInt("hc") < 0) {
            return null;
        }
        return new Passport(o, c.getLong("at"), c.getString("c"), c.getByte("r") == 1, c.getByte("s") == 1,
                c.getString("lr"), c.getLong("vp"), c.getInt("hc"));
    }

    /** The root of {@code s} in the current format. */
    public static CompoundTag write(PlantState s) {
        CompoundTag root = new CompoundTag();
        root.putInt("v", CURRENT);
        root.putString("sp", s.species);
        if (s.genome != null) {
            CompoundTag g = new CompoundTag();
            g.putInt("v", s.genome.v());
            g.putString("sp", s.genome.species());
            g.putLong("lh", s.genome.lh());
            g.put("a", new LongArrayTag(s.genome.a()));
            root.put("g", g);
            root.put("e", new ByteArrayTag(s.noise == null ? new byte[0] : s.noise));
        }
        Passport p = s.passport;
        if (p.origin() != Passport.Origin.PLAYER) {
            CompoundTag c = new CompoundTag();
            c.putByte("o", (byte) p.origin().ordinal());
            if (p.patchOrigin() != 0) {
                c.putLong("at", p.patchOrigin());
            }
            if (!p.category().isEmpty()) {
                c.putString("c", p.category());
            }
            if (p.rich()) {
                c.putByte("r", (byte) 1);
            }
            if (p.starter()) {
                c.putByte("s", (byte) 1);
            }
            if (!p.landrace().isEmpty()) {
                c.putString("lr", p.landrace());
            }
            if (p.villagePos() != 0) {
                c.putLong("vp", p.villagePos());
            }
            if (p.harvestCount() != 0) {
                c.putInt("hc", p.harvestCount());
            }
            root.put("pp", c);
        }
        if (s.hasPlanter()) {
            root.putLong("um", s.planterMost);
            root.putLong("ul", s.planterLeast);
        }
        if (s.cultivar != null) {
            root.putString("cv", s.cultivar);
            root.putByte("ct", s.tier);
            if (s.inherited) {
                root.putByte("ci", (byte) 1);
            }
        }
        if (s.lineName != null) {
            root.putString("ln", s.lineName);
        }
        if (s.generation != 0) {
            root.putInt("gen", s.generation);
        }
        if (s.lotFlags != 0) {
            root.putByte("fl", s.lotFlags);
        }
        if (s.plantFlags != 0) {
            root.putByte("pf", s.plantFlags);
        }
        if (s.pending != null && s.genome != null) {
            CompoundTag pl = new CompoundTag();
            pl.put("f", LotFathersNbt.encode(s.pending.fathers(), s.genome.lh()));
            pl.putFloat("po", s.pending.pOut());
            if (s.pending.brush() != Brush.NONE) {
                pl.putByte("br", (byte) s.pending.brush().ordinal());
            }
            if (s.pending.bees()) {
                pl.putByte("bee", (byte) 1);
            }
            root.put("pl", pl);
        }
        return root;
    }

    /**
     * {@code getUpdateTag} content (§6.2): empty for a built-in species, whose
     * look comes from the {@code BlockState}; the species id for a data-pack
     * species on an archetype block (tint colour, §7.1).
     */
    public static CompoundTag updateTag(boolean builtIn, String species) {
        CompoundTag t = new CompoundTag();
        if (!builtIn && species != null) {
            t.putString("sp", species);
        }
        return t;
    }
}
