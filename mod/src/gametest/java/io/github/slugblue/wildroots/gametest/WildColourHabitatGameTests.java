package io.github.slugblue.wildroots.gametest;

import io.github.slugblue.wildroots.WildRoots;
import io.github.slugblue.wildroots.genetics.api.Classification;
import io.github.slugblue.wildroots.genetics.api.PatchPassport;
import io.github.slugblue.wildroots.genetics.model.TraitId;
import io.github.slugblue.wildroots.plant.Passport;
import io.github.slugblue.wildroots.plant.PlantBlockEntity;
import io.github.slugblue.wildroots.worldgen.SpeciesClimatePlacement;
import io.github.slugblue.wildroots.worldgen.WildColourHabitats;
import io.github.slugblue.wildroots.worldgen.WildPatchFeature;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.levelgen.placement.PlacementContext;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/** Owner's exact habitat lists, rare wild colours, lazy realization and inherited genomes. */
@GameTestHolder(WildRoots.MOD_ID)
@PrefixGameTestTemplate(false)
public final class WildColourHabitatGameTests {
    static final TraitId P = TraitId.of("color_p");
    static final TraitId Y1 = TraitId.of("color_y1");
    static final Set<ResourceKey<Biome>> PURPLE = Set.of(Biomes.SAVANNA_PLATEAU, Biomes.WINDSWEPT_SAVANNA,
            Biomes.SAVANNA, Biomes.JUNGLE, Biomes.BAMBOO_JUNGLE, Biomes.SPARSE_JUNGLE);
    static final Set<ResourceKey<Biome>> YELLOW = Set.of(Biomes.SAVANNA_PLATEAU, Biomes.WINDSWEPT_SAVANNA,
            Biomes.JUNGLE, Biomes.BAMBOO_JUNGLE, Biomes.SPARSE_JUNGLE, Biomes.PLAINS, Biomes.SUNFLOWER_PLAINS,
            Biomes.BEACH, Biomes.FOREST, Biomes.FLOWER_FOREST, Biomes.DARK_FOREST);

    private WildColourHabitatGameTests() { }

    @GameTest(template = SmokeGameTests.FLOOR_3X3X3, batch = "colour_habitat_rules", timeoutTicks = 600)
    public static void exactBiomeListsAndRareWildColours(GameTestHelper helper) {
        var registry = helper.getLevel().registryAccess().registryOrThrow(Registries.BIOME);
        var entry = PlantFixtures.carrot();
        var genetics = io.github.slugblue.wildroots.data.Definitions.current().genetics();
        for (var biome : registry.holders().toList()) {
            var id = biome.key();
            helper.assertTrue(biome.is(WildColourHabitats.PURPLE) == PURPLE.contains(id), "purple habitat " + id);
            helper.assertTrue(biome.is(WildColourHabitats.YELLOW) == YELLOW.contains(id), "yellow habitat " + id);
            int purple = 0;
            int yellow = 0;
            for (int i = 0; i < 512; i++) {
                var pp = new PatchPassport(PlantFixtures.SP, "meadow", false, 1234, false, 42,
                        WildColourHabitats.frequencies(entry.def(), biome));
                var g = genetics.wildPlant(entry.def(), pp, 10000L + i).orElseThrow();
                int p = g.allele(P, 0, 0) + g.allele(P, 0, 1);
                int y = g.allele(Y1, 0, 0) + g.allele(Y1, 0, 1);
                helper.assertTrue(PURPLE.contains(id) || p == 0, "hidden purple allele outside habitat " + id);
                helper.assertTrue(YELLOW.contains(id) || y == 0, "hidden yellow allele outside habitat " + id);
                purple += p > 0 ? 1 : 0;
                yellow += p == 0 && y == 2 ? 1 : 0;
            }
            if (PURPLE.contains(id)) {
                helper.assertTrue(purple >= 10 && purple <= 100, "purple is absent or common: " + purple);
            }
            if (YELLOW.contains(id)) {
                helper.assertTrue(yellow >= 10 && yellow <= 125, "yellow is absent or common: " + yellow);
            }
        }
        helper.succeed();
    }

    @GameTest(template = SmokeGameTests.FLOOR_3X3X3, batch = "colour_habitat_lazy", timeoutTicks = 600)
    public static void lazyWildPlantsUseTheirBiomeButStoredAndPlayerGenomesStay(GameTestHelper helper) {
        BlockPos at = helper.absolutePos(PlantFixtures.PLANT);
        PlantBlockEntity be = PlantFixtures.place(helper, PlantFixtures.PLANT, 3);
        for (var key : List.of(Biomes.PLAINS, Biomes.SAVANNA, Biomes.BIRCH_FOREST)) {
            Runnable undo = ClimateKit.setBiome(helper.getLevel(), at, 12, key);
            try {
                int purple = 0;
                int yellow = 0;
                for (int i = 0; i < 256; i++) {
                    be.initWild(PlantFixtures.SP, Passport.wild(at.offset(i, 0, 0).asLong(), "meadow", false, false));
                    var g = be.live(helper.getLevel()).orElseThrow().genome();
                    int p = g.allele(P, 0, 0) + g.allele(P, 0, 1);
                    int y = g.allele(Y1, 0, 0) + g.allele(Y1, 0, 1);
                    helper.assertTrue(key == Biomes.SAVANNA || p == 0, "lazy purple leak " + key);
                    helper.assertTrue(key == Biomes.PLAINS || y == 0, "lazy yellow leak " + key);
                    purple += p > 0 ? 1 : 0;
                    yellow += y == 2 ? 1 : 0;
                }
                helper.assertTrue(key != Biomes.SAVANNA || purple > 0, "no purple source in savanna");
                helper.assertTrue(key != Biomes.PLAINS || yellow > 0, "no yellow source in plains");
                var inherited = ClimateKit.genome(891, ClimateKit.PURPLE, 0, 0);
                for (Passport origin : List.of(Passport.PLAYER, Passport.wild(at.asLong(), "meadow", false, false))) {
                    be.init(io.github.slugblue.wildroots.data.Definitions.current().genetics(), new PlantBlockEntity.Init(
                            PlantFixtures.SP, inherited, ClimateKit.noNoise(inherited), origin, null,
                            Classification.WILD, null, 0, 0, 0));
                    helper.assertTrue(be.live(helper.getLevel()).orElseThrow().genome().equals(inherited),
                            "rewrote an existing genome in " + key);
                }
            } finally {
                undo.run();
            }
        }
        helper.succeed();
    }

    @GameTest(template = SmokeGameTests.FLOOR_3X3X3, batch = "colour_habitat_placement", timeoutTicks = 600)
    public static void warmHabitatBypassesOnlyThePlacementWindowAndHasNaturalPatches(GameTestHelper helper) {
        var level = helper.getLevel();
        var placed = level.registryAccess().registryOrThrow(Registries.PLACED_FEATURE).get(WildPatchGameTests.CARROT_PATCH);
        SpeciesClimatePlacement filter = placed.placement().stream().filter(p -> p instanceof SpeciesClimatePlacement)
                .map(p -> (SpeciesClimatePlacement) p).findFirst().orElseThrow();
        var context = new PlacementContext(level, level.getChunkSource().getGenerator(), Optional.of(placed));
        BlockPos at = helper.absolutePos(PlantFixtures.PLANT);
        for (var key : List.of(Biomes.SAVANNA, Biomes.DESERT)) {
            Runnable undo = ClimateKit.setBiome(level, at, 12, key);
            try {
                helper.assertTrue(filter.getPositions(context, net.minecraft.util.RandomSource.create(1), at)
                        .findAny().isPresent() == (key == Biomes.SAVANNA), "placement window " + key);
            } finally {
                undo.run();
            }
        }
        helper.assertTrue(PlantFixtures.carrot().def().tempMax() == 1.0, "changed the growth temperature window");
        SkyPlatform sky = SkyPlatform.build(helper, 12);
        Runnable undo = ClimateKit.setBiome(level, sky.surface(), 16, Biomes.SAVANNA);
        try {
            var result = WildPatchGameTests.place(level, sky.surface(),
                    WildPatchGameTests.carrotConfig(level).withBeeNest(Optional.empty()), WildPatchFeature.Options.NATURAL);
            helper.assertTrue(result.placed(), "no natural patch in the newly enabled savanna");
        } finally {
            undo.run();
            sky.clear();
        }
        helper.succeed();
    }
}
