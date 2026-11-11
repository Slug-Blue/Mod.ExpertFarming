package io.github.slugblue.wildroots.gametest;

import io.github.slugblue.wildroots.core.Ids;
import io.github.slugblue.wildroots.core.registry.ModBlocks;
import io.github.slugblue.wildroots.data.Definitions;
import io.github.slugblue.wildroots.data.DefinitionsSnapshot;
import io.github.slugblue.wildroots.data.SpeciesInfo;
import io.github.slugblue.wildroots.genetics.api.Classification;
import io.github.slugblue.wildroots.genetics.api.PatchPassport;
import io.github.slugblue.wildroots.genetics.api.QuantTrait;
import io.github.slugblue.wildroots.genetics.api.SpeciesDef;
import io.github.slugblue.wildroots.genetics.codec.LayoutDescriptor;
import io.github.slugblue.wildroots.genetics.codec.LayoutDictionary;
import io.github.slugblue.wildroots.genetics.model.Genome;
import io.github.slugblue.wildroots.genetics.model.GenomeLayout;
import io.github.slugblue.wildroots.genetics.phenotype.NoiseVector;
import io.github.slugblue.wildroots.plant.FieldPlantBlock;
import io.github.slugblue.wildroots.plant.Passport;
import io.github.slugblue.wildroots.plant.PlantBlockEntity;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.UnaryOperator;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FarmBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** Shared set-up of the plant GameTests (WR-131…WR-138). */
final class PlantFixtures {

    static final String SP = ModBlocks.WILD_CARROT_SPECIES;
    /** A fixed "world seed" for genomes built in tests (not the world secret). */
    static final long WORLD_SEED_FIXTURE = 42L;
    static final BlockPos PLANT = new BlockPos(1, 2, 1);

    private PlantFixtures() {
    }

    static DefinitionsSnapshot.SpeciesEntry carrot() {
        return Definitions.current().species(SP).orElseThrow(() -> new IllegalStateException("no wild_carrot"));
    }

    /** Moist farmland under {@code rel}. */
    static void farmland(GameTestHelper helper, BlockPos rel) {
        helper.setBlock(rel.below(), Blocks.FARMLAND.defaultBlockState().setValue(FarmBlock.MOISTURE, 7));
    }

    /**
     * Moist farmland under {@code rel} and a light block (level 15) above it.
     * Test areas lie underground and their light depends on the world: a
     * crop with raw light below 8 and no sky pops on the next shape update
     * of a neighbour (vanilla {@code CropBlock#canSurvive}), so a test whose
     * cell is on the template edge must not rely on the world's light (WR-195).
     * The light engine applies the block a tick or two later: wait for
     * {@link #lit} before planting.
     */
    static void litFarmland(GameTestHelper helper, BlockPos rel) {
        farmland(helper, rel);
        helper.setBlock(rel.above(), Blocks.LIGHT.defaultBlockState());
    }

    /** Whether a crop at {@code rel} survives shape updates by its light (raw light ≥ 8 or sky). */
    static boolean lit(GameTestHelper helper, BlockPos rel) {
        BlockPos abs = helper.absolutePos(rel);
        return helper.getLevel().getRawBrightness(abs, 0) >= 8 || helper.getLevel().canSeeSky(abs);
    }

    /** Runs {@code body} once every cell is {@link #lit} (at most 40 ticks, then runs it anyway). */
    static void whenLit(GameTestHelper helper, List<BlockPos> cells, Runnable body, int waited) {
        if (waited >= 40 || cells.stream().allMatch(c -> lit(helper, c))) {
            body.run();
            return;
        }
        helper.runAfterDelay(1, () -> whenLit(helper, cells, body, waited + 1));
    }

    /** Farmland and a wild carrot at stage {@code stage}; returns its block entity. */
    static PlantBlockEntity place(GameTestHelper helper, BlockPos rel, int stage) {
        farmland(helper, rel);
        helper.setBlock(rel, ModBlocks.WILD_CARROT.get().getStateForAge(stage));
        BlockEntity be = helper.getBlockEntity(rel);
        if (!(be instanceof PlantBlockEntity p)) {
            throw new IllegalStateException("no plant block entity at " + rel + ": " + be);
        }
        return p;
    }

    /** A wild genome of the carrot, different for every {@code key}. */
    static Genome genome(long key) {
        DefinitionsSnapshot.SpeciesEntry e = carrot();
        return Definitions.current().genetics()
                .wildPlant(e.def(), new PatchPassport(SP, "meadow", false, 0, false, WORLD_SEED_FIXTURE), key)
                .orElseThrow();
    }

    static NoiseVector noise(long key) {
        return Definitions.current().genetics().rollNoise(carrot().def(), key).orElseThrow();
    }

    /** Fills {@code be} as a player plant with genome {@code key}. */
    static PlantBlockEntity plant(PlantBlockEntity be, long key) {
        be.init(Definitions.current().genetics(), new PlantBlockEntity.Init(SP, genome(key), noise(key + 1),
                Passport.PLAYER, new UUID(1, 2), Classification.WILD, null, 0, 0, 0));
        return be;
    }

    /**
     * Fills {@code be} as a planted mother root with genome {@code key}: the
     * only kind of biennial that leaves harvest ripeness (D1).
     */
    static PlantBlockEntity mother(PlantBlockEntity be, long key) {
        be.init(Definitions.current().genetics(), new PlantBlockEntity.Init(SP, genome(key), noise(key + 1),
                Passport.PLAYER, new UUID(1, 2), Classification.WILD, null, 0, 0,
                io.github.slugblue.wildroots.plant.PlantState.MOTHER));
        return be;
    }

    /** Full save, then a fresh block entity loaded from it (chunk save/load). */
    static PlantBlockEntity saveLoad(PlantBlockEntity be) {
        CompoundTag tag = be.saveWithFullMetadata();
        return (PlantBlockEntity) BlockEntity.loadStatic(be.getBlockPos(), be.getBlockState(), tag);
    }

    static BlockState stateAt(GameTestHelper helper, BlockPos rel) {
        return helper.getBlockState(rel);
    }

    static int stage(GameTestHelper helper, BlockPos rel) {
        BlockState s = helper.getBlockState(rel);
        return s.getBlock() instanceof FieldPlantBlock ? FieldPlantBlock.stage(s) : -1;
    }

    /** The carrot definition under another id, with {@code quant} applied to the trait list. */
    static SpeciesDef carrotAs(String id, UnaryOperator<List<QuantTrait>> quant) {
        SpeciesDef d = carrot().def();
        return new SpeciesDef(id, d.tempMin(), d.tempMax(), quant.apply(d.quantitative()), d.tradeoffs(), d.budget(),
                d.wild(), d.wildF(), d.qualitative(), d.starterFreq(), d.load(), d.mutation(), d.pollination(),
                d.timing(), d.qualityAxes());
    }

    /** The current snapshot plus {@code extra} (with the carrot's mod data) and extra layout history. */
    static DefinitionsSnapshot withSpecies(DefinitionsSnapshot base, SpeciesDef extra, LayoutDescriptor... history) {
        Map<String, DefinitionsSnapshot.SpeciesEntry> species = new LinkedHashMap<>(base.species());
        SpeciesInfo ci = carrot().info();
        SpeciesInfo info = new SpeciesInfo(Ids.parse(extra.id()), ci.archetype(), ci.block(), ci.color(),
                ci.lifeCycle(), ci.displayTraits(), ci.rareAllele(), ci.propagation());
        species.put(extra.id(), new DefinitionsSnapshot.SpeciesEntry(extra, info, Ids.mod("gametest/" + Ids.parse(extra.id()).getPath())));
        LayoutDictionary dict = new LayoutDictionary();
        for (LayoutDescriptor d : history) {
            dict.register(d);
        }
        for (LayoutDescriptor d : base.layouts().entries()) {
            dict.register(d);
        }
        for (DefinitionsSnapshot.SpeciesEntry e : species.values()) {
            dict.register(e.def().id(), GenomeLayout.of(e.def()));
        }
        return new DefinitionsSnapshot(species, base.cultivars(), base.stats(), dict);
    }
}
