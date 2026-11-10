package io.github.slugblue.wildroots.gametest.perf;

import io.github.slugblue.wildroots.core.registry.ModBlocks;
import io.github.slugblue.wildroots.data.Definitions;
import io.github.slugblue.wildroots.data.DefinitionsSnapshot;
import io.github.slugblue.wildroots.genetics.api.Classification;
import io.github.slugblue.wildroots.genetics.api.GeneticsService;
import io.github.slugblue.wildroots.genetics.api.PatchPassport;
import io.github.slugblue.wildroots.genetics.api.SpeciesDef;
import io.github.slugblue.wildroots.genetics.model.Genome;
import io.github.slugblue.wildroots.genetics.phenotype.NoiseVector;
import io.github.slugblue.wildroots.genetics.pollination.Brush;
import io.github.slugblue.wildroots.item.LotFathersNbt;
import io.github.slugblue.wildroots.plant.FieldPlantBlock;
import io.github.slugblue.wildroots.plant.Passport;
import io.github.slugblue.wildroots.plant.PendingLot;
import io.github.slugblue.wildroots.plant.PlantBlockEntity;
import io.github.slugblue.wildroots.plant.PlantService;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * WR-195: the real plant of the bench (replaces the S9 prototype). A plant is a
 * {@code wildroots:wild_carrot} player plant: a wild genome of one of five patch
 * categories (fixed keys), its noise, the planter's UUID, placed by
 * {@link PlantService#place} at a stage; a flowering plant carries a pending lot
 * of {@value #DONORS} bee donors.
 */
final class RealPlants {

    static final int DONORS = 4;
    static final long WORLD_SEED_FIXTURE = 0x5_9BE7CL;
    static final UUID PLANTER = new UUID(0x195L, 0x28L);
    private static final String[] CATS = {"meadow", "plains", "forest_edge", "flower_fields", "meadow"};

    private RealPlants() {
    }

    static SpeciesDef def() {
        return Definitions.current().species(ModBlocks.WILD_CARROT_SPECIES).map(DefinitionsSnapshot.SpeciesEntry::def)
                .orElseThrow(() -> new IllegalStateException("no wild_carrot"));
    }

    static Genome genome(long key) {
        GeneticsService gs = Definitions.current().genetics();
        PatchPassport pp = new PatchPassport(ModBlocks.WILD_CARROT_SPECIES, CATS[(int) Math.floorMod(key, 5L)], false,
                key / 5, false, WORLD_SEED_FIXTURE);
        return gs.wildPlant(def(), pp, key).orElseThrow();
    }

    static PlantBlockEntity.Init init(long key) {
        GeneticsService gs = Definitions.current().genetics();
        NoiseVector n = gs.rollNoise(def(), key ^ 0x7E57L).orElseThrow();
        return new PlantBlockEntity.Init(ModBlocks.WILD_CARROT_SPECIES, genome(key), n, Passport.PLAYER, PLANTER,
                Classification.WILD, null, 1, 0, 0);
    }

    /** Pending lot of {@code fathers} neighbour fathers and {@code donors} bee donors. */
    static PendingLot lot(long key, int fathers, int donors) {
        GeneticsService gs = Definitions.current().genetics();
        List<LotFathersNbt.Father> f = new ArrayList<>();
        for (int i = 0; i < fathers + donors; i++) {
            f.add(new LotFathersNbt.Father(gs.encode(genome(key * 16 + i)).toFather(), 1, i >= fathers));
        }
        return new PendingLot(f, 0.95f, Brush.NONE, donors > 0);
    }

    /** Places a real plant at {@code pos} (farmland below) at {@code stage}. */
    static boolean place(ServerLevel level, BlockPos pos, int stage, long key) {
        FieldPlantBlock block = ModBlocks.WILD_CARROT.get();
        if (!PlantService.place(level, pos, block, init(key), stage)) {
            return false;
        }
        if (stage == FieldPlantBlock.FLOWER_STAGE && level.getBlockEntity(pos) instanceof PlantBlockEntity be) {
            be.setPendingLot(lot(key, 0, DONORS));
        }
        return true;
    }

    /**
     * A block entity outside the world, as the server holds it after its first
     * access: the genome decoded. {@code worst} adds a pending lot of 4 fathers
     * and 4 donors (8 genomes).
     */
    static PlantBlockEntity offWorld(BlockPos pos, int stage, long key, boolean worst) {
        BlockState state = ModBlocks.WILD_CARROT.get().getStateForAge(stage);
        PlantBlockEntity be = new PlantBlockEntity(pos, state);
        be.init(Definitions.current().genetics(), init(key));
        be.setGrowthChance(0.25f);
        if (worst) {
            be.setPendingLot(lot(key, 4, 4));
        }
        return be;
    }

    /** As loaded from a chunk and not yet touched: the stored genome is not decoded. */
    static PlantBlockEntity lazy(BlockPos pos, int stage, long key) {
        PlantBlockEntity be = offWorld(pos, stage, key, false);
        CompoundTag tag = be.saveWithFullMetadata();
        return (PlantBlockEntity) BlockEntity.loadStatic(pos, be.getBlockState(), tag);
    }
}
