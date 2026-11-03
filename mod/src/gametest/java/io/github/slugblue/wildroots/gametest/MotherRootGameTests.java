package io.github.slugblue.wildroots.gametest;

import io.github.slugblue.wildroots.WildRoots;
import io.github.slugblue.wildroots.core.registry.ModItems;
import io.github.slugblue.wildroots.core.safety.NbtLimits;
import io.github.slugblue.wildroots.genetics.api.Phenotype;
import io.github.slugblue.wildroots.item.LotNbt;
import io.github.slugblue.wildroots.item.LotStacks;
import io.github.slugblue.wildroots.item.MotherItem;
import io.github.slugblue.wildroots.item.MotherNbt;
import io.github.slugblue.wildroots.item.ProduceStacks;
import io.github.slugblue.wildroots.plant.FieldPlantBlock;
import io.github.slugblue.wildroots.plant.GrowthLogic;
import io.github.slugblue.wildroots.plant.HarvestService;
import io.github.slugblue.wildroots.plant.PlantBlockEntity;
import io.github.slugblue.wildroots.plant.PlantState;
import java.util.Arrays;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/** WR-137: the mother root (GDD §5.2; narrative §7.4). */
@GameTestHolder(WildRoots.MOD_ID)
@PrefixGameTestTemplate(false)
public final class MotherRootGameTests {

    private MotherRootGameTests() {
    }

    /** Drops of the plant at {@code rel} broken by {@code breaker} (no tool). */
    static List<ItemStack> drops(GameTestHelper helper, BlockPos rel, Entity breaker) {
        BlockPos abs = helper.absolutePos(rel);
        return Block.getDrops(helper.getLevel().getBlockState(abs), helper.getLevel(), abs,
                helper.getLevel().getBlockEntity(abs), breaker, ItemStack.EMPTY);
    }

    /** Random ticks with a fixed RNG until {@code stage}; records the stages passed. */
    static boolean growTo(GameTestHelper helper, BlockPos rel, int stage, boolean[] seen, long seed) {
        ServerLevel level = helper.getLevel();
        BlockPos abs = helper.absolutePos(rel);
        RandomSource r = RandomSource.create(seed);
        for (int i = 0; i < 20_000 && PlantFixtures.stage(helper, rel) < stage; i++) {
            level.getBlockState(abs).randomTick(level, abs, r);
            int s = PlantFixtures.stage(helper, rel);
            if (s >= 0 && seen != null) {
                seen[s] = true;
            }
        }
        return PlantFixtures.stage(helper, rel) == stage;
    }

    @GameTest(template = SmokeGameTests.FLOOR_3X3X3)
    public static void motherRootDoesNotStackAndCarriesThePlant(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Player player = helper.makeMockSurvivalPlayer();
        PlantBlockEntity be = PlantFixtures.plant(PlantFixtures.place(helper, PlantFixtures.PLANT, FieldPlantBlock.HARVEST_STAGE), 71);
        PlantBlockEntity.Live live = be.live(level).orElseThrow();
        Phenotype p = GrowthLogic.phenotype(live, GrowthLogic.environment(level, helper.absolutePos(PlantFixtures.PLANT)))
                .orElseThrow();
        ItemStack mother = drops(helper, PlantFixtures.PLANT, player).stream()
                .filter(s -> s.is(ModItems.MOTHER_ROOT.get())).findFirst().orElseThrow();
        helper.assertTrue(mother.getMaxStackSize() == 1, "mother root stacks");
        MotherNbt.Mother m = MotherItem.read(mother).orElseThrow();
        helper.assertTrue(Arrays.equals(m.genome().a(), live.genome().bits()), "genome not copied bit for bit");
        helper.assertTrue(Arrays.equals(m.noise(), live.noise().bytes()), "noise not copied");
        helper.assertTrue(NbtLimits.mother(mother.getTag().get(MotherNbt.KEY)).ok(), "mother root over 512 B");
        int grade = HarvestService.grade(live, p);
        helper.assertTrue(m.grade() == ProduceStacks.normalise(grade, p.bitter()), "grade " + m.grade() + " vs " + grade);
        FoodProperties f = mother.getFoodProperties(player);
        helper.assertTrue(mother.isEdible() && f.getNutrition() == ProduceStacks.nutrition(3, m.grade())
                        && Math.abs(f.getSaturationModifier() - ProduceStacks.saturation(0.6, m.grade(), m.bitter())) < 1e-5,
                "food " + f.getNutrition() + "/" + f.getSaturationModifier() + " for grade " + m.grade());
        helper.succeed();
    }

    @GameTest(template = SmokeGameTests.FLOOR_3X3X3, timeoutTicks = 200)
    public static void plantedMotherFlowersAndGivesALot(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Player player = helper.makeMockSurvivalPlayer();
        PlantBlockEntity source = PlantFixtures.plant(PlantFixtures.place(helper, new BlockPos(0, 2, 0),
                FieldPlantBlock.HARVEST_STAGE), 72);
        ItemStack mother = drops(helper, new BlockPos(0, 2, 0), player).stream()
                .filter(s -> s.is(ModItems.MOTHER_ROOT.get())).findFirst().orElseThrow();
        MotherNbt.Mother m = MotherItem.read(mother).orElseThrow();

        PlantFixtures.farmland(helper, PlantFixtures.PLANT);
        helper.assertTrue(PlantingGameTests.use(helper, player, mother, PlantFixtures.PLANT).consumesAction(), "not planted");
        helper.assertTrue(mother.isEmpty(), "mother root not used up");
        PlantBlockEntity be = (PlantBlockEntity) helper.getBlockEntity(PlantFixtures.PLANT);
        PlantBlockEntity.Live live = be.live(level).orElseThrow();
        helper.assertTrue(Arrays.equals(live.genome().bits(), m.genome().a()), "genome changed");
        helper.assertTrue(Arrays.equals(live.noise().bytes(), m.noise()), "noise changed");
        helper.assertTrue(be.data().has(PlantState.MOTHER), "not marked as a mother");
        helper.assertTrue(PlantFixtures.stage(helper, PlantFixtures.PLANT) == FieldPlantBlock.HARVEST_STAGE, "start stage");
        double minutes = GrowthLogic.stageMinutes(live.def().timing(), 4) + GrowthLogic.stageMinutes(live.def().timing(), 5);
        helper.assertTrue(Math.abs(minutes - live.def().timing().seedStageMin()) < 1e-9, "flowering takes " + minutes);

        boolean[] seen = new boolean[6];
        helper.assertTrue(growTo(helper, PlantFixtures.PLANT, FieldPlantBlock.SEED_STAGE, seen, 7201), "did not set seed");
        helper.assertTrue(seen[FieldPlantBlock.FLOWER_STAGE], "skipped flowering");
        List<ItemStack> d = drops(helper, PlantFixtures.PLANT, player);
        helper.assertTrue(d.size() == 1 && d.get(0).is(ModItems.SEED_LOT.get()), "drops " + d);
        LotNbt.Lot lot = (LotNbt.Lot) LotStacks.read(d.get(0));
        helper.assertTrue(Arrays.equals(lot.mother().a(), m.genome().a()), "lot mother is not the mother root");
        helper.assertTrue(source.live(level).isPresent(), "source plant");
        helper.succeed();
    }

    @GameTest(template = SmokeGameTests.FLOOR_3X3X3, timeoutTicks = 200)
    public static void plantLeftOnTheRootFlowersAndGivesALotWithoutFood(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Player player = helper.makeMockSurvivalPlayer();
        PlantFixtures.farmland(helper, PlantFixtures.PLANT);
        ItemStack lot = PlantingGameTests.lot(level, 3);
        helper.assertTrue(PlantingGameTests.use(helper, player, lot, PlantFixtures.PLANT).consumesAction(), "not planted");
        helper.assertTrue(growTo(helper, PlantFixtures.PLANT, FieldPlantBlock.SEED_STAGE, null, 7301), "did not set seed");
        List<ItemStack> d = drops(helper, PlantFixtures.PLANT, player);
        helper.assertTrue(!d.isEmpty() && d.stream().allMatch(s -> s.is(ModItems.SEED_LOT.get())), "drops " + d);
        helper.succeed();
    }
}
