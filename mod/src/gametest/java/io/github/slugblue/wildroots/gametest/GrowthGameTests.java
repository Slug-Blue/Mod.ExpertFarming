package io.github.slugblue.wildroots.gametest;

import io.github.slugblue.wildroots.WildRoots;
import io.github.slugblue.wildroots.core.registry.ModBlocks;
import io.github.slugblue.wildroots.genetics.api.Environment;
import io.github.slugblue.wildroots.genetics.api.Phenotype;
import io.github.slugblue.wildroots.genetics.api.SpeciesDef;
import io.github.slugblue.wildroots.plant.ClimateService;
import io.github.slugblue.wildroots.plant.FieldPlantBlock;
import io.github.slugblue.wildroots.plant.GrowthLogic;
import io.github.slugblue.wildroots.plant.PlantBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.BoneMealItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/** WR-132: environment and growth (GDD §3.5, §5.1, §12.1; economy §3.1; docs/05-architecture.md §10.1, §11.8). */
@GameTestHolder(WildRoots.MOD_ID)
@PrefixGameTestTemplate(false)
public final class GrowthGameTests {

    private GrowthGameTests() {
    }

    /**
     * 5 biomes × Y 64/120/200 × 3 points: the base minus 0.00125 per block
     * above Y 80 within 0.01 (noise) outside frozen biomes, and vanilla snow
     * ({@code coldEnoughToSnow}) agrees with {@code T < 0.15} everywhere.
     * No reflection: the private {@code Biome#getTemperature} is checked
     * through its public consequence (SEC-43, S11).
     */
    @GameTest(template = SmokeGameTests.FLOOR_3X3X3)
    public static void climateServiceMatchesVanillaTemperature(GameTestHelper helper) {
        Registry<Biome> reg = helper.getLevel().registryAccess().registryOrThrow(Registries.BIOME);
        @SuppressWarnings("unchecked")
        ResourceKey<Biome>[] biomes = new ResourceKey[] {Biomes.PLAINS, Biomes.MEADOW, Biomes.DESERT, Biomes.TAIGA,
                Biomes.FROZEN_OCEAN};
        int[] heights = {64, 120, 200};
        int[][] xz = {{0, 0}, {1000, -2000}, {12345, 6789}};
        for (ResourceKey<Biome> key : biomes) {
            Holder<Biome> holder = reg.getHolderOrThrow(key);
            Biome biome = holder.value();
            for (int y : heights) {
                for (int[] p : xz) {
                    BlockPos pos = new BlockPos(p[0], y, p[1]);
                    float t = ClimateService.temperature(holder, pos);
                    helper.assertTrue(biome.coldEnoughToSnow(pos) == (t < 0.15f),
                            key.location() + " " + pos + ": snow disagrees with T=" + t);
                    if (!key.equals(Biomes.FROZEN_OCEAN)) {
                        float formula = biome.getBaseTemperature() - 0.00125f * Math.max(0, y - 80);
                        helper.assertTrue(Math.abs(t - formula) <= 0.0100001f,
                                key.location() + " " + pos + ": T=" + t + " formula " + formula);
                    }
                }
            }
        }
        BlockPos here = helper.absolutePos(PlantFixtures.PLANT);
        helper.assertTrue(Float.compare(ClimateService.temperature(helper.getLevel(), here),
                ClimateService.temperature(helper.getLevel().getBiome(here), here)) == 0, "level variant");
        helper.succeed();
    }

    /** Counts Forge crop events at one position and can deny growth there. */
    public static final class CropProbe {
        final BlockPos pos;
        final boolean deny;
        int pre;
        int post;

        CropProbe(BlockPos pos, boolean deny) {
            this.pos = pos;
            this.deny = deny;
        }

        @SubscribeEvent
        public void onPre(BlockEvent.CropGrowEvent.Pre e) {
            if (e.getPos().equals(pos)) {
                pre++;
                if (deny) {
                    e.setResult(Event.Result.DENY);
                }
            }
        }

        @SubscribeEvent
        public void onPost(BlockEvent.CropGrowEvent.Post e) {
            if (e.getPos().equals(pos)) {
                post++;
            }
        }
    }

    /** Random ticks until the stage changes (at most {@code max}); returns the ticks used or -1. */
    static int tickUntilGrown(ServerLevel level, BlockPos abs, RandomSource random, int max) {
        int start = FieldPlantBlock.stage(level.getBlockState(abs));
        for (int i = 1; i <= max; i++) {
            BlockState s = level.getBlockState(abs);
            s.randomTick(level, abs, random);
            if (FieldPlantBlock.stage(level.getBlockState(abs)) != start) {
                return i;
            }
        }
        return -1;
    }

    @GameTest(template = SmokeGameTests.FLOOR_3X3X3)
    public static void randomTicksUseCachedChanceAndForgeHooks(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        PlantBlockEntity be = PlantFixtures.plant(PlantFixtures.place(helper, PlantFixtures.PLANT, 1), 21);
        BlockPos abs = helper.absolutePos(PlantFixtures.PLANT);
        RandomSource random = RandomSource.create(2101);

        CropProbe deny = new CropProbe(abs, true);
        MinecraftForge.EVENT_BUS.register(deny);
        try {
            for (int i = 0; i < 300; i++) {
                level.getBlockState(abs).randomTick(level, abs, random);
            }
        } finally {
            MinecraftForge.EVENT_BUS.unregister(deny);
        }
        helper.assertTrue(PlantFixtures.stage(helper, PlantFixtures.PLANT) == 1, "a denied plant grew");
        helper.assertTrue(deny.pre > 0 && deny.post == 0, "pre " + deny.pre + " post " + deny.post);

        float cached = be.growthChance();
        PlantBlockEntity.Live live = be.live(level).orElseThrow();
        Environment env = GrowthLogic.environment(level, abs);
        Phenotype p = GrowthLogic.phenotype(live, env).orElseThrow();
        double expected = GrowthLogic.chance(live.def(), p, env, 1, 0);
        helper.assertTrue(!Float.isNaN(cached) && Math.abs(cached - expected) < 1e-6,
                "cached chance " + cached + " expected " + expected);
        helper.assertTrue(env.moisturized(), "fixture environment " + env);

        CropProbe count = new CropProbe(abs, false);
        MinecraftForge.EVENT_BUS.register(count);
        try {
            int ticks = tickUntilGrown(level, abs, random, 2000);
            helper.assertTrue(ticks > 0, "no growth in 2000 random ticks at p=" + cached);
        } finally {
            MinecraftForge.EVENT_BUS.unregister(count);
        }
        helper.assertTrue(count.post == 1 && count.pre >= 1, "pre " + count.pre + " post " + count.post);
        helper.assertTrue(PlantFixtures.stage(helper, PlantFixtures.PLANT) == 2, "stage after growth");
        helper.assertTrue(be.growthChance() != cached || GrowthLogic.stageMinutes(live.def().timing(), 3)
                == GrowthLogic.stageMinutes(live.def().timing(), 2), "chance not refreshed on stage change");
        helper.succeed();
    }

    /** n = 400 per stage, fixed RNG: stages 1–3 advance at 0.35 ± 0.05, flowering never. */
    @GameTest(template = SmokeGameTests.FLOOR_3X3X3)
    public static void boneMealAdvancesAt035ButNeverOnFlowering(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        PlantFixtures.plant(PlantFixtures.place(helper, PlantFixtures.PLANT, 1), 22);
        BlockPos abs = helper.absolutePos(PlantFixtures.PLANT);
        FieldPlantBlock block = ModBlocks.WILD_CARROT.get();
        RandomSource random = RandomSource.create(2202);
        StringBuilder rates = new StringBuilder();
        for (int stage : new int[] {1, 2, 3, 4}) {
            int advanced = 0;
            for (int i = 0; i < 400; i++) {
                BlockState s = level.getBlockState(abs).setValue(FieldPlantBlock.STAGE, stage);
                level.setBlock(abs, s, 2);
                if (block.isValidBonemealTarget(level, abs, s, false)
                        && block.isBonemealSuccess(level, random, abs, s)) {
                    block.performBonemeal(level, random, abs, s);
                }
                if (FieldPlantBlock.stage(level.getBlockState(abs)) != stage) {
                    advanced++;
                }
            }
            double rate = advanced / 400.0;
            rates.append(stage).append('=').append(rate).append(' ');
            if (stage == FieldPlantBlock.FLOWER_STAGE) {
                helper.assertTrue(advanced == 0, "bone meal worked on flowering: " + advanced + "/400");
            } else {
                helper.assertTrue(Math.abs(rate - 0.35) <= 0.05, "stage " + stage + " rate " + rate);
            }
        }
        // the vanilla item path refuses flowering and the final stage
        for (int stage : new int[] {4, 5}) {
            level.setBlock(abs, level.getBlockState(abs).setValue(FieldPlantBlock.STAGE, stage), 2);
            helper.assertTrue(!BoneMealItem.growCrop(new ItemStack(Items.BONE_MEAL), level, abs),
                    "bone meal item used on stage " + stage);
        }
        helper.succeed();
    }

    /** Far out of the temperature window: slower growth, but the plant keeps living. */
    @GameTest(template = SmokeGameTests.FLOOR_3X3X3)
    public static void outOfWindowGrowsSlowerButDoesNotDie(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        PlantBlockEntity be = PlantFixtures.plant(PlantFixtures.place(helper, PlantFixtures.PLANT, 1), 23);
        PlantBlockEntity.Live live = be.live(level).orElseThrow();
        SpeciesDef d = live.def();
        Environment inside = new Environment((d.tempMin() + d.tempMax()) / 2, true, 15, 0);
        // 0.3 beyond the window even if the heat tolerance widens it by its maximum 0.6
        Environment outside = new Environment(d.tempMax() + 0.6 + 0.3, true, 15, 0);
        Phenotype pIn = GrowthLogic.phenotype(live, inside).orElseThrow();
        Phenotype pOut = GrowthLogic.phenotype(live, outside).orElseThrow();
        for (int stage = 0; stage < 5; stage++) {
            double in = GrowthLogic.chance(d, pIn, inside, stage, 0);
            double out = GrowthLogic.chance(d, pOut, outside, stage, 0);
            helper.assertTrue(out < in && out > 0, "stage " + stage + ": inside " + in + " outside " + out);
        }
        // the plant never dies from random ticks
        be.setGrowthChance(1e-6f);
        BlockPos abs = helper.absolutePos(PlantFixtures.PLANT);
        RandomSource random = RandomSource.create(2301);
        for (int i = 0; i < 500; i++) {
            level.getBlockState(abs).randomTick(level, abs, random);
        }
        helper.assertTrue(level.getBlockState(abs).getBlock() == ModBlocks.WILD_CARROT.get(), "plant died");
        helper.succeed();
    }

    /** {@code size}, {@code ailing}, {@code variant} change only together with the stage. */
    @GameTest(template = SmokeGameTests.FLOOR_3X3X3)
    public static void lookChangesOnlyWithTheStage(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        PlantBlockEntity be = PlantFixtures.plant(PlantFixtures.place(helper, PlantFixtures.PLANT, 0), 24);
        BlockPos abs = helper.absolutePos(PlantFixtures.PLANT);
        RandomSource random = RandomSource.create(2401);
        int changes = 0;
        for (int i = 0; i < 5000 && FieldPlantBlock.stage(level.getBlockState(abs)) < 5; i++) {
            BlockState before = level.getBlockState(abs);
            before.randomTick(level, abs, random);
            BlockState after = level.getBlockState(abs);
            if (FieldPlantBlock.stage(after) == FieldPlantBlock.stage(before)) {
                helper.assertTrue(after == before, "state changed without a stage change: " + before + " -> " + after);
            } else {
                changes++;
                helper.assertTrue(FieldPlantBlock.stage(after) == FieldPlantBlock.stage(before) + 1, "skipped a stage");
                PlantBlockEntity.Live live = be.live(level).orElseThrow();
                Phenotype p = GrowthLogic.phenotype(live, GrowthLogic.environment(level, abs)).orElseThrow();
                helper.assertTrue(after == GrowthLogic.look(after, live.def(), p), "look not from the phenotype " + after);
                helper.assertTrue(after.getValue(FieldPlantBlock.ACTIVE) && !after.getValue(FieldPlantBlock.VILLAGE),
                        "growth reset active/village");
            }
        }
        helper.assertTrue(changes == 5 && PlantFixtures.stage(helper, PlantFixtures.PLANT) == 5,
                "grew " + changes + " stages");
        helper.assertTrue(!level.getBlockState(abs).isRandomlyTicking(), "final stage still ticks");
        helper.succeed();
    }
}
