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

    /**
     * D1 (`00-vision.md` item 32): an own plant that is not a mother root
     * waits at harvest ripeness until it is harvested. Random ticks, bone
     * meal (not spent) and {@code growCrops} leave it at stage 3; the harvest
     * gives food and a mother root.
     */
    @GameTest(template = SmokeGameTests.FLOOR_3X3X3, timeoutTicks = 200)
    public static void plantLeftOnTheRootWaitsAtHarvestRipeness(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Player player = helper.makeMockSurvivalPlayer();
        PlantFixtures.farmland(helper, PlantFixtures.PLANT);
        ItemStack lot = PlantingGameTests.lot(level, 3);
        helper.assertTrue(PlantingGameTests.use(helper, player, lot, PlantFixtures.PLANT).consumesAction(), "not planted");
        helper.assertTrue(growTo(helper, PlantFixtures.PLANT, FieldPlantBlock.HARVEST_STAGE, null, 7301),
                "did not ripen");
        BlockPos abs = helper.absolutePos(PlantFixtures.PLANT);
        RandomSource r = RandomSource.create(7302);
        for (int i = 0; i < 20_000; i++) {
            level.getBlockState(abs).randomTick(level, abs, r);
        }
        helper.assertTrue(PlantFixtures.stage(helper, PlantFixtures.PLANT) == FieldPlantBlock.HARVEST_STAGE,
                "left harvest ripeness on random ticks: " + PlantFixtures.stage(helper, PlantFixtures.PLANT));
        PlantBlockEntity be = (PlantBlockEntity) helper.getBlockEntity(PlantFixtures.PLANT);
        helper.assertTrue(be.growthChance() == 0f, "cached chance " + be.growthChance());
        FieldPlantBlock block = (FieldPlantBlock) level.getBlockState(abs).getBlock();
        helper.assertTrue(!block.isValidBonemealTarget(level, abs, level.getBlockState(abs), false),
                "bone meal target");
        ItemStack meal = new ItemStack(net.minecraft.world.item.Items.BONE_MEAL, 4);
        helper.assertTrue(!net.minecraft.world.item.BoneMealItem.growCrop(meal, level, abs) && meal.getCount() == 4,
                "bone meal used: " + meal.getCount());
        block.growCrops(level, abs, level.getBlockState(abs));
        helper.assertTrue(PlantFixtures.stage(helper, PlantFixtures.PLANT) == FieldPlantBlock.HARVEST_STAGE, "growCrops");
        List<ItemStack> d = drops(helper, PlantFixtures.PLANT, player);
        helper.assertTrue(d.stream().anyMatch(s -> s.is(ModItems.MOTHER_ROOT.get()))
                && d.stream().noneMatch(s -> s.is(ModItems.SEED_LOT.get())), "drops " + d);
        helper.succeed();
    }

    /** D1: a planted mother root leaves stage 3 (bone meal, growCrops); the rule does not touch it. */
    @GameTest(template = SmokeGameTests.FLOOR_3X3X3)
    public static void motherAtHarvestRipenessStillGrowsAndTakesBoneMeal(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        PlantBlockEntity be = PlantFixtures.mother(PlantFixtures.place(helper, PlantFixtures.PLANT,
                FieldPlantBlock.HARVEST_STAGE), 76);
        BlockPos abs = helper.absolutePos(PlantFixtures.PLANT);
        FieldPlantBlock block = (FieldPlantBlock) level.getBlockState(abs).getBlock();
        helper.assertTrue(block.isValidBonemealTarget(level, abs, level.getBlockState(abs), false), "bone meal target");
        helper.assertTrue(GrowthLogic.cachedChance(level, abs, FieldPlantBlock.HARVEST_STAGE, be,
                be.live(level).orElseThrow()) > 0, "mother does not grow");
        block.growCrops(level, abs, level.getBlockState(abs));
        helper.assertTrue(PlantFixtures.stage(helper, PlantFixtures.PLANT) == FieldPlantBlock.FLOWER_STAGE, "growCrops");
        helper.succeed();
    }

    /** Minor 6: the mother root keeps the status of its plant, the hysteresis flag included. */
    @GameTest(template = SmokeGameTests.FLOOR_3X3X3)
    public static void motherRootKeepsTheInheritedStatus(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Player player = helper.makeMockSurvivalPlayer();
        PlantBlockEntity be = PlantFixtures.place(helper, new BlockPos(0, 2, 0), FieldPlantBlock.HARVEST_STAGE);
        be.init(io.github.slugblue.wildroots.data.Definitions.current().genetics(), new PlantBlockEntity.Init(
                PlantFixtures.SP, PlantFixtures.genome(74), PlantFixtures.noise(75),
                io.github.slugblue.wildroots.plant.Passport.PLAYER, new java.util.UUID(7, 7),
                new io.github.slugblue.wildroots.genetics.api.Classification(java.util.Optional.of(
                        io.github.slugblue.wildroots.genetics.api.CultivarId.of("wildroots:eastern_carrot")), 1, true),
                null, 1, 0, 0));
        ItemStack mother = drops(helper, new BlockPos(0, 2, 0), player).stream()
                .filter(s -> s.is(ModItems.MOTHER_ROOT.get())).findFirst().orElseThrow();
        helper.assertTrue(MotherItem.read(mother).orElseThrow().inherited(), "inherited not stored");
        PlantFixtures.farmland(helper, PlantFixtures.PLANT);
        helper.assertTrue(PlantingGameTests.use(helper, player, mother, PlantFixtures.PLANT).consumesAction(), "not planted");
        PlantBlockEntity planted = (PlantBlockEntity) helper.getBlockEntity(PlantFixtures.PLANT);
        helper.assertTrue(planted.data().inherited() && planted.data().tier() == 1
                && "wildroots:eastern_carrot".equals(planted.data().cultivar()), "status lost");
        helper.assertTrue(level != null, "level");
        helper.succeed();
    }

    /**
     * M1: a mother root and a seedling lot stored in the old layout, planted
     * under the new one, get the noise migrated like the block entity does
     * (trait order of the new layout), not the stored bytes as they are.
     */
    @GameTest(template = SmokeGameTests.FLOOR_3X3X3, batch = "wr13x_noise_migration", timeoutTicks = 200)
    public static void storedNoiseMigratesWhenPlantedUnderANewLayout(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Player player = helper.makeMockSurvivalPlayer();
        var gs = io.github.slugblue.wildroots.data.Definitions.current().genetics();
        var oldDef = PlantFixtures.carrot().def();
        var oldLayout = io.github.slugblue.wildroots.genetics.model.GenomeLayout.of(oldDef);
        // a new trait that sorts first shifts every noise byte (traits are ordered by id)
        var newDef = PlantFixtures.carrotAs(PlantFixtures.SP, q -> {
            java.util.List<io.github.slugblue.wildroots.genetics.api.QuantTrait> l = new java.util.ArrayList<>(q);
            l.add(new io.github.slugblue.wildroots.genetics.api.QuantTrait(
                    io.github.slugblue.wildroots.genetics.model.TraitId.of("aroma"), 4, 0.06, false));
            return l;
        });
        var genome = gs.encode(PlantFixtures.genome(76));
        byte[] noise = PlantFixtures.noise(77).bytes();
        ItemStack mother = new ItemStack(ModItems.MOTHER_ROOT.get());
        mother.getOrCreateTag().put(MotherNbt.KEY, MotherNbt.write(new MotherNbt.Mother(genome, noise,
                java.util.Optional.empty(), 0, false, java.util.Optional.empty(), 0, 0, 3, false)));
        ItemStack seedling = LotStacks.stack(LotStacks.seedlingTag(new LotStacks.Parent(PlantFixtures.SP,
                java.util.Optional.empty(), genome, java.util.Optional.empty(), 0, 0), noise, 77L), 1);
        BlockPos a = new BlockPos(0, 2, 1);
        BlockPos b = new BlockPos(2, 2, 1);
        // a and b lie on the template edge: their light must not come from the world (WR-195)
        PlantFixtures.litFarmland(helper, a);
        PlantFixtures.litFarmland(helper, b);
        PlantFixtures.whenLit(helper, List.of(a, b), () -> {
        helper.assertTrue(PlantFixtures.lit(helper, a) && PlantFixtures.lit(helper, b), "cells not lit");
        var original = io.github.slugblue.wildroots.data.Definitions.current();
        try {
            io.github.slugblue.wildroots.data.Definitions.publish(PlantFixtures.withSpecies(original, newDef));
            var gsNew = io.github.slugblue.wildroots.data.Definitions.current().genetics();
            helper.assertTrue(!gsNew.layout(newDef).orElseThrow().equals(oldLayout), "layout did not change");
            for (var c : java.util.List.of(java.util.Map.entry(a, mother), java.util.Map.entry(b, seedling))) {
                helper.assertTrue(PlantingGameTests.use(helper, player, c.getValue(), c.getKey()).consumesAction(),
                        "not planted at " + c.getKey());
                BlockPos abs = helper.absolutePos(c.getKey());
                var expected = gsNew.migrateNoise(new io.github.slugblue.wildroots.genetics.phenotype.NoiseVector(noise),
                        oldLayout, newDef, io.github.slugblue.wildroots.plant.PlantService.noiseMigrationKey(abs, PlantFixtures.SP))
                        .orElseThrow();
                // WR-195: name what is in the cell when the plant is gone (order-dependent failure)
                helper.assertTrue(helper.getBlockEntity(c.getKey()) instanceof PlantBlockEntity, "no plant at "
                        + c.getKey() + " right after planting: " + level.getBlockState(abs) + " on "
                        + level.getBlockState(abs.below()) + ", light " + level.getRawBrightness(abs, 0) + ", sky "
                        + level.canSeeSky(abs));
                var live = ((PlantBlockEntity) helper.getBlockEntity(c.getKey())).live(level).orElseThrow();
                helper.assertTrue(live.noise().equals(expected), "noise not migrated at " + c.getKey());
                helper.assertTrue(live.noise().size() == newDef.quantitative().size(), "noise length");
            }
        } finally {
            io.github.slugblue.wildroots.data.Definitions.publish(original);
        }
        helper.succeed();
        }, 0);
    }

    /**
     * WR-195 regression for the order-dependent failure above: a planted
     * mother root in a dark cell (raw light below 8, no sky) pops on the
     * next shape update of a neighbour (vanilla {@code CropBlock#canSurvive});
     * the same cell lit by {@link PlantFixtures#litFarmland} keeps it.
     */
    @GameTest(template = SmokeGameTests.FLOOR_3X3X3, timeoutTicks = 100)
    public static void darkCellPopsOnANeighbourUpdateLitCellKeepsThePlant(GameTestHelper helper) {
        BlockPos cell = PlantFixtures.PLANT;
        PlantFixtures.farmland(helper, cell);
        // a sealed dark cell: stone on the four sides and on top
        BlockPos[] walls = {cell.west(), cell.east(), cell.north(), cell.south(), cell.above()};
        for (BlockPos w : walls) {
            helper.setBlock(w, net.minecraft.world.level.block.Blocks.STONE);
        }
        Player player = helper.makeMockSurvivalPlayer();
        helper.runAfterDelay(10, () -> {
            helper.assertFalse(PlantFixtures.lit(helper, cell), "sealed cell still lit after 10 ticks");
            helper.assertTrue(PlantingGameTests.use(helper, player, mother(), cell).consumesAction(), "dark: not planted");
            helper.assertTrue(helper.getBlockEntity(cell) instanceof PlantBlockEntity, "dark: gone at planting");
            helper.setBlock(cell.west(), net.minecraft.world.level.block.Blocks.COBBLESTONE);
            helper.assertTrue(helper.getBlockState(cell).isAir(), "dark: the plant survived a neighbour update");
            // the fix: the same cell with the light block of litFarmland
            helper.setBlock(cell.above(), net.minecraft.world.level.block.Blocks.LIGHT);
            PlantFixtures.whenLit(helper, List.of(cell), () -> {
                helper.assertTrue(PlantFixtures.lit(helper, cell), "light block did not light the cell");
                helper.assertTrue(PlantingGameTests.use(helper, player, mother(), cell).consumesAction(), "lit: not planted");
                helper.setBlock(cell.west(), net.minecraft.world.level.block.Blocks.STONE);
                helper.assertTrue(helper.getBlockEntity(cell) instanceof PlantBlockEntity,
                        "lit: the plant popped on a neighbour update");
                helper.succeed();
            }, 0);
        });
    }

    /** A wild mother root (genome 91), as dug from a plant at harvest ripeness. */
    static ItemStack mother() {
        var gs = io.github.slugblue.wildroots.data.Definitions.current().genetics();
        ItemStack m = new ItemStack(ModItems.MOTHER_ROOT.get());
        m.getOrCreateTag().put(MotherNbt.KEY, MotherNbt.write(new MotherNbt.Mother(gs.encode(PlantFixtures.genome(91)),
                PlantFixtures.noise(92).bytes(), java.util.Optional.empty(), 0, false, java.util.Optional.empty(), 0, 0,
                3, false)));
        return m;
    }
}
