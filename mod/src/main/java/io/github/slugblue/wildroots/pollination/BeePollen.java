package io.github.slugblue.wildroots.pollination;

import io.github.slugblue.wildroots.core.safety.NbtLimits;
import io.github.slugblue.wildroots.genetics.codec.FatherRecord;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.LongArrayTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ICapabilitySerializable;
import net.minecraftforge.common.util.LazyOptional;

/**
 * Pollen a bee carries (docs/05-architecture.md §10.3 item 4; ADR-0009; GDD
 * §4.3): one record — species, the plant it came from, a copy of that
 * plant's genome (by value, the plant may be gone later), the visits since
 * and its age in ticks <b>outside the hive</b>.
 *
 * <p>It is a capability on {@code Bee}, so it is saved with the entity,
 * also inside a hive ({@code ForgeCaps} survive {@code removeIgnoredBeeTags},
 * S2). The age grows in {@code LivingTickEvent}, which does not fire while
 * the bee sits in a hive; by world time the 1200-tick window would never be
 * reachable (a bee with nectar stays in the hive ≥ 2400 ticks; S2; default
 * of STATUS.md, no owner answer). No UUID or entity reference is stored
 * anywhere: a bee leaves the hive as a new entity.
 *
 * <p>NBT: {@code {sp, src, lh, a, v, age}}; the decoder is type-safe and
 * never throws: anything malformed or over the genome limits gives empty
 * pollen (SEC-09).
 */
public final class BeePollen {

    public static final ResourceLocation ID = new ResourceLocation("wildroots", "bee_pollen");

    /** Pollen older than this many ticks outside the hive is stale (GDD §4.3). */
    public static final int MAX_AGE_OUTSIDE = 1200;
    /** Pollen is deposited at most on the 2nd visit after it was collected (GDD §4.3). */
    public static final int MAX_VISITS = 2;

    private String species = "";
    private long source;
    private FatherRecord genome;
    private int visits;
    private int ageOutside;
    /** Transient: the nectar the bee carries now came from our goal (not saved). */
    boolean nectarFromUs;
    /** Transient: {@code hasNectar} at the previous tick (not saved). */
    boolean hadNectar;

    public boolean hasPollen() {
        return !species.isEmpty() && genome != null;
    }

    public String species() {
        return species;
    }

    public long source() {
        return source;
    }

    public FatherRecord genome() {
        return genome;
    }

    public int visits() {
        return visits;
    }

    public int ageOutside() {
        return ageOutside;
    }

    /**
     * Whether the pollen can still be deposited: collected at most
     * {@value #MAX_VISITS} visits and {@value #MAX_AGE_OUTSIDE} ticks outside
     * the hive ago.
     */
    public boolean fresh() {
        return hasPollen() && visits < MAX_VISITS && ageOutside <= MAX_AGE_OUTSIDE;
    }

    /** Pollen of a visited plant replaces whatever the bee carried. */
    public void collect(String species, long source, FatherRecord genome) {
        this.species = species == null ? "" : species;
        this.source = source;
        this.genome = genome;
        this.visits = 0;
        this.ageOutside = 0;
    }

    /** A visit that did not replace the pollen (a vanilla flower). */
    public void countVisit() {
        if (hasPollen() && visits < Integer.MAX_VALUE) {
            visits++;
        }
    }

    /** One tick of the living bee (outside the hive). */
    public void tickOutside() {
        if (hasPollen() && ageOutside < Integer.MAX_VALUE) {
            ageOutside++;
        }
    }

    public void clear() {
        collect("", 0, null);
    }

    /** Test hook: set the counters directly. */
    public void setCounters(int visits, int ageOutside) {
        this.visits = Math.max(0, visits);
        this.ageOutside = Math.max(0, ageOutside);
    }

    public CompoundTag save() {
        CompoundTag t = new CompoundTag();
        if (!hasPollen()) {
            return t;
        }
        t.putString("sp", species);
        t.putLong("src", source);
        t.putLong("lh", genome.lh());
        t.put("a", new LongArrayTag(genome.a()));
        t.putInt("v", visits);
        t.putInt("age", ageOutside);
        return t;
    }

    public void load(CompoundTag t) {
        clear();
        if (t == null || !t.contains("sp", Tag.TAG_STRING) || !t.contains("a", Tag.TAG_LONG_ARRAY)
                || !t.contains("lh", Tag.TAG_LONG)) {
            return;
        }
        String sp = t.getString("sp");
        long[] a = t.getLongArray("a");
        if (sp.isEmpty() || sp.length() > NbtLimits.GENOME_MAX_SPECIES_LENGTH || ResourceLocation.tryParse(sp) == null
                || a.length == 0 || a.length > NbtLimits.GENOME_MAX_LONGS) {
            return;
        }
        collect(sp, t.getLong("src"), new FatherRecord(t.getLong("lh"), a));
        setCounters(t.getInt("v"), t.getInt("age"));
    }

    /** Provider attached to every bee. */
    public static final class Provider implements ICapabilitySerializable<CompoundTag> {

        private final BeePollen pollen = new BeePollen();
        private final LazyOptional<BeePollen> self = LazyOptional.of(() -> pollen);

        @Override
        public <T> LazyOptional<T> getCapability(Capability<T> cap, Direction side) {
            return BeePollenCapability.CAPABILITY.orEmpty(cap, self);
        }

        @Override
        public CompoundTag serializeNBT() {
            return pollen.save();
        }

        @Override
        public void deserializeNBT(CompoundTag nbt) {
            pollen.load(nbt);
        }
    }
}
