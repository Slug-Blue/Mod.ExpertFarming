package io.github.slugblue.wildroots.gametest;

import io.github.slugblue.wildroots.WildRoots;
import io.github.slugblue.wildroots.core.registry.ModBlocks;
import io.github.slugblue.wildroots.core.registry.ModFeatures;
import io.github.slugblue.wildroots.plant.FieldPlantBlock;
import io.github.slugblue.wildroots.plant.Passport;
import io.github.slugblue.wildroots.plant.PlantBlockEntity;
import io.github.slugblue.wildroots.worldgen.ChunkBandPlacement;
import io.github.slugblue.wildroots.worldgen.SpeciesClimatePlacement;
import io.github.slugblue.wildroots.worldgen.WildPatchConfig;
import io.github.slugblue.wildroots.worldgen.WildPatchFeature;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/** WR-150: the wild patch feature (worldgen §3, §3.3, §12.2). */
@GameTestHolder(WildRoots.MOD_ID)
@PrefixGameTestTemplate(false)
public final class WildPatchGameTests {

    static final String FLOOR = SmokeGameTests.FLOOR_3X3X3;
    static final ResourceLocation CARROT_PATCH = new ResourceLocation("wildroots", "wild_patch/wild_carrot");

    /** Category falls back to meadow when the test biome is in none of the carrot categories. */
    static final WildPatchFeature.Options TEST = new WildPatchFeature.Options(false, Optional.empty(),
            Optional.empty(), "meadow", 0);

    private WildPatchGameTests() {
    }

    /** The configured feature of the carrot line pack. */
    static WildPatchConfig carrotConfig(ServerLevel level) {
        ConfiguredFeature<?, ?> cf = level.registryAccess().registryOrThrow(Registries.CONFIGURED_FEATURE)
                .get(CARROT_PATCH);
        if (cf == null || !(cf.config() instanceof WildPatchConfig c)) {
            throw new IllegalStateException("no configured feature " + CARROT_PATCH + ": " + cf);
        }
        return c;
    }

    static WildPatchFeature.Result place(ServerLevel level, BlockPos origin, WildPatchConfig c,
                                         WildPatchFeature.Options o) {
        return ModFeatures.WILD_PATCH.get().placePatch(level, level.getChunkSource().getGenerator(), origin, c, o);
    }

    /**
     * A generated patch: 5–14 static plants ({@code active = false}, no
     * random ticks) whose block entities hold only the passport (origin,
     * category, not rich, not starter); no genome until the first touch,
     * which realises it; the same origin gives the same patch.
     */
    @GameTest(template = FLOOR, batch = "wr150_sky_patch")
    public static void patchWritesPassportWithoutGenome(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        SkyPlatform sky = SkyPlatform.build(helper, 8);
        try {
            WildPatchConfig c = carrotConfig(level);
            BlockPos origin = sky.surface();
            WildPatchFeature.Result r = place(level, origin, c, TEST);
            helper.assertTrue(r.placed(), "patch not placed on a flat grass platform");
            helper.assertTrue(r.plants().size() >= 5 && r.plants().size() <= 14, "plants " + r.plants().size());
            int flowering = 0;
            for (BlockPos p : r.plants()) {
                BlockState st = level.getBlockState(p);
                helper.assertTrue(st.getBlock() instanceof FieldPlantBlock, "no plant at " + p);
                helper.assertFalse(st.getValue(FieldPlantBlock.ACTIVE), "wild plant is active at " + p);
                helper.assertFalse(st.isRandomlyTicking(), "wild plant ticks at " + p);
                int stage = FieldPlantBlock.stage(st);
                helper.assertTrue(stage >= FieldPlantBlock.HARVEST_STAGE, "stage " + stage);
                flowering += stage == FieldPlantBlock.FLOWER_STAGE ? 1 : 0;
                PlantBlockEntity be = (PlantBlockEntity) level.getBlockEntity(p);
                Passport pp = be.data().passport();
                helper.assertTrue(pp.origin() == Passport.Origin.WILD && pp.patchOrigin() == origin.asLong()
                        && !pp.category().isEmpty() && !pp.rich() && !pp.starter(), "passport " + pp);
                helper.assertTrue(be.data().genome() == null && !be.genomeDecoded(), "genome made at generation");
                PlantBlockEntity reloaded = PlantFixtures.saveLoad(be);
                helper.assertTrue(reloaded.data().genome() == null && reloaded.data().passport().equals(pp),
                        "passport lost in save/load");
            }
            helper.assertTrue(flowering >= 1, "no flowering plant in the patch");
            PlantBlockEntity first = (PlantBlockEntity) level.getBlockEntity(r.plants().get(0));
            helper.assertTrue(first.live(level).isPresent() && first.data().genome() != null,
                    "first touch did not realise the genome");
            // determinism: the same origin gives the same patch
            List<BlockPos> before = new ArrayList<>(r.plants());
            List<Integer> stages = before.stream().map(p -> FieldPlantBlock.stage(level.getBlockState(p))).toList();
            sky.fill(Blocks.GRASS_BLOCK.defaultBlockState());
            WildPatchFeature.Result again = place(level, origin, c, TEST);
            helper.assertTrue(again.plants().equals(before), "same origin, other positions");
            helper.assertTrue(again.plants().stream().map(p -> FieldPlantBlock.stage(level.getBlockState(p)))
                    .toList().equals(stages), "same origin, other stages");
        } finally {
            sky.clear();
        }
        helper.succeed();
    }

    /** Fewer valid spots than {@code min_plants}: nothing at all is placed (worldgen §3.2 item 4). */
    @GameTest(template = FLOOR, batch = "wr150_sky_min")
    public static void tooFewSpotsPlacesNothing(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        SkyPlatform sky = SkyPlatform.build(helper, 8);
        try {
            sky.fill(Blocks.STONE.defaultBlockState());
            BlockPos origin = sky.surface();
            level.setBlock(origin.below(), Blocks.GRASS_BLOCK.defaultBlockState(), 2);
            level.setBlock(origin.below().east(), Blocks.GRASS_BLOCK.defaultBlockState(), 2);
            WildPatchFeature.Result r = place(level, origin, carrotConfig(level), TEST);
            helper.assertFalse(r.placed(), "a patch of fewer than 5 plants was placed");
            helper.assertTrue(level.getBlockState(origin).isAir() && level.getBlockState(origin.east()).isAir(),
                    "plants left behind by a rejected patch");
        } finally {
            sky.clear();
        }
        helper.succeed();
    }

    /** Wild plants stand on their wild soil (grass), not only on farmland. */
    @GameTest(template = FLOOR)
    public static void wildSoilTagOfTheCarrot(GameTestHelper helper) {
        FieldPlantBlock block = ModBlocks.WILD_CARROT.get();
        helper.assertTrue(block.wildSoil().location().equals(new ResourceLocation("wildroots", "wild_soil/wild_carrot")),
                "soil tag id " + block.wildSoil());
        helper.assertTrue(Blocks.GRASS_BLOCK.defaultBlockState().is(block.wildSoil()), "grass not in the soil tag");
        helper.assertTrue(Blocks.PODZOL.defaultBlockState().is(block.wildSoil()), "podzol not in the soil tag");
        helper.assertFalse(Blocks.STONE.defaultBlockState().is(block.wildSoil()), "stone in the soil tag");
        helper.succeed();
    }

    /** The carrot line pack ships the placed feature with the chunk band and the climate filter. */
    @GameTest(template = FLOOR)
    public static void carrotPlacedFeatureLoaded(GameTestHelper helper) {
        PlacedFeature placed = helper.getLevel().registryAccess().registryOrThrow(Registries.PLACED_FEATURE)
                .get(CARROT_PATCH);
        helper.assertTrue(placed != null, "no placed feature " + CARROT_PATCH);
        boolean band = placed.placement().stream().anyMatch(m -> m instanceof ChunkBandPlacement b
                && b.from() == 0.0 && Math.abs(b.to() - 0.036) < 1e-9);
        boolean climate = placed.placement().stream().anyMatch(m -> m instanceof SpeciesClimatePlacement s
                && s.species().equals(PlantFixtures.SP));
        helper.assertTrue(band && climate, "placement chain " + placed.placement());
        helper.succeed();
    }
}
