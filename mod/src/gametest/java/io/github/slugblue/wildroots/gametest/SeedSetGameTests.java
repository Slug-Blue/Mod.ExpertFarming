package io.github.slugblue.wildroots.gametest;

import io.github.slugblue.wildroots.WildRoots;
import io.github.slugblue.wildroots.core.safety.LoadedLevelAccess;
import io.github.slugblue.wildroots.data.Definitions;
import io.github.slugblue.wildroots.genetics.api.Environment;
import io.github.slugblue.wildroots.genetics.api.Phenotype;
import io.github.slugblue.wildroots.genetics.model.Genome;
import io.github.slugblue.wildroots.genetics.phenotype.Vigor;
import io.github.slugblue.wildroots.item.LotFathersNbt;
import io.github.slugblue.wildroots.item.LotNbt;
import io.github.slugblue.wildroots.item.LotStacks;
import io.github.slugblue.wildroots.plant.FieldPlantBlock;
import io.github.slugblue.wildroots.plant.GrowthLogic;
import io.github.slugblue.wildroots.plant.PendingLot;
import io.github.slugblue.wildroots.plant.PlantBlockEntity;
import io.github.slugblue.wildroots.plant.PlantService;
import io.github.slugblue.wildroots.pollination.BloomTracker;
import io.github.slugblue.wildroots.pollination.PollinationService;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/** WR-141: seed set and the pending lot (docs/05-architecture.md §10.2; GDD §4.1, §14). */
@GameTestHolder(WildRoots.MOD_ID)
@PrefixGameTestTemplate(false)
public final class SeedSetGameTests {

    static final String FIELD = "gametest/lit_field_17x10x17";
    /**
     * Ticks before the test body. GameTests run underground (y = -60 of a
     * normal world), so the template carries a layer of light blocks; the
     * light engine needs a few ticks after placement, and a crop in the dark
     * pops on the first neighbour shape update (vanilla
     * {@code CropBlock#canSurvive}).
     */
    static final long SETUP = 40;

    private SeedSetGameTests() {
    }

    static PlantBlockEntity flowering(GameTestHelper helper, BlockPos rel, long key) {
        return PlantFixtures.plant(PlantFixtures.place(helper, rel, FieldPlantBlock.FLOWER_STAGE), key);
    }

    static PendingLot seedSet(GameTestHelper helper, BlockPos rel) {
        ServerLevel level = helper.getLevel();
        PlantBlockEntity be = (PlantBlockEntity) helper.getBlockEntity(rel);
        PollinationService.seedSet(level, helper.absolutePos(rel), be, be.live(level).orElseThrow());
        return be.data().pending();
    }

    static long fathers(PendingLot p) {
        return p.fathers().stream().filter(f -> !f.donor()).count();
    }

    /**
     * A 4×4 bed of flowering carrots (radius 2): every plant gets at least
     * one father; an isolated plant 10 blocks away gets none, selfs 100 %
     * and its selfed seedlings are ailing more often.
     */
    @GameTest(template = FIELD, setupTicks = SETUP)
    public static void bedHasFathersIsolatedPlantSelfs(GameTestHelper helper) {
        List<BlockPos> bed = new ArrayList<>();
        for (int x = 0; x < 4; x++) {
            for (int z = 0; z < 4; z++) {
                BlockPos rel = new BlockPos(1 + x, 2, 1 + z);
                flowering(helper, rel, 100 + x * 4 + z);
                bed.add(rel);
            }
        }
        BlockPos lone = new BlockPos(14, 2, 4);
        long motherKey = selfingMother();
        flowering(helper, lone, motherKey);
        for (BlockPos rel : bed) {
            helper.assertTrue(BloomTracker.contains(helper.getLevel(), helper.absolutePos(rel), PlantFixtures.SP),
                    "bed plant not indexed " + rel);
        }
        for (BlockPos rel : bed) {
            PendingLot p = seedSet(helper, rel);
            helper.assertTrue(p != null && fathers(p) >= 1, "bed plant " + rel + " has no father: " + p);
            helper.assertTrue(p.pOut() > 0, "bed plant " + rel + " p_out " + p.pOut());
        }
        PendingLot alone = seedSet(helper, lone);
        helper.assertTrue(alone != null && alone.fathers().isEmpty() && alone.pOut() == 0,
                "isolated plant: fathers " + (alone == null ? null : alone.fathers().size()) + ", p_out "
                        + (alone == null ? null : alone.pOut()));
        // selfed seedlings: at least 1 of 10 ailing (mother with one dd load locus and ≥ 3 heterozygous)
        ServerLevel level = helper.getLevel();
        PlantBlockEntity lb = (PlantBlockEntity) helper.getBlockEntity(lone);
        PlantBlockEntity.Live live = lb.live(level).orElseThrow();
        ItemStack lot = LotStacks.seeds(level, live, alone.fathers(), alone.pOut(), 0, () -> false, 10);
        LotNbt.Lot l = (LotNbt.Lot) LotStacks.read(lot);
        int ailing = 0;
        for (int i = 0; i < 10; i++) {
            BlockPos at = helper.absolutePos(new BlockPos(6 + i, 2, 10));
            Genome g = PlantService.seedling(level, l, 10 - i, at).orElseThrow().genome();
            Environment env = GrowthLogic.environment(level, at);
            Phenotype p = Definitions.current().genetics()
                    .phenotype(g, live.def(), env, PlantFixtures.noise(i)).orElseThrow();
            if (p.vigor() < GrowthLogic.AILING_BELOW) {
                ailing++;
            }
        }
        helper.assertTrue(ailing >= 1, "no ailing seedling among 10 selfed ones");
        helper.succeed();
    }

    /** A wild genome key whose genome has one dd load locus and at least three heterozygous ones. */
    static long selfingMother() {
        for (long key = 5000; key < 50_000; key++) {
            Genome g = PlantFixtures.genome(key);
            var load = g.layout().load();
            int dd = Vigor.homozygousDeleterious(g, load);
            int het = 0;
            for (int i = 0; i < load.loci(); i++) {
                if (g.allele(load, i, 0) != g.allele(load, i, 1)) {
                    het++;
                }
            }
            if (dd == 1 && het >= 3) {
                return key;
            }
        }
        throw new IllegalStateException("no selfing mother found");
    }

    /** The real path: growing from flowering to seeds runs the seed set with the neighbours. */
    @GameTest(template = FIELD, setupTicks = SETUP)
    public static void growthToSeedsRunsSeedSet(GameTestHelper helper) {
        BlockPos a = new BlockPos(5, 2, 5);
        flowering(helper, a, 201);
        flowering(helper, new BlockPos(6, 2, 5), 202);
        flowering(helper, new BlockPos(5, 2, 6), 203);
        PlantBlockEntity be = (PlantBlockEntity) helper.getBlockEntity(a);
        GrowthLogic.advance(helper.getLevel(), helper.absolutePos(a), helper.getBlockState(a), be,
                be.live(helper.getLevel()).orElseThrow(), FieldPlantBlock.SEED_STAGE);
        helper.assertTrue(PlantFixtures.stage(helper, a) == FieldPlantBlock.SEED_STAGE, "not at seeds");
        PendingLot p = be.data().pending();
        helper.assertTrue(p != null && fathers(p) >= 1, "no fathers after growth: " + p);
        helper.assertFalse(BloomTracker.contains(helper.getLevel(), helper.absolutePos(a), PlantFixtures.SP),
                "plant at seeds still in the index");
        helper.succeed();
    }

    /**
     * Candidates from an unloaded chunk are not read and the chunk is not
     * loaded: index entries around a position far away (never loaded) give
     * no candidate, and the loaded-chunk count does not change.
     */
    @GameTest(template = FIELD, setupTicks = SETUP)
    public static void unloadedNeighboursAreNotRead(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos far = new BlockPos(1_000_000 + helper.absolutePos(BlockPos.ZERO).getX(), 64, 1_000_007);
        helper.assertFalse(LoadedLevelAccess.isLoaded(level, far), "far chunk is loaded already");
        BlockPos[] stale = {far.east(), far.north(), far.offset(1, 0, 1)};
        for (BlockPos s : stale) {
            BloomTracker.set(level, s, PlantFixtures.SP, true);
        }
        int loadedBefore = level.getChunkSource().getLoadedChunksCount();
        try {
            List<PollinationService.Found> found = PollinationService.candidates(level, far,
                    PlantFixtures.carrot().def(), 1L);
            helper.assertTrue(found.isEmpty(), "candidates read from an unloaded chunk: " + found.size());
            helper.assertFalse(LoadedLevelAccess.isLoaded(level, far), "the query loaded the far chunk");
            helper.assertTrue(level.getChunkSource().getLoadedChunksCount() == loadedBefore,
                    "loaded chunks " + loadedBefore + " -> " + level.getChunkSource().getLoadedChunksCount());
        } finally {
            for (BlockPos s : stale) {
                BloomTracker.set(level, s, PlantFixtures.SP, false);
            }
        }
        helper.succeed();
    }

    /** A father broken after the seed set stays in the lot: it was copied by value. */
    @GameTest(template = FIELD, setupTicks = SETUP)
    public static void brokenFatherStaysInTheLot(GameTestHelper helper) {
        BlockPos mother = new BlockPos(8, 2, 8);
        BlockPos father = new BlockPos(9, 2, 8);
        flowering(helper, mother, 301);
        PlantBlockEntity fb = flowering(helper, father, 302);
        long[] fatherBits = fb.live(helper.getLevel()).orElseThrow().genome().bits();
        PendingLot p = seedSet(helper, mother);
        helper.assertTrue(p != null && fathers(p) == 1, "expected one father: " + p);
        helper.getLevel().destroyBlock(helper.absolutePos(father), false);
        PlantBlockEntity mb = PlantFixtures.saveLoad((PlantBlockEntity) helper.getBlockEntity(mother));
        PendingLot after = mb.data().pending();
        LotFathersNbt.Father f = after.fathers().get(0);
        helper.assertTrue(Arrays.equals(f.genome().a(), fatherBits), "father genome changed after the break");
        helper.succeed();
    }
}
