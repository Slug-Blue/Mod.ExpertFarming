package io.github.slugblue.wildroots.gametest;

import io.github.slugblue.wildroots.core.config.ServerConfig;
import io.github.slugblue.wildroots.core.registry.ModBlocks;
import io.github.slugblue.wildroots.data.Definitions;
import io.github.slugblue.wildroots.genetics.api.Classification;
import io.github.slugblue.wildroots.genetics.model.Genome;
import io.github.slugblue.wildroots.genetics.model.GenomeLayout;
import io.github.slugblue.wildroots.genetics.model.Locus;
import io.github.slugblue.wildroots.genetics.model.TraitId;
import io.github.slugblue.wildroots.genetics.phenotype.NoiseVector;
import io.github.slugblue.wildroots.plant.FieldPlantBlock;
import io.github.slugblue.wildroots.plant.Passport;
import io.github.slugblue.wildroots.plant.PlantBlockEntity;
import io.github.slugblue.wildroots.plant.PlantService;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FarmBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.PalettedContainer;

/**
 * Shared set-up of the climate GameTests (WR-711, WR-712, WR-714): plants with a known tolerance and colour in a
 * biome of the test's choosing. A GameTest world has a random seed, so the biome (and with it the temperature) at
 * the test area is not known: {@link #setBiome} paints the biome cells around a cell for the length of a test, and
 * {@link #withWilting} switches the server flag. The GameTest server starts with {@code climate.wilting} off
 * ({@link ClimateGameTestDefaults}), so no other test depends on the biome of its area; a test of wilting turns it
 * on for the span of one synchronous body.
 */
final class ClimateKit {

    /** Colour loci: purple {@code P_}, white (no {@code y1y1}). */
    static final int[] WHITE = {0, 0, 0, 0};
    static final int[] PURPLE = {1, 0, 0, 0};

    private ClimateKit() {
    }

    /**
     * A carrot genome of {@code key} with the four colour loci ({@code copies} of the domestic allele of
     * {@code color_p, color_y1, color_y2, color_r}) and every locus of the heat tolerance set to allele
     * {@code heat} and of the cold tolerance to {@code cold} (0, 1 or 2: P about 0, 0.5, 1; the budget and the
     * trade-offs may move a middle value, the callers check the stress they get).
     */
    static Genome genome(long key, int[] colour, int heat, int cold) {
        Genome g = PlantFixtures.genome(key);
        GenomeLayout layout = g.layout();
        Genome.Builder b = g.toBuilder();
        String[] traits = {"color_p", "color_y1", "color_y2", "color_r"};
        for (int i = 0; i < traits.length; i++) {
            Locus s = layout.section(TraitId.of(traits[i]));
            b.set(s, 0, 0, colour[i] >= 1 ? 1 : 0).set(s, 0, 1, colour[i] >= 2 ? 1 : 0);
        }
        tolerance(b, layout, "heat_tolerance", heat);
        tolerance(b, layout, "cold_tolerance", cold);
        return b.build();
    }

    private static void tolerance(Genome.Builder b, GenomeLayout layout, String trait, int allele) {
        Locus s = layout.section(TraitId.of(trait));
        for (int l = 0; l < s.loci(); l++) {
            b.set(s, l, 0, allele).set(s, l, 1, allele);
        }
    }

    /** No planting noise: the tolerance is exactly the one of the genome. */
    static NoiseVector noNoise(Genome g) {
        return NoiseVector.zero(g.layout().quantitative().size());
    }

    /** Farmland of the given moisture under {@code abs}. */
    static void farmland(ServerLevel level, BlockPos abs, int moisture) {
        level.setBlock(abs.below(), Blocks.FARMLAND.defaultBlockState().setValue(FarmBlock.MOISTURE, moisture), 2);
    }

    /**
     * Plants a carrot of {@code genome} at {@code abs} (farmland of {@code moisture} below, which must be lit) with
     * {@code passport} and {@code flags}, at {@code stage}, through {@link PlantService#place}.
     */
    static PlantBlockEntity plant(ServerLevel level, BlockPos abs, int stage, Genome genome, Passport passport,
                                  int flags, int moisture) {
        farmland(level, abs, moisture);
        boolean ok = PlantService.place(level, abs, ModBlocks.WILD_CARROT.get(), new PlantBlockEntity.Init(
                PlantFixtures.SP, genome, noNoise(genome), passport, new UUID(5, 6), Classification.WILD, null, 0, 0,
                flags), stage);
        if (!ok || !(level.getBlockEntity(abs) instanceof PlantBlockEntity be)) {
            throw new IllegalStateException("could not plant at " + abs);
        }
        return be;
    }

    /** {@code n} random ticks of the plant block at {@code abs}; growth is denied by the caller's probe. */
    static void tick(ServerLevel level, BlockPos abs, RandomSource random, int n) {
        for (int i = 0; i < n; i++) {
            level.getBlockState(abs).randomTick(level, abs, random);
        }
    }

    static int strain(ServerLevel level, BlockPos abs) {
        BlockState s = level.getBlockState(abs);
        return s.getBlock() instanceof FieldPlantBlock ? FieldPlantBlock.strain(s) : -1;
    }

    /** What breaking the plant by {@code player} drops. */
    static List<ItemStack> drops(ServerLevel level, BlockPos abs, Player player) {
        return Block.getDrops(level.getBlockState(abs), level, abs, level.getBlockEntity(abs), player, ItemStack.EMPTY);
    }

    // ------------------------------------------------------------------ biome

    /**
     * Paints the biome cells within {@code radius} blocks (horizontally) and 12 blocks (vertically) of {@code at}
     * with {@code biome}, in loaded chunks only (the biome lookup blends the neighbouring cells, so one cell is not
     * enough). Returns the undo: the old holders, cell by cell.
     */
    static Runnable setBiome(ServerLevel level, BlockPos at, int radius, ResourceKey<Biome> biome) {
        Holder<Biome> holder = level.registryAccess().registryOrThrow(Registries.BIOME).getHolderOrThrow(biome);
        List<Runnable> undo = new ArrayList<>();
        int minY = Math.max(level.getMinBuildHeight(), at.getY() - 12);
        int maxY = Math.min(level.getMaxBuildHeight() - 1, at.getY() + 12);
        for (int x = (at.getX() - radius) & ~3; x <= at.getX() + radius; x += 4) {
            for (int z = (at.getZ() - radius) & ~3; z <= at.getZ() + radius; z += 4) {
                LevelChunk chunk = level.getChunkSource().getChunkNow(x >> 4, z >> 4);
                if (chunk == null) {
                    continue;
                }
                for (int y = minY & ~3; y <= maxY; y += 4) {
                    LevelChunkSection section = chunk.getSection(chunk.getSectionIndex(y));
                    int qx = (x & 15) >> 2;
                    int qy = (y & 15) >> 2;
                    int qz = (z & 15) >> 2;
                    @SuppressWarnings("unchecked")
                    PalettedContainer<Holder<Biome>> cells = (PalettedContainer<Holder<Biome>>) section.getBiomes();
                    Holder<Biome> old = cells.getAndSetUnchecked(qx, qy, qz, holder);
                    undo.add(() -> cells.getAndSetUnchecked(qx, qy, qz, old));
                }
                chunk.setUnsaved(true);
            }
        }
        return () -> undo.forEach(Runnable::run);
    }

    // ------------------------------------------------------------------ config

    /** Runs {@code body} with {@code climate.wilting} as given, then back to the GameTest default (off). */
    static void withWilting(boolean on, Runnable body) {
        ServerConfig.overrideForTests(ServerConfig.CLIMATE_WILTING, on);
        try {
            body.run();
        } finally {
            ServerConfig.overrideForTests(ServerConfig.CLIMATE_WILTING, ClimateGameTestDefaults.DEFAULT);
        }
    }
}
