package io.github.slugblue.wildroots.gametest;

import io.github.slugblue.wildroots.WildRoots;
import io.github.slugblue.wildroots.core.registry.ModBlocks;
import io.github.slugblue.wildroots.genetics.api.Environment;
import io.github.slugblue.wildroots.genetics.api.SpeciesDef;
import io.github.slugblue.wildroots.genetics.api.Phenotype;
import io.github.slugblue.wildroots.item.LotNbt;
import io.github.slugblue.wildroots.item.LotStacks;
import io.github.slugblue.wildroots.plant.FieldPlantBlock;
import io.github.slugblue.wildroots.plant.GrowthLogic;
import io.github.slugblue.wildroots.plant.PlantBlockEntity;
import io.github.slugblue.wildroots.plant.PlantService;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * Batch 3 test pass: determinism (SEC-13, SEC-26) and the growth-chance
 * cache (§10.1, WR-132). The same lot, cell and remaining count give the
 * same plant whatever happened in between (item and block entity saved and
 * loaded, game time moved on); halves of a lazy lot realise alike in two
 * players' inventories in different ticks; the cached chance is replaced
 * on every stage change and recomputed after a reload.
 */
@GameTestHolder(WildRoots.MOD_ID)
@PrefixGameTestTemplate(false)
public final class DeterminismGameTests {

    private DeterminismGameTests() {
    }

    static PlantBlockEntity plantAt(GameTestHelper helper, BlockPos rel) {
        return (PlantBlockEntity) helper.getBlockEntity(rel);
    }

    /** Item save/load, block entity save/load and later game ticks change nothing of the seedling. */
    @GameTest(template = SmokeGameTests.FLOOR_3X3X3, timeoutTicks = 100)
    public static void sameLotCellAndCountGiveTheSamePlantAfterSaveAndLoad(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Player player = helper.makeMockSurvivalPlayer();
        BlockPos rel = PlantFixtures.PLANT;
        BlockPos abs = helper.absolutePos(rel);
        PlantFixtures.farmland(helper, rel);
        ItemStack original = PlantingGameTests.lot(level, 5);
        // the lot as it comes back from a player file or a chest after a restart
        ItemStack reloaded = ItemStack.of(original.save(new CompoundTag()));
        helper.assertTrue(ItemStack.isSameItemSameTags(original, reloaded) && reloaded.getCount() == 5, "item round trip");

        LotNbt.Lot lot = SeedLotGameTests.lot(original);
        PlantService.Seedling before = PlantService.seedling(level, lot, 5, abs).orElseThrow();
        long t0 = level.getGameTime();
        helper.assertTrue(PlantingGameTests.use(helper, player, original.copy(), rel).consumesAction(), "planting");
        PlantBlockEntity be = plantAt(helper, rel);
        PlantBlockEntity.Live live = be.live(level).orElseThrow();
        helper.assertTrue(live.genome().equals(before.genome()) && live.noise().equals(before.noise()), "planted != seedling");
        PlantBlockEntity loaded = PlantFixtures.saveLoad(be);
        helper.assertTrue(loaded.data().genome().equals(be.data().genome())
                && java.util.Arrays.equals(loaded.data().noise(), be.data().noise()), "block entity save/load changed it");

        helper.runAfterDelay(7, () -> {
            helper.assertTrue(level.getGameTime() > t0, "time did not move");
            PlantService.Seedling later = PlantService.seedling(level, SeedLotGameTests.lot(reloaded), 5, abs)
                    .orElseThrow();
            helper.assertTrue(later.genome().equals(before.genome()) && later.noise().equals(before.noise())
                    && later.status().equals(before.status()) && later.mutant() == before.mutant(),
                    "the reloaded lot rolled another seedling later");
            helper.setBlock(rel, Blocks.AIR);
            helper.assertTrue(PlantingGameTests.use(helper, player, reloaded, rel).consumesAction(), "replanting");
            PlantBlockEntity again = plantAt(helper, rel);
            helper.assertTrue(again.live(level).orElseThrow().genome().equals(before.genome()), "replanted genome differs");
            // a different remaining count or cell does roll anew
            helper.assertTrue(!PlantService.seedling(level, lot, 4, abs).orElseThrow().genome().equals(before.genome()),
                    "remaining count ignored");
            helper.assertTrue(!PlantService.seedling(level, lot, 5, abs.east()).orElseThrow().genome()
                    .equals(before.genome()), "position ignored");
            helper.succeed();
        });
    }

    /** SEC-13: halves of a lazy lot realised by two players' inventory ticks, 6 game ticks apart, are equal. */
    @GameTest(template = SmokeGameTests.FLOOR_3X3X3, timeoutTicks = 100)
    public static void lazyHalvesRealiseAlikeInDifferentInventoriesAndTicks(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Player a = helper.makeMockSurvivalPlayer();
        Player b = helper.makeMockSurvivalPlayer();
        ItemStack lazy = LotStacks.lazy(level, PlantFixtures.SP, "meadow", 6, LotNbt.RareSource.NONE);
        ItemStack half = lazy.split(3);
        a.getInventory().setItem(0, lazy);
        b.getInventory().setItem(17, half);
        a.getInventory().tick();
        helper.assertTrue(LotStacks.read(a.getInventory().getItem(0)) instanceof LotNbt.Lot, "A did not realise");
        helper.assertTrue(LotStacks.read(b.getInventory().getItem(17)) instanceof LotNbt.Lazy, "B realised too early");
        long t0 = level.getGameTime();
        helper.runAfterDelay(6, () -> {
            b.getInventory().tick();
            ItemStack ra = a.getInventory().getItem(0);
            ItemStack rb = b.getInventory().getItem(17);
            helper.assertTrue(level.getGameTime() > t0 && LotStacks.read(rb) instanceof LotNbt.Lot, "B did not realise");
            helper.assertTrue(ItemStack.isSameItemSameTags(ra, rb), "halves realised differently:\n" + ra.getTag()
                    + "\n" + rb.getTag());
            helper.succeed();
        });
    }

    /** §10.1: every stage change replaces the cached chance by the chance of the new stage; a reload recomputes it. */
    @GameTest(template = SmokeGameTests.FLOOR_3X3X3)
    public static void growthChanceIsReplacedOnEveryStageChangeAndAfterReload(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        PlantBlockEntity be = PlantFixtures.mother(PlantFixtures.place(helper, PlantFixtures.PLANT, 0), 61);
        BlockPos abs = helper.absolutePos(PlantFixtures.PLANT);
        FieldPlantBlock block = ModBlocks.WILD_CARROT.get();
        PlantBlockEntity.Live live = be.live(level).orElseThrow();
        Environment env = GrowthLogic.environment(level, abs);
        Phenotype p = GrowthLogic.phenotype(live, env).orElseThrow();
        float sentinel = 0.123f;
        for (int stage = 0; stage < FieldPlantBlock.MAX_STAGE; stage++) {
            be.setGrowthChance(sentinel);
            BlockState s = level.getBlockState(abs);
            block.growCrops(level, abs, s);
            int now = PlantFixtures.stage(helper, PlantFixtures.PLANT);
            helper.assertTrue(now == stage + 1, "growCrops from " + stage + " gave " + now);
            float expected = (float) GrowthLogic.chance(live.def(), p, env, now, 0);
            helper.assertTrue(be.growthChance() == expected, "stage " + now + ": cached " + be.growthChance()
                    + " expected " + expected);
            if (now < FieldPlantBlock.MAX_STAGE) {
                helper.assertTrue(expected != sentinel, "sentinel equals the real chance");
            }
        }
        helper.assertTrue(be.growthChance() == 0f, "final stage chance " + be.growthChance());
        // a random tick at a cached stage does not recompute
        level.setBlock(abs, level.getBlockState(abs).setValue(FieldPlantBlock.STAGE, 2), 2);
        be.setGrowthChance(0f);
        for (int i = 0; i < 50; i++) {
            level.getBlockState(abs).randomTick(level, abs, net.minecraft.util.RandomSource.create(i));
        }
        helper.assertTrue(PlantFixtures.stage(helper, PlantFixtures.PLANT) == 2 && be.growthChance() == 0f,
                "a cached chance of 0 was recomputed by a random tick");
        // reload: the cache is not saved and is recomputed from the stage in the world
        PlantBlockEntity loaded = PlantFixtures.saveLoad(be);
        helper.assertTrue(Float.isNaN(loaded.growthChance()), "cache survived a reload: " + loaded.growthChance());
        PlantBlockEntity.Live l2 = loaded.live(level).orElseThrow();
        double c = GrowthLogic.cachedChance(level, abs, 2, loaded, l2);
        helper.assertTrue((float) c == (float) GrowthLogic.chance(l2.def(), p, env, 2, 0), "recomputed " + c);
        helper.succeed();
    }

    /** GDD §3.5: far out of the window the plant has a phenotype and a positive chance; nothing in growth removes it. */
    @GameTest(template = SmokeGameTests.FLOOR_3X3X3)
    public static void extremeTemperatureSlowsButNeverKills(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        PlantBlockEntity be = PlantFixtures.mother(PlantFixtures.place(helper, PlantFixtures.PLANT, 1), 62);
        PlantBlockEntity.Live live = be.live(level).orElseThrow();
        SpeciesDef d = live.def();
        for (double t : new double[] {d.tempMin() - 10, d.tempMin() - 2, d.tempMax() + 2, d.tempMax() + 10}) {
            for (boolean moist : new boolean[] {true, false}) {
                Environment env = new Environment(t, moist, 0, 0);
                Phenotype p = GrowthLogic.phenotype(live, env).orElse(null);
                helper.assertTrue(p != null, "no phenotype at T " + t);
                helper.assertTrue(Double.isFinite(p.vigor()), "vigor " + p.vigor());
                for (int stage = 0; stage < FieldPlantBlock.MAX_STAGE; stage++) {
                    double c = GrowthLogic.chance(d, p, env, stage, 0);
                    helper.assertTrue(c >= GrowthLogic.MIN_CHANCE && c <= GrowthLogic.MAX_CHANCE,
                            "T " + t + " stage " + stage + " chance " + c);
                }
                BlockState look = GrowthLogic.look(ModBlocks.WILD_CARROT.get().getStateForAge(3), d, p);
                helper.assertTrue(look.getBlock() == ModBlocks.WILD_CARROT.get() && FieldPlantBlock.stage(look) == 3,
                        "look changed the block or stage: " + look);
            }
        }
        // in the world: many random ticks at the fastest chance only advance stages, the block stays a plant
        BlockPos abs = helper.absolutePos(PlantFixtures.PLANT);
        net.minecraft.util.RandomSource r = net.minecraft.util.RandomSource.create(62);
        for (int i = 0; i < 2000; i++) {
            if (Float.isNaN(be.growthChance()) || be.growthChance() > 0) {
                be.setGrowthChance((float) GrowthLogic.MAX_CHANCE);
            }
            level.getBlockState(abs).randomTick(level, abs, r);
            helper.assertTrue(level.getBlockState(abs).getBlock() == ModBlocks.WILD_CARROT.get(), "plant gone at tick " + i);
        }
        helper.assertTrue(PlantFixtures.stage(helper, PlantFixtures.PLANT) == FieldPlantBlock.MAX_STAGE, "did not finish");
        helper.succeed();
    }
}
