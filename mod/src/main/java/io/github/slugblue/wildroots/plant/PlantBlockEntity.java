package io.github.slugblue.wildroots.plant;

import com.mojang.logging.LogUtils;
import io.github.slugblue.wildroots.core.registry.ModBlockEntities;
import io.github.slugblue.wildroots.core.rng.RngKeys;
import io.github.slugblue.wildroots.core.safety.AuditLog;
import io.github.slugblue.wildroots.core.safety.EntryPoint;
import io.github.slugblue.wildroots.core.safety.Guarded;
import io.github.slugblue.wildroots.core.safety.LogThrottle;
import io.github.slugblue.wildroots.data.Definitions;
import io.github.slugblue.wildroots.data.DefinitionsSnapshot;
import io.github.slugblue.wildroots.genetics.api.Classification;
import io.github.slugblue.wildroots.genetics.api.CultivarId;
import io.github.slugblue.wildroots.genetics.api.GeneticsService;
import io.github.slugblue.wildroots.genetics.api.PatchPassport;
import io.github.slugblue.wildroots.genetics.api.SpeciesDef;
import io.github.slugblue.wildroots.genetics.codec.DecodeResult;
import io.github.slugblue.wildroots.genetics.model.Genome;
import io.github.slugblue.wildroots.genetics.phenotype.NoiseVector;
import io.github.slugblue.wildroots.item.LotFathersNbt;
import io.github.slugblue.wildroots.item.LotFlags;
import io.github.slugblue.wildroots.item.LotStacks;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.slf4j.Logger;

/**
 * State of one planted plant (docs/05-architecture.md §6.1–6.2; ADR-0003):
 * genome, noise, passport, planter, cultivar status, line name, generation,
 * flags and the pending lot. <b>No ticker</b>; it works in random ticks,
 * interaction and saving only.
 *
 * <p>Lazy: the stored genome is decoded on the first server access, and a
 * wild plant keeps only its passport until then (worldgen §3.3); its genome
 * is realised deterministically from {@code (worldSeed, patchOrigin, pos)}
 * and saved from then on.
 *
 * <p>Inert objects (§5.3, §6.1; SEC-09): a root from a future version,
 * damaged or over the NBT limit is inert for good while loaded; a plant whose
 * species is missing from the definitions sleeps and wakes up when the
 * species returns. Either way it does not grow or pollinate, its raw root is
 * written back unchanged on save, and nothing of it is copied into new
 * objects (no drops with its data).
 */
public class PlantBlockEntity extends BlockEntity implements LotStacks.RareClaim {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final LogThrottle INERT_LOG = new LogThrottle(System::currentTimeMillis, 5 * 60 * 1000L);

    /** What the plant can do now. */
    public enum Status {
        /** Data decoded or decodable. */
        OK,
        /** Species missing from the definitions: sleeps, wakes up when it returns. */
        SLEEPING,
        /** Future version, damage or over the limit: inert while loaded. */
        INERT
    }

    /** A plant ready for the server logic: definitions, genome and noise. */
    public record Live(DefinitionsSnapshot snapshot, DefinitionsSnapshot.SpeciesEntry species, Genome genome,
                       NoiseVector noise, PlantState data) {

        public SpeciesDef def() {
            return species.def();
        }

        public GeneticsService genetics() {
            return snapshot.genetics();
        }
    }

    private Status status = Status.INERT;
    private String inertReason = "no plant data";
    /** Stored data; {@code null} while inert. */
    private PlantState state;
    /** Raw root kept while the plant is not {@link Status#OK}, written back unchanged. */
    private CompoundTag raw;
    /** Decoded genome and the species definition it was decoded for. */
    private Genome genome;
    private SpeciesDef decodedFor;
    /** Transient: the rare flag was claimed by the removal of the block (SEC-21, see {@link #claimForRemoval}). */
    private boolean claimedByRemoval;
    /** Cached chance of the next stage per random tick (§10.1); NaN = not computed. */
    private float growthChance = Float.NaN;

    public PlantBlockEntity(BlockPos pos, BlockState blockState) {
        this(ModBlockEntities.PLANT.get(), pos, blockState);
    }

    protected PlantBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState blockState) {
        super(type, pos, blockState);
    }

    // ------------------------------------------------------------------ persistence

    @Override
    @EntryPoint
    public void load(CompoundTag tag) {
        super.load(tag);
        Tag root = tag.get(PlantNbt.ROOT);
        Guarded.run("plant load", worldPosition, () -> readRoot(root),
                () -> makeInert("load failed", root instanceof CompoundTag c ? c.copy() : null));
    }

    private void readRoot(Tag root) {
        genome = null;
        decodedFor = null;
        growthChance = Float.NaN;
        PlantNbt.Result r = PlantNbt.read(root);
        if (r instanceof PlantNbt.Ok ok) {
            state = ok.state();
            if (Definitions.current().species(state.species).isPresent()) {
                status = Status.OK;
                raw = null;
            } else {
                status = Status.SLEEPING;
                inertReason = "unknown species " + state.species;
                raw = (CompoundTag) root.copy();
            }
        } else {
            PlantNbt.Inert inert = (PlantNbt.Inert) r;
            makeInert(inert.reason(), root instanceof CompoundTag c ? c.copy() : null);
        }
    }

    private void makeInert(String reason, CompoundTag keep) {
        status = Status.INERT;
        inertReason = reason;
        state = null;
        genome = null;
        decodedFor = null;
        raw = keep;
        String where = String.valueOf(worldPosition);
        if (INERT_LOG.allow(where)) {
            LOGGER.warn("Wild Roots: plant at {} is inert: {}", where, reason);
            AuditLog.record("inert_object", "kind", "plant", "pos", where, "reason", reason);
        }
    }

    @Override
    @EntryPoint
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        Guarded.run("plant save", worldPosition, () -> {
            if (status != Status.OK && raw != null) {
                tag.put(PlantNbt.ROOT, raw.copy());
            } else if (state != null) {
                tag.put(PlantNbt.ROOT, PlantNbt.write(state));
            }
        }, () -> {
            // never lose the plant on a failed save: the raw root if kept, else its essential data
            CompoundTag keep = raw != null ? raw.copy() : essentialRoot(state);
            if (keep != null) {
                tag.put(PlantNbt.ROOT, keep);
            }
        });
    }

    /**
     * The root written part by part, each part guarded, so a part that
     * cannot be written (typically the pending lot) is the only thing lost:
     * genome and noise, passport, planter, cultivar status, line name,
     * generation, lot flags, plant flags ({@code MOTHER}, {@code MUTANT},
     * {@code rareClaimed}) and the pending lot. Used only when the full
     * write failed; no extra copy of the root is kept in memory.
     */
    static CompoundTag essentialRoot(PlantState state) {
        if (state == null) {
            return null;
        }
        CompoundTag root = new CompoundTag();
        root.putInt("v", PlantNbt.CURRENT);
        if (state.species != null) {
            root.putString("sp", state.species);
        }
        part(root, state, s -> {
            s.genome = state.genome;
            s.noise = state.noise;
        }, "g", "e");
        part(root, state, s -> s.passport = state.passport, "pp");
        part(root, state, s -> {
            s.planterMost = state.planterMost;
            s.planterLeast = state.planterLeast;
        }, "um", "ul");
        part(root, state, s -> {
            s.cultivar = state.cultivar;
            s.tier = state.tier;
            s.inherited = state.inherited;
        }, "cv", "ct", "ci");
        part(root, state, s -> s.lineName = state.lineName, "ln");
        part(root, state, s -> s.generation = state.generation, "gen");
        part(root, state, s -> s.lotFlags = state.lotFlags, "fl");
        part(root, state, s -> s.plantFlags = state.plantFlags, "pf");
        if (root.contains("g")) {
            part(root, state, s -> {
                s.genome = state.genome;
                s.pending = state.pending;
            }, "pl");
        }
        return root;
    }

    /** Writes the keys {@code keys} of a root holding only what {@code fill} copies; nothing on a failure. */
    private static void part(CompoundTag root, PlantState state, java.util.function.Consumer<PlantState> fill,
                             String... keys) {
        try {
            PlantState s = new PlantState();
            s.species = state.species;
            fill.accept(s);
            CompoundTag one = PlantNbt.write(s);
            for (String k : keys) {
                if (one.contains(k)) {
                    root.put(k, one.get(k));
                }
            }
        } catch (RuntimeException ignored) {
            // this part could not be written; the others are kept
        }
    }

    /** SEC-34: the logic of this plant threw; it becomes inert and keeps its data as it was. */
    public void markFailed(String reason) {
        if (status == Status.INERT) {
            return;
        }
        makeInert(reason, state == null ? raw : PlantNbt.write(state));
    }

    /**
     * The block is being removed (SEC-21): the flag is claimed now, without
     * {@code setChanged()} because the block entity goes away, and the drops
     * computed after this (survival break) still get it once.
     */
    public void claimForRemoval() {
        if (rareAvailable()) {
            state.plantFlags = (byte) (state.plantFlags | PlantState.RARE_CLAIMED);
            claimedByRemoval = true;
        }
    }

    /** Whether the drops of this plant carry the {@code mutation} flag (claim not consumed here). */
    public boolean rareForDrops() {
        return rareAvailable() || claimedByRemoval;
    }

    /** Whether the {@code mutation} rare flag is still to be given (looked at, not consumed). */
    public boolean rareAvailable() {
        return state != null && state.has(PlantState.MUTANT) && !state.has(PlantState.RARE_CLAIMED);
    }

    /** Empty for a built-in species (render by {@code BlockState}), species id otherwise (§6.2). */
    @Override
    public CompoundTag getUpdateTag() {
        String species = state == null ? null : state.species;
        boolean builtIn = getBlockState().getBlock() instanceof FieldPlantBlock b && b.species().equals(species);
        return PlantNbt.updateTag(builtIn || species == null, species);
    }

    // ------------------------------------------------------------------ status

    public Status status() {
        return status;
    }

    public String inertReason() {
        return status == Status.OK ? "" : inertReason;
    }

    /** Stored data (read-only view for tests and tooltips); {@code null} while inert. */
    public PlantState data() {
        return state;
    }

    /** Whether the stored genome is still undecoded (lazy) or not realised (wild passport only). */
    public boolean genomeDecoded() {
        return genome != null;
    }

    public float growthChance() {
        return growthChance;
    }

    public void setGrowthChance(float chance) {
        this.growthChance = chance;
    }

    // ------------------------------------------------------------------ server access

    /**
     * The plant for server logic: decodes the genome, realises a wild plant,
     * wakes a sleeping plant whose species returned. Empty while inert or
     * sleeping. Server thread only.
     */
    public Optional<Live> live(ServerLevel level) {
        if (status == Status.INERT || state == null) {
            return Optional.empty();
        }
        DefinitionsSnapshot snap = Definitions.current();
        Optional<DefinitionsSnapshot.SpeciesEntry> entry = snap.species(state.species);
        if (entry.isEmpty()) {
            if (status == Status.OK) {
                status = Status.SLEEPING;
                inertReason = "unknown species " + state.species;
                raw = PlantNbt.write(state);
            }
            return Optional.empty();
        }
        if (status == Status.SLEEPING) {
            status = Status.OK;
            raw = null;
            genome = null;
            growthChance = Float.NaN;
        }
        SpeciesDef def = entry.get().def();
        if (genome == null || decodedFor != def) {
            if (!decode(level, snap, def)) {
                return Optional.empty();
            }
        }
        return Optional.of(new Live(snap, entry.get(), genome, new NoiseVector(state.noise), state));
    }

    private boolean decode(ServerLevel level, DefinitionsSnapshot snap, SpeciesDef def) {
        GeneticsService gs = snap.genetics();
        if (state.genome == null) {
            return realiseWild(level, snap, def);
        }
        DecodeResult r = gs.decode(state.genome, def);
        if (r instanceof DecodeResult.Valid v) {
            genome = v.genome();
            decodedFor = def;
            growthChance = Float.NaN;
            if (v.migrated()) {
                v.warnings().forEach(w -> LOGGER.warn("Wild Roots: plant at {}: {}", worldPosition, w));
                NoiseVector n = new NoiseVector(state.noise);
                if (v.storedLayout().isPresent()) {
                    n = gs.migrateNoise(n, v.storedLayout().get(), def,
                            PlantService.noiseMigrationKey(worldPosition, state.species)).orElse(n);
                }
                int storedV = state.genome.v();
                state.genome = gs.encode(genome);
                state.noise = n.bytes();
                if (state.pending != null) {
                    state.pending = migrateFathers(gs, def, storedV, state.pending);
                }
                setChanged();
            }
            return true;
        }
        makeInert(((DecodeResult.Inert) r).reason(), PlantNbt.write(state));
        return false;
    }

    /** Every genome of the root migrates, fathers of the pending lot included (§5.3, S10). */
    private PendingLot migrateFathers(GeneticsService gs, SpeciesDef def, int lotV, PendingLot p) {
        List<LotFathersNbt.Father> out = new ArrayList<>(p.fathers().size());
        for (LotFathersNbt.Father f : p.fathers()) {
            DecodeResult r = gs.decode(f.genome().withLot(lotV, state.species), def);
            if (r instanceof DecodeResult.Valid v) {
                out.add(new LotFathersNbt.Father(gs.encode(v.genome()).toFather(), f.weight(), f.donor()));
            } else {
                LOGGER.warn("Wild Roots: plant at {}: father dropped in migration: {}", worldPosition,
                        ((DecodeResult.Inert) r).reason());
            }
        }
        return new PendingLot(out, p.pOut(), p.brush(), p.bees());
    }

    /** Wild plant touched for the first time: genome and ε from {@code (worldSeed, patchOrigin, pos)}. */
    private boolean realiseWild(ServerLevel level, DefinitionsSnapshot snap, SpeciesDef def) {
        Passport p = state.passport;
        if (p.origin() != Passport.Origin.WILD) {
            // village plants are realised by the village tasks (WR-16x); until then they sleep
            return false;
        }
        long seed = level.getSeed();
        long pos = worldPosition.asLong();
        GeneticsService gs = snap.genetics();
        Optional<Genome> g = gs.wildPlant(def, new PatchPassport(state.species, p.category(), p.rich(),
                p.patchOrigin(), p.starter(), seed), RngKeys.wildPlant(seed, p.patchOrigin(), pos));
        Optional<NoiseVector> n = gs.rollNoise(def, RngKeys.wildNoise(seed, p.patchOrigin(), pos));
        if (g.isEmpty() || n.isEmpty()) {
            return false;
        }
        Classification c = gs.classify(g.get(), def, snap.profiles(state.species), Optional.empty())
                .orElse(Classification.WILD);
        genome = g.get();
        decodedFor = def;
        growthChance = Float.NaN;
        state.genome = gs.encode(genome);
        state.noise = n.get().bytes();
        setStatus(c);
        state.lotFlags |= LotFlags.FROM_WILD;
        setChanged();
        return true;
    }

    private void setStatus(Classification c) {
        state.cultivar = c.cultivar().map(CultivarId::value).orElse(null);
        state.tier = (byte) c.tier();
        state.inherited = c.inherited();
    }

    // ------------------------------------------------------------------ writers (server)

    /** What a new plant starts with. */
    public record Init(String species, Genome genome, NoiseVector noise, Passport passport, UUID planter,
                       Classification status, String lineName, int generation, int lotFlags, int plantFlags) {
    }

    /** Fills a freshly placed plant (planting, mother root, tests). */
    public void init(GeneticsService genetics, Init init) {
        PlantState s = new PlantState();
        s.species = init.species();
        s.genome = genetics.encode(init.genome());
        s.noise = init.noise().bytes();
        s.passport = init.passport() == null ? Passport.PLAYER : init.passport();
        if (init.planter() != null) {
            s.planterMost = init.planter().getMostSignificantBits();
            s.planterLeast = init.planter().getLeastSignificantBits();
        }
        state = s;
        setStatus(init.status() == null ? Classification.WILD : init.status());
        s.lineName = init.lineName();
        s.generation = init.generation();
        s.lotFlags = (byte) (init.lotFlags() & LotFlags.INHERITED);
        s.plantFlags = (byte) init.plantFlags();
        status = Status.OK;
        raw = null;
        genome = init.genome();
        decodedFor = null;
        growthChance = Float.NaN;
        setChanged();
    }

    /** A wild plant that keeps only its passport until the first server access (worldgen §3.3). */
    public void initWild(String species, Passport passport) {
        PlantState s = new PlantState();
        s.species = species;
        s.passport = passport;
        state = s;
        status = Status.OK;
        raw = null;
        genome = null;
        decodedFor = null;
        growthChance = Float.NaN;
        setChanged();
    }

    /** Replaces the pending lot (seed set, WR-141). */
    public void setPendingLot(PendingLot lot) {
        if (state != null) {
            state.pending = lot;
            setChanged();
        }
    }

    /** Sets or clears a plant flag of {@link PlantState}. */
    public void setPlantFlag(int flag, boolean on) {
        if (state != null) {
            state.plantFlags = (byte) (on ? state.plantFlags | flag : state.plantFlags & ~flag);
            setChanged();
        }
    }

    /**
     * SEC-21: the {@code mutation} rare flag goes to exactly one item per
     * mutant plant; {@code rareClaimed} is saved, so later harvests, regrowth
     * and copies never get it again.
     */
    @Override
    public boolean claimRare() {
        if (!rareAvailable()) {
            return false;
        }
        setPlantFlag(PlantState.RARE_CLAIMED, true);
        return true;
    }
}
