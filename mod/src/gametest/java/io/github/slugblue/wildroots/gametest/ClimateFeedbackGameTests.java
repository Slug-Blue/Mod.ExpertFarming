package io.github.slugblue.wildroots.gametest;

import io.github.slugblue.wildroots.WildRoots;
import io.github.slugblue.wildroots.core.registry.ModBlocks;
import io.github.slugblue.wildroots.genetics.api.Classification;
import io.github.slugblue.wildroots.journal.Journals;
import io.github.slugblue.wildroots.lens.Lens;
import io.github.slugblue.wildroots.plant.Passport;
import io.github.slugblue.wildroots.plant.PlantBlockEntity;
import io.github.slugblue.wildroots.plant.PlantService;
import io.github.slugblue.wildroots.plant.PlantState;
import io.github.slugblue.wildroots.plant.Wilting;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/** WR-713: real climate ticks, qualitative lens reading and idempotent owner hints. */
@GameTestHolder(WildRoots.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ClimateFeedbackGameTests {
    private ClimateFeedbackGameTests() { }

    static void feedback(GameTestHelper helper, ResourceKey<Biome> biome, String direction) {
        ClimateStressGameTests.underground(helper, biome, List.of(ClimateStressGameTests.A), level -> {
            BlockPos at = helper.absolutePos(ClimateStressGameTests.A);
            ServerPlayer player = JournalGameTests.survival(helper, "wr713_" + direction);
            JournalHintGameTests.Rec rec = new JournalHintGameTests.Rec();
            var old = Journals.setExtraOnline(id -> id.equals(player.getUUID()) ? player : null);
            try {
                var genome = ClimateKit.genome(713, ClimateKit.WHITE, 0, 0);
                ClimateKit.farmland(level, at, 3);
                helper.assertTrue(PlantService.place(level, at, ModBlocks.WILD_CARROT.get(),
                        new PlantBlockEntity.Init(PlantFixtures.SP, genome, ClimateKit.noNoise(genome), Passport.PLAYER,
                                player.getUUID(), Classification.WILD, null, 0, 0, 0), 1), "plant failed");
                PlantBlockEntity be = (PlantBlockEntity) level.getBlockEntity(at);
                helper.assertTrue(Wilting.plantingWarning(level, at, be.live(level).orElseThrow().def())
                        .orElse("").endsWith(direction), "no species warning");
                ClimateStressGameTests.denyingGrowth(List.of(at), () -> {
                    ClimateKit.tick(level, at, RandomSource.create(713), 1);
                    helper.assertTrue(JournalGameTests.journal(player).has("hint.climate"), "no stress hint");
                    helper.assertTrue(!JournalGameTests.journal(player).has("hint." + direction + "_wilt"),
                            "wilt hint before wilting");
                    var packet = Lens.readPlant(level, at, player).orElseThrow().packet().orElseThrow();
                    helper.assertTrue(packet.climate().lines().stream().anyMatch(l -> l.key()
                            .equals("wildroots.lens.climate." + direction + ".3")), "lens omitted climate");
                    helper.assertTrue(packet.size() <= 1024, "packet over budget");
                    ClimateKit.tick(level, at, RandomSource.create(714), 8);
                    helper.assertTrue(Wilting.isWilted(level.getBlockState(at)), "plant did not wilt");
                    for (int i = 0; i < 4; i++) {
                        Lens.readPlant(level, at, player);
                    }
                    for (String id : List.of("hint.climate", "hint." + direction + "_wilt")) {
                        helper.assertTrue(JournalGameTests.journal(player).entries().stream()
                                .filter(e -> e.id().equals(id)).count() == 1, "missing or repeated " + id);
                    }
                    helper.assertTrue(rec.toasts.size() == 1, "climate hints spammed toasts");
                });
                be.setPlantFlag(PlantState.CLIMATE_AWARE, false);
                // A real old-save tick unlocks the update notice, at most once.
                level.setBlock(at, level.getBlockState(at).setValue(
                        io.github.slugblue.wildroots.plant.FieldPlantBlock.STRAIN, 0), 2);
                ClimateStressGameTests.denyingGrowth(List.of(at), () ->
                        ClimateKit.tick(level, at, RandomSource.create(715), 2));
                helper.assertTrue(JournalGameTests.journal(player).has("hint.climate_update"), "no old-save notice");
            } finally {
                Journals.setExtraOnline(old);
                rec.close();
                player.discard();
            }
        });
    }

    @GameTest(template = SmokeGameTests.FLOOR_3X3X3, batch = "climate_feedback_heat", timeoutTicks = 600)
    public static void heatWarningsLensAndHints(GameTestHelper helper) {
        feedback(helper, Biomes.DESERT, "heat");
    }

    @GameTest(template = SmokeGameTests.FLOOR_3X3X3, batch = "climate_feedback_cold", timeoutTicks = 600)
    public static void coldWarningsLensAndHints(GameTestHelper helper) {
        feedback(helper, Biomes.FROZEN_PEAKS, "cold");
    }

    @GameTest(template = SmokeGameTests.FLOOR_3X3X3, batch = "climate_feedback_exempt", timeoutTicks = 600)
    public static void noWarningInTemperateClimateAndConfigOff(GameTestHelper helper) {
        ClimateStressGameTests.underground(helper, Biomes.PLAINS, List.of(ClimateStressGameTests.A), level -> {
            BlockPos at = helper.absolutePos(ClimateStressGameTests.A);
            var def = PlantFixtures.carrot().def();
            helper.assertTrue(Wilting.plantingWarning(level, at, def).isEmpty(), "temperate warning");
            Runnable undo = ClimateKit.setBiome(level, at, 12, Biomes.DESERT);
            try {
                ClimateKit.withWilting(false, () -> helper.assertTrue(
                        Wilting.plantingWarning(level, at, def).isEmpty(), "warning with wilting off"));
            } finally {
                undo.run();
            }
        });
    }
}
