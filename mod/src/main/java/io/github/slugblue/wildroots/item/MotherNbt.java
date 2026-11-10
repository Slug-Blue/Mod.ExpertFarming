package io.github.slugblue.wildroots.item;

import io.github.slugblue.wildroots.core.safety.NbtBudget;
import io.github.slugblue.wildroots.core.safety.NbtLimits;
import io.github.slugblue.wildroots.genetics.codec.GenomeRecord;
import java.util.Optional;
import net.minecraft.nbt.ByteArrayTag;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.LongArrayTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;

/**
 * NBT of a mother root (GDD §5.2; docs/05-architecture.md §6.1; SEC-09):
 * the whole genome and noise of the harvested plant plus its status, line
 * data and the grade of its own phenotype (for eating), under
 * {@value #KEY}; at most 512 B, depth 4.
 *
 * <pre>
 * wildroots:mother: { v: 1, g: {v, sp, lh, a}, e: [B; ...], var?, ct, ci?, ln?, gen, fl, gr, bt? }
 * </pre>
 */
public final class MotherNbt {

    public static final String KEY = "wildroots:mother";
    public static final int CURRENT = 1;

    /** A decoded mother root. */
    public record Mother(GenomeRecord genome, byte[] noise, Optional<String> cultivar, int tier, boolean inherited,
                         Optional<String> lineName, int generation, int flags, int grade, boolean bitter) {

        public String species() {
            return genome.species();
        }
    }

    private MotherNbt() {
    }

    public static Optional<Mother> read(Tag tag) {
        if (!(tag instanceof CompoundTag root)) {
            return Optional.empty();
        }
        NbtBudget.Result b = NbtLimits.mother(root);
        if (!b.ok() || root.getInt("v") < 1 || root.getInt("v") > CURRENT) {
            return Optional.empty();
        }
        try {
            if (!(root.get("g") instanceof CompoundTag g) || !g.contains("v", Tag.TAG_INT) || !id(g, "sp")
                    || !g.contains("lh", Tag.TAG_LONG) || !g.contains("a", Tag.TAG_LONG_ARRAY)
                    || !root.contains("e", Tag.TAG_BYTE_ARRAY)) {
                return Optional.empty();
            }
            long[] a = g.getLongArray("a");
            byte[] e = root.getByteArray("e");
            int gr = root.getByte("gr");
            int tier = root.getByte("ct");
            int gen = root.getInt("gen");
            if (a.length == 0 || a.length > NbtLimits.GENOME_MAX_LONGS || e.length > NbtLimits.BLOCK_ENTITY_MAX_NOISE
                    || gr < 1 || gr > 4 || tier < 0 || tier > 2 || gen < 0 || gen > NbtLimits.LOT_MAX_GENERATION
                    || (root.contains("var") && !id(root, "var"))) {
                return Optional.empty();
            }
            String ln = root.getString("ln");
            if ((root.contains("ln") && !root.contains("ln", Tag.TAG_STRING))
                    || ln.codePointCount(0, ln.length()) > LotNbt.MAX_LINE_NAME) {
                return Optional.empty();
            }
            // T2: a name that is not clean player text is dropped; the root stays readable
            boolean named = root.contains("ln")
                    && io.github.slugblue.wildroots.core.safety.PlayerText.isClean(ln, LotNbt.MAX_LINE_NAME);
            return Optional.of(new Mother(new GenomeRecord(g.getInt("v"), g.getString("sp"), g.getLong("lh"), a), e,
                    root.contains("var") ? Optional.of(root.getString("var")) : Optional.empty(), tier,
                    root.getByte("ci") == 1, named ? Optional.of(ln) : Optional.empty(), gen, root.getByte("fl"), gr,
                    root.getByte("bt") == 1));
        } catch (RuntimeException ex) {
            return Optional.empty();
        }
    }

    private static boolean id(CompoundTag c, String key) {
        return c.contains(key, Tag.TAG_STRING) && c.getString(key).length() <= NbtLimits.GENOME_MAX_SPECIES_LENGTH
                && ResourceLocation.tryParse(c.getString(key)) != null;
    }

    public static CompoundTag write(Mother m) {
        CompoundTag root = new CompoundTag();
        root.putInt("v", CURRENT);
        CompoundTag g = new CompoundTag();
        g.putInt("v", m.genome().v());
        g.putString("sp", m.genome().species());
        g.putLong("lh", m.genome().lh());
        g.put("a", new LongArrayTag(m.genome().a()));
        root.put("g", g);
        root.put("e", new ByteArrayTag(m.noise()));
        m.cultivar().ifPresent(c -> root.putString("var", c));
        if (m.tier() != 0) {
            root.putByte("ct", (byte) m.tier());
        }
        if (m.inherited()) {
            root.putByte("ci", (byte) 1);
        }
        m.lineName().ifPresent(n -> root.putString("ln", n));
        if (m.generation() != 0) {
            root.putInt("gen", m.generation());
        }
        if (m.flags() != 0) {
            root.putByte("fl", (byte) m.flags());
        }
        int gr = ProduceStacks.normalise(m.grade(), m.bitter());
        root.putByte("gr", (byte) gr);
        if (m.bitter()) {
            root.putByte("bt", (byte) 1);
        }
        return root;
    }
}
