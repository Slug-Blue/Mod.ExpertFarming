package io.github.slugblue.wildroots.gametest;

import io.github.slugblue.wildroots.WildRoots;
import io.github.slugblue.wildroots.data.Definitions;
import io.github.slugblue.wildroots.genetics.api.SpeciesDef;
import io.github.slugblue.wildroots.plant.PlantBlockEntity;
import io.github.slugblue.wildroots.pollination.BeePollen;
import io.github.slugblue.wildroots.pollination.BloomIndex;
import io.github.slugblue.wildroots.pollination.BloomTracker;
import io.github.slugblue.wildroots.pollination.PollinationService;
import io.github.slugblue.wildroots.worldgen.ClimateWindow;
import io.github.slugblue.wildroots.worldgen.SpeciesClimatePlacement;
import io.github.slugblue.wildroots.worldgen.WildPatchConfig;
import io.github.slugblue.wildroots.worldgen.WildPatchFeature;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Bee;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Batch 4 test pass (phase 5): boundary and adversarial cases of WR-140…142
 * and WR-150…154 that the feature tests do not reach.
 */
@GameTestHolder(WildRoots.MOD_ID)
@PrefixGameTestTemplate(false)
public final class Batch4BoundaryGameTests {

    static final String FLOOR = SmokeGameTests.FLOOR_3X3X3;
    static final String CARROT = "wildroots:wild_carrot";
    static final Logger LOG = LoggerFactory.getLogger(Batch4BoundaryGameTests.class);

    private Batch4BoundaryGameTests() {
    }

    static SpeciesDef carrot() {
        return Definitions.current().species(CARROT).orElseThrow().def();
    }

    // ------------------------------------------------------------------ WR-140

    /** The bloom index is per level: an entry in the Nether is not seen from the overworld and goes with it. */
    @GameTest(template = FLOOR)
    public static void bloomIndexIsPerDimension(GameTestHelper helper) {
        ServerLevel overworld = helper.getLevel();
        ServerLevel nether = overworld.getServer().getLevel(Level.NETHER);
        helper.assertTrue(nether != null, "no nether");
        BlockPos p = new BlockPos(123_456, 70, -654_321);
        try {
            BloomTracker.set(nether, p, CARROT, true);
            helper.assertTrue(BloomTracker.contains(nether, p, CARROT), "not in the nether index");
            helper.assertFalse(BloomTracker.contains(overworld, p, CARROT), "seen from the overworld");
            helper.assertTrue(BloomIndex.peek(nether) != BloomIndex.peek(overworld), "one index for two levels");
            BloomTracker.set(overworld, p, CARROT, false);
            helper.assertTrue(BloomTracker.contains(nether, p, CARROT), "an overworld removal reached the nether");
        } finally {
            BloomTracker.set(nether, p, CARROT, false);
        }
        helper.assertFalse(BloomTracker.contains(nether, p, CARROT), "not removed");
        helper.succeed();
    }

    // ------------------------------------------------------------------ WR-142

    /**
     * Donors at 4 and 5 through real bee visits ({@code PollinationService.beeVisit}):
     * four bees' fresh pollen from four other plants gives four donors; the
     * fifth evicts the oldest; pollen at the freshness edge (1 visit, 1200
     * ticks) still counts, one step past it does not.
     */
    @GameTest(template = BeeGameTests.FIELD, setupTicks = BeeGameTests.SETUP)
    public static void beeDonorsCapAtFourViaVisits(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos rel = new BlockPos(3, 2, 3);
        PlantBlockEntity be = SeedSetGameTests.flowering(helper, rel, 471);
        BlockPos at = helper.absolutePos(rel);
        Bee bee = helper.spawn(EntityType.BEE, new BlockPos(4, 4, 4));
        bee.setNoAi(true);
        BeePollen p = BeeGameTests.pollen(bee);
        var gs = Definitions.current().genetics();
        for (int i = 0; i < 5; i++) {
            p.collect(PlantFixtures.SP, at.asLong() + 1 + i, gs.encode(PlantFixtures.genome(480 + i)).toFather());
            p.setCounters(i == 4 ? BeePollen.MAX_VISITS - 1 : 0, i == 4 ? BeePollen.MAX_AGE_OUTSIDE : 0);
            helper.assertTrue(PollinationService.beeVisit(level, bee, at) == PollinationService.Visit.DONOR,
                    "visit " + i);
            helper.assertTrue(BeeGameTests.donors(be) == Math.min(4, i + 1), "after " + (i + 1) + ": "
                    + BeeGameTests.donors(be));
        }
        long[] first = PlantFixtures.saveLoad(be).data().pending().fathers().stream()
                .filter(f -> f.donor()).findFirst().orElseThrow().genome().a();
        helper.assertTrue(java.util.Arrays.equals(first, gs.encode(PlantFixtures.genome(481)).a()),
                "the oldest donor was not evicted first");
        // one tick past the edge: no new donor
        p.collect(PlantFixtures.SP, at.asLong() + 99, gs.encode(PlantFixtures.genome(499)).toFather());
        p.setCounters(0, BeePollen.MAX_AGE_OUTSIDE + 1);
        helper.assertTrue(PollinationService.beeVisit(level, bee, at) == PollinationService.Visit.VISITED,
                "1201 ticks");
        helper.assertTrue(BeeGameTests.donors(be) == 4, "donors " + BeeGameTests.donors(be));
        bee.discard();
        helper.succeed();
    }

    // ------------------------------------------------------------------ WR-150

    static Holder<Biome> biome(ServerLevel level, ResourceKey<Biome> key) {
        return level.registryAccess().registryOrThrow(Registries.BIOME).getHolderOrThrow(key);
    }

    /**
     * S11 edges: the temperature carries noise above Y 80 (not at 80) and in
     * frozen biomes at any height; there the window edges get ±0.01.
     */
    @GameTest(template = FLOOR)
    public static void climateNoiseAboveY80AndInFrozenBiomes(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Holder<Biome> plains = biome(level, Biomes.PLAINS);
        Holder<Biome> frozen = biome(level, Biomes.FROZEN_OCEAN);
        helper.assertFalse(SpeciesClimatePlacement.noisy(plains, new BlockPos(0, 80, 0)), "plains at 80");
        helper.assertTrue(SpeciesClimatePlacement.noisy(plains, new BlockPos(0, 81, 0)), "plains at 81");
        helper.assertTrue(SpeciesClimatePlacement.noisy(frozen, new BlockPos(0, 40, 0)), "frozen ocean at 40");
        helper.assertFalse(SpeciesClimatePlacement.noisy(biome(level, Biomes.SNOWY_PLAINS), new BlockPos(0, 64, 0)),
                "snowy plains is cold, not FROZEN-modified");
        SpeciesDef def = carrot();
        double edge = def.tempMin() - ClimateWindow.EDGE_SLACK / 2;
        helper.assertFalse(ClimateWindow.fits(edge, def.tempMin(), def.tempMax(), 0, false), "quiet edge");
        helper.assertTrue(ClimateWindow.fits(edge, def.tempMin(), def.tempMax(), 0, true), "noisy edge");
        helper.succeed();
    }

    /**
     * Patches at the world limits place nothing and never throw: the origin
     * above the build limit, over the void (no ground), over water and over
     * lava.
     */
    @GameTest(template = FLOOR, batch = "b4_sky_limits")
    public static void patchAtWorldLimitsPlacesNothing(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        WildPatchConfig c = WildPatchGameTests.carrotConfig(level).withBeeNest(java.util.Optional.empty());
        BlockPos column = helper.absolutePos(new BlockPos(8, 0, 8));
        WildPatchFeature.Result top = WildPatchGameTests.place(level, column.atY(level.getMaxBuildHeight()), c,
                WildPatchGameTests.TEST);
        helper.assertFalse(top.placed(), "placed above the build limit");
        WildPatchFeature.Result high = WildPatchGameTests.place(level, column.atY(level.getMaxBuildHeight() - 1), c,
                WildPatchGameTests.TEST);
        helper.assertFalse(high.placed(), "placed over the void at the top");
        SkyPlatform sky = SkyPlatform.build(helper, 6);
        try {
            for (BlockState floor : new BlockState[] {Blocks.WATER.defaultBlockState(),
                Blocks.LAVA.defaultBlockState(), Blocks.AIR.defaultBlockState()}) {
                sky.fill(floor);
                WildPatchFeature.Result r = WildPatchGameTests.place(level, sky.surface(), c, WildPatchGameTests.TEST);
                helper.assertFalse(r.placed(), "placed over " + floor);
                helper.assertTrue(level.getBlockState(sky.surface()).isAir(), "wrote over " + floor);
            }
        } finally {
            sky.fill(Blocks.AIR.defaultBlockState());
            sky.clear();
        }
        helper.succeed();
    }
}
