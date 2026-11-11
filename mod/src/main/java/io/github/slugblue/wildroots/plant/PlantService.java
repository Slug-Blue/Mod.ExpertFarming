package io.github.slugblue.wildroots.plant;

import io.github.slugblue.wildroots.core.registry.ModBlocks;
import io.github.slugblue.wildroots.core.rng.RngKeys;
import io.github.slugblue.wildroots.core.safety.LoadedLevelAccess;
import io.github.slugblue.wildroots.core.safety.WorldSecret;
import io.github.slugblue.wildroots.data.Definitions;
import io.github.slugblue.wildroots.data.DefinitionsSnapshot;
import io.github.slugblue.wildroots.genetics.api.Classification;
import io.github.slugblue.wildroots.genetics.api.CultivarId;
import io.github.slugblue.wildroots.genetics.api.FatherPick;
import io.github.slugblue.wildroots.genetics.api.GeneticsService;
import io.github.slugblue.wildroots.genetics.api.LotRecord;
import io.github.slugblue.wildroots.genetics.api.SpeciesDef;
import io.github.slugblue.wildroots.genetics.model.Genome;
import io.github.slugblue.wildroots.genetics.model.Locus;
import io.github.slugblue.wildroots.genetics.phenotype.NoiseVector;
import io.github.slugblue.wildroots.item.LotNbt;
import io.github.slugblue.wildroots.item.LotStacks;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.FarmBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;

/**
 * Planting (docs/05-architecture.md §2 planting flow, §4.3, §21 item 1; GDD
 * §3.8, §5.3, §6.2).
 *
 * <p>The seedling of a lot is realised now: {@code offspring(lot,
 * Hash64(secret, lotId, remainingCount, packedPos))} and noise with the
 * {@code "noise"} key. The same lot, the same remaining count and the same
 * cell give the same plant, so breaking a young plant and planting again
 * rolls nothing new (SEC-26). A realised seedling lot plants exactly its
 * genome and noise. Classification runs here, the planter's UUID is kept.
 */
public final class PlantService {

    /** Built-in species and their blocks (§7.1). */
    private static final Map<String, java.util.function.Supplier<? extends FieldPlantBlock>> BLOCKS =
            Map.of(ModBlocks.WILD_CARROT_SPECIES, ModBlocks.WILD_CARROT);

    private PlantService() {
    }

    /** Block of a species, if it has one. */
    /** The plant blocks of the built-in species (WR-162: the vanilla crop each one replaces). */
    public static java.util.List<FieldPlantBlock> blocks() {
        return BLOCKS.values().stream().map(s -> (FieldPlantBlock) s.get()).toList();
    }

    public static Optional<FieldPlantBlock> blockFor(String species) {
        var b = BLOCKS.get(species);
        return b == null ? Optional.empty() : Optional.of(b.get());
    }

    /** Key of a noise migration at {@code pos}: the same as the block entity uses (§5.3). */
    public static long noiseMigrationKey(BlockPos pos, String species) {
        return io.github.slugblue.wildroots.genetics.random.Hash64.of(pos.asLong(),
                io.github.slugblue.wildroots.genetics.random.Hash64.str(species));
    }

    /**
     * Noise stored next to {@code genome} (seedling lot, mother root), in the
     * trait order of the current layout: migrated like the genome when the
     * layout changed (§5.3), unchanged otherwise.
     */
    static NoiseVector storedNoise(GeneticsService gs, io.github.slugblue.wildroots.genetics.codec.GenomeRecord genome,
                                   SpeciesDef def, byte[] noise, BlockPos pos) {
        NoiseVector n = new NoiseVector(noise);
        if (gs.decode(genome, def) instanceof io.github.slugblue.wildroots.genetics.codec.DecodeResult.Valid v
                && v.migrated() && v.storedLayout().isPresent()) {
            return gs.migrateNoise(n, v.storedLayout().get(), def, noiseMigrationKey(pos, def.id())).orElse(n);
        }
        return n;
    }

    /** Whether {@code pos} is free air above farmland (both read only in loaded chunks). */
    public static boolean canPlantAt(ServerLevel level, BlockPos pos) {
        Optional<BlockState> at = LoadedLevelAccess.blockState(level, pos);
        Optional<BlockState> below = LoadedLevelAccess.blockState(level, pos.below());
        return at.isPresent() && at.get().isAir() && below.isPresent() && below.get().getBlock() instanceof FarmBlock;
    }

    /** What a planted lot becomes. */
    public record Seedling(Genome genome, NoiseVector noise, Classification status, boolean mutant) {
    }

    /** The plant that {@code lot} with {@code remainingCount} seeds left gives at {@code pos}. */
    public static Optional<Seedling> seedling(ServerLevel level, LotNbt.Lot lot, int remainingCount, BlockPos pos) {
        DefinitionsSnapshot snap = Definitions.current();
        Optional<DefinitionsSnapshot.SpeciesEntry> e = snap.species(lot.species());
        Optional<LotRecord> rec = LotStacks.record(lot, snap);
        if (e.isEmpty() || rec.isEmpty()) {
            return Optional.empty();
        }
        SpeciesDef def = e.get().def();
        GeneticsService gs = snap.genetics();
        Optional<Genome> g;
        Optional<NoiseVector> n;
        if (lot.seedling()) {
            g = Optional.of(rec.get().mother());
            n = Optional.of(storedNoise(gs, lot.mother(), e.get().def(), lot.seedlingNoise().get(), pos));
        } else {
            long secret = WorldSecret.get(level.getServer());
            g = gs.offspring(def, rec.get(), RngKeys.offspring(secret, lot.lotId(), remainingCount, pos.asLong()));
            n = gs.rollNoise(def, RngKeys.noise(secret, lot.lotId(), remainingCount, pos.asLong()));
        }
        if (g.isEmpty() || n.isEmpty()) {
            return Optional.empty();
        }
        Classification c = gs.classify(g.get(), def, snap.profiles(lot.species()), lot.cultivar().map(CultivarId::of))
                .orElse(Classification.WILD);
        boolean mutant = !lot.seedling() && qualitativeMutation(g.get(), rec.get());
        return Optional.of(new Seedling(g.get(), n.get(), c, mutant));
    }

    /**
     * A qualitative allele that neither the mother nor any father carries at
     * that locus can only come from a mutation (GDD §3.8: "unusual plant";
     * the mutant gets {@code rare_source: mutation} once, SEC-21).
     */
    static boolean qualitativeMutation(Genome child, LotRecord parents) {
        for (Locus s : child.layout().qualitative()) {
            for (int l = 0; l < s.loci(); l++) {
                for (int w = 0; w < 2; w++) {
                    int a = child.allele(s, l, w);
                    boolean found = carries(parents.mother(), s, l, a);
                    for (FatherPick f : parents.fathers()) {
                        found |= carries(f.genome(), s, l, a);
                    }
                    if (!found) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private static boolean carries(Genome g, Locus s, int locus, int allele) {
        Locus own = g.layout().section(s.trait());
        return own != null && locus < own.loci() && (g.allele(own, locus, 0) == allele || g.allele(own, locus, 1) == allele);
    }

    /**
     * Places the plant of {@code lot} at {@code pos} (free air above
     * farmland). Returns {@code false} and changes nothing if the lot cannot
     * be planted (inert, unknown species, no block for the species).
     */
    public static boolean plant(ServerLevel level, BlockPos pos, LotNbt.Lot lot, int remainingCount, UUID planter) {
        Optional<FieldPlantBlock> block = blockFor(lot.species());
        if (block.isEmpty() || !canPlantAt(level, pos)) {
            return false;
        }
        Optional<Seedling> s = seedling(level, lot, remainingCount, pos);
        if (s.isEmpty()) {
            return false;
        }
        return place(level, pos, block.get(), new PlantBlockEntity.Init(lot.species(), s.get().genome(),
                s.get().noise(), Passport.PLAYER, planter, s.get().status(), lot.lineName().orElse(null),
                lot.generation(), lot.flags(), s.get().mutant() ? PlantState.MUTANT : 0), 0);
    }

    /**
     * Plants a mother root (GDD §5.2): genome and noise bit for bit (migrated
     * only if the layout changed), the status of its plant, at harvest
     * ripeness and marked as a mother, so it flowers within
     * {@code seed_stage_min} and its harvest gives no food.
     */
    public static boolean plantMother(ServerLevel level, BlockPos pos, io.github.slugblue.wildroots.item.MotherNbt.Mother m,
                                      UUID planter) {
        Optional<FieldPlantBlock> block = blockFor(m.species());
        DefinitionsSnapshot snap = Definitions.current();
        Optional<DefinitionsSnapshot.SpeciesEntry> e = snap.species(m.species());
        if (block.isEmpty() || e.isEmpty() || !canPlantAt(level, pos)) {
            return false;
        }
        if (!(snap.genetics().decode(m.genome(), e.get().def())
                instanceof io.github.slugblue.wildroots.genetics.codec.DecodeResult.Valid v)) {
            return false;
        }
        Classification status = new Classification(m.cultivar().map(CultivarId::of), m.tier(), m.inherited());
        return place(level, pos, block.get(), new PlantBlockEntity.Init(m.species(), v.genome(),
                storedNoise(snap.genetics(), m.genome(), e.get().def(), m.noise(), pos), Passport.PLAYER, planter, status, m.lineName().orElse(null),
                m.generation(), m.flags(), PlantState.MOTHER), FieldPlantBlock.HARVEST_STAGE);
    }

    /** Sets the block, fills its block entity, computes look and growth chance of {@code stage}. */
    public static boolean place(ServerLevel level, BlockPos pos, FieldPlantBlock block, PlantBlockEntity.Init init,
                                int stage) {
        BlockState state = block.getStateForAge(stage);
        level.setBlock(pos, state, 3);
        Optional<PlantBlockEntity> be = LoadedLevelAccess.blockEntity(level, pos, PlantBlockEntity.class);
        if (be.isEmpty()) {
            return false;
        }
        be.get().init(Definitions.current().genetics(), init);
        be.get().live(level).ifPresent(live -> GrowthLogic.advance(level, pos, state, be.get(), live, stage));
        level.playSound(null, pos, SoundEvents.CROP_PLANTED, SoundSource.BLOCKS, 1.0f, 1.0f);
        level.gameEvent(null, GameEvent.BLOCK_PLACE, pos);
        return true;
    }
}
