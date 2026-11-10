package io.github.slugblue.wildroots.pollination;

import io.github.slugblue.wildroots.core.rng.RngKeys;
import io.github.slugblue.wildroots.core.safety.LoadedLevelAccess;
import io.github.slugblue.wildroots.core.safety.WorldSecret;
import io.github.slugblue.wildroots.genetics.api.Defaults;
import io.github.slugblue.wildroots.genetics.api.FatherPick;
import io.github.slugblue.wildroots.genetics.api.GeneticsService;
import io.github.slugblue.wildroots.genetics.api.SpeciesDef;
import io.github.slugblue.wildroots.genetics.codec.DecodeResult;
import io.github.slugblue.wildroots.genetics.pollination.Brush;
import io.github.slugblue.wildroots.genetics.pollination.Candidate;
import io.github.slugblue.wildroots.genetics.pollination.Donor;
import io.github.slugblue.wildroots.genetics.pollination.FatherSelector;
import io.github.slugblue.wildroots.genetics.random.Hash64;
import io.github.slugblue.wildroots.item.LotFathersNbt;
import io.github.slugblue.wildroots.plant.FieldPlantBlock;
import io.github.slugblue.wildroots.plant.PendingLot;
import io.github.slugblue.wildroots.plant.PlantBlockEntity;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

/**
 * Seed set (docs/05-architecture.md §10.2; ADR-0008; GDD §4.1, §14): once
 * per cycle, on the step from flowering to seeds, the plant collects its
 * fathers.
 *
 * <ol>
 *   <li>Candidates come from {@link BloomIndex} around the mother (radius
 *   of the species, ±2 in Y, ≤ 1024 entries visited, ≤ 64 kept).</li>
 *   <li>Each candidate's block entity is read only through
 *   {@link LoadedLevelAccess} (SEC-32): an unloaded chunk is "empty" and is
 *   never loaded; an inert, sleeping or other-species candidate is
 *   skipped.</li>
 *   <li>Donors already in the plant (bees, brush) join with their weights;
 *   the engine chooses fathers and {@code p_out}.</li>
 *   <li>Fathers are written into the pending lot <b>by value</b> (encoded
 *   genomes), so a father broken later stays in the lot (GDD §14).</li>
 * </ol>
 */
public final class PollinationService {

    private static final long SAMPLE_SALT = Hash64.str("bloom_sample");

    private PollinationService() {
    }

    /** A candidate read from the world: its position too (tests, diagnostics). */
    public record Found(BlockPos pos, Candidate candidate) {
    }

    /**
     * Flowering plants of {@code def} around {@code pos} that can father seeds:
     * from the bloom index, read only in loaded chunks.
     */
    public static List<Found> candidates(ServerLevel level, BlockPos pos, SpeciesDef def, long rngKey) {
        BloomIndex idx = BloomIndex.peek(level);
        if (idx == null) {
            return List.of();
        }
        BloomIndex.Sample s = idx.sample(SpeciesIndex.of(def.id()), pos, def.pollination().radius(),
                Defaults.POLLINATION_DY, rngKey);
        List<Found> out = new ArrayList<>(s.count());
        for (int i = 0; i < s.count(); i++) {
            BlockPos at = BlockPos.of(s.positions()[i]);
            Optional<PlantBlockEntity> be = LoadedLevelAccess.blockEntity(level, at, PlantBlockEntity.class);
            if (be.isEmpty() || !(be.get().getBlockState().getBlock() instanceof FieldPlantBlock)
                    || FieldPlantBlock.stage(be.get().getBlockState()) != FieldPlantBlock.FLOWER_STAGE) {
                continue;
            }
            Optional<PlantBlockEntity.Live> live = be.get().live(level);
            if (live.isEmpty() || !live.get().def().id().equals(def.id())) {
                continue;
            }
            out.add(new Found(at, new Candidate(live.get().genome(), at.getX() - pos.getX(), at.getY() - pos.getY(),
                    at.getZ() - pos.getZ())));
        }
        return out;
    }

    /**
     * Seed set of the plant at {@code pos}: fathers from the neighbours and
     * the donors in its pending lot, {@code p_out}, written as the new
     * pending lot. Called once, before the stage becomes 5.
     */
    public static void seedSet(ServerLevel level, BlockPos pos, PlantBlockEntity be, PlantBlockEntity.Live live) {
        GeneticsService gs = live.genetics();
        SpeciesDef def = live.def();
        long key = RngKeys.fathers(WorldSecret.get(level.getServer()), pos.asLong(), (int) level.getGameTime());
        List<Candidate> nearby = new ArrayList<>();
        for (Found f : candidates(level, pos, def, Hash64.of(key, SAMPLE_SALT))) {
            nearby.add(f.candidate());
        }
        PendingLot before = live.data().pending();
        List<Donor> donors = donors(gs, def, live, before);
        FatherSelector.Selection sel = gs.chooseFathers(def, live.genome(), nearby, donors, key)
                .orElse(new FatherSelector.Selection(List.of(), 0, 0, 0));
        boolean bees = before != null && before.bees();
        Brush brush = before == null ? Brush.NONE : before.brush();
        double pOut = gs.pOut(def, sel, bees, brush);
        List<LotFathersNbt.Father> fathers = new ArrayList<>(sel.fathers().size());
        for (FatherPick f : sel.fathers()) {
            fathers.add(new LotFathersNbt.Father(gs.encode(f.genome()).toFather(), (float) f.weight(), f.donor()));
        }
        be.setPendingLot(new PendingLot(fathers, (float) Math.max(0, Math.min(1, pOut)), brush, bees));
    }

    /** Donors (bee, brush) waiting in the pending lot, decoded in the mother's species. */
    static List<Donor> donors(GeneticsService gs, SpeciesDef def, PlantBlockEntity.Live live, PendingLot pending) {
        if (pending == null) {
            return List.of();
        }
        int v = live.data().genome() == null ? 0 : live.data().genome().v();
        List<Donor> out = new ArrayList<>();
        for (LotFathersNbt.Father f : pending.fathers()) {
            if (!f.donor()) {
                continue;
            }
            if (gs.decode(f.genome().withLot(v, def.id()), def) instanceof DecodeResult.Valid ok) {
                out.add(new Donor(ok.genome(), f.weight(), false));
            }
        }
        return out;
    }

    /** What a bee visit did (tests, diagnostics). */
    public enum Visit {
        /** Not a flowering plant of a species with bees, or not loaded: nothing happened. */
        NONE,
        /** Visited; the bee had no fresh pollen of this species from another plant. */
        VISITED,
        /** Visited and the bee's pollen became a donor of the plant. */
        DONOR
    }

    /**
     * A bee finished hovering at the plant at {@code pos} (§10.3 item 3; GDD
     * §4.3): fresh pollen of the same species from another plant becomes a
     * donor of weight {@code Defaults.BEE_DONOR_WEIGHT}; the plant is marked
     * bee-visited; the bee's pollen is replaced by this plant's (a visit to
     * another species replaces it too); the bee gets nectar.
     */
    public static Visit beeVisit(ServerLevel level, net.minecraft.world.entity.animal.Bee bee, BlockPos pos) {
        Optional<PlantBlockEntity> be = BeeCropPollinateGoal.flowering(level, pos);
        Optional<BeePollen> pollen = bee.getCapability(BeePollenCapability.CAPABILITY).resolve();
        if (be.isEmpty() || pollen.isEmpty()) {
            return Visit.NONE;
        }
        Optional<PlantBlockEntity.Live> live = be.get().live(level);
        if (live.isEmpty() || live.get().def().pollination().bees().isEmpty()) {
            return Visit.NONE;
        }
        BeePollen p = pollen.get();
        String species = live.get().def().id();
        boolean donor = p.fresh() && species.equals(p.species()) && p.source() != pos.asLong();
        be.get().beeVisit(donor ? p.genome() : null, (float) Defaults.BEE_DONOR_WEIGHT);
        p.collect(species, pos.asLong(), live.get().genetics().encode(live.get().genome()).toFather());
        p.nectarFromUs = true;
        bee.setHasNectar(true); // package-private in 1.20.1, opened by the access transformer (S2)
        // the journal hook runs last and isolated: a failure never leaves the visit half done (review 1, M3)
        io.github.slugblue.wildroots.core.safety.Guarded.run("journal bee visit", pos,
                () -> io.github.slugblue.wildroots.journal.HintTriggers.beeVisited(level, be.get()));
        return donor ? Visit.DONOR : Visit.VISITED;
    }
}
