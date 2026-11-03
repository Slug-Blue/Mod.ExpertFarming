package io.github.slugblue.wildroots.gametest;

import io.github.slugblue.wildroots.WildRoots;
import io.github.slugblue.wildroots.core.registry.ModItems;
import io.github.slugblue.wildroots.data.Definitions;
import io.github.slugblue.wildroots.genetics.api.Classification;
import io.github.slugblue.wildroots.genetics.api.CultivarId;
import io.github.slugblue.wildroots.genetics.api.Phenotype;
import io.github.slugblue.wildroots.genetics.api.Traits;
import io.github.slugblue.wildroots.genetics.pollination.Brush;
import io.github.slugblue.wildroots.item.LotFathersNbt;
import io.github.slugblue.wildroots.item.LotNbt;
import io.github.slugblue.wildroots.item.LotStacks;
import io.github.slugblue.wildroots.item.ProduceItem;
import io.github.slugblue.wildroots.item.ProduceStacks;
import io.github.slugblue.wildroots.plant.FieldPlantBlock;
import io.github.slugblue.wildroots.plant.GrowthLogic;
import io.github.slugblue.wildroots.plant.HarvestService;
import io.github.slugblue.wildroots.plant.Passport;
import io.github.slugblue.wildroots.plant.PendingLot;
import io.github.slugblue.wildroots.plant.PlantBlockEntity;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.piston.PistonBaseBlock;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/** WR-134 (and the WR-136 bed invariant): harvest (GDD §5.2, §5.3, §6.4, §14; docs/05-architecture.md §10.2, §6.3; SEC-26). */
@GameTestHolder(WildRoots.MOD_ID)
@PrefixGameTestTemplate(false)
public final class HarvestGameTests {

    private HarvestGameTests() {
    }

    static List<LotFathersNbt.Father> fathers(int n) {
        List<LotFathersNbt.Father> f = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            f.add(new LotFathersNbt.Father(Definitions.current().genetics().encode(PlantFixtures.genome(800 + i))
                    .toFather(), 1 + i, false));
        }
        return f;
    }

    /** A plant at stage 5 with a pending lot of 3 fathers and p_out 0.6. */
    static PlantBlockEntity seedPlant(GameTestHelper helper, BlockPos rel, long key) {
        PlantBlockEntity be = PlantFixtures.plant(PlantFixtures.place(helper, rel, FieldPlantBlock.SEED_STAGE), key);
        be.setPendingLot(new PendingLot(fathers(3), 0.6f, Brush.NONE, true));
        return be;
    }

    static LotNbt.Lot onlyLot(List<ItemStack> drops) {
        List<ItemStack> lots = drops.stream().filter(s -> s.is(ModItems.SEED_LOT.get())).toList();
        if (lots.size() != 1) {
            throw new IllegalStateException("expected one lot in " + drops);
        }
        return (LotNbt.Lot) LotStacks.read(lots.get(0));
    }

    @GameTest(template = SmokeGameTests.FLOOR_3X3X3)
    public static void playerHarvestGivesFoodOfOneGradeAndAMother(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Player player = helper.makeMockSurvivalPlayer();
        PlantBlockEntity be = PlantFixtures.plant(PlantFixtures.place(helper, PlantFixtures.PLANT, FieldPlantBlock.HARVEST_STAGE), 81);
        PlantBlockEntity.Live live = be.live(level).orElseThrow();
        Phenotype p = GrowthLogic.phenotype(live, GrowthLogic.environment(level, helper.absolutePos(PlantFixtures.PLANT)))
                .orElseThrow();
        double x = 1.0 * (1 + 4 * p.pOr(Traits.YIELD, 0));
        List<ItemStack> d = MotherRootGameTests.drops(helper, PlantFixtures.PLANT, player);
        List<ItemStack> food = d.stream().filter(s -> s.getItem() instanceof ProduceItem).toList();
        helper.assertTrue(food.size() == 1 && food.get(0).is(ModItems.WILD_CARROT_ROOT.get()), "food stacks " + d);
        int n = food.get(0).getCount();
        helper.assertTrue(n == (int) Math.floor(x) || n == (int) Math.ceil(x), "food " + n + " for " + x);
        int grade = ProduceStacks.normalise(HarvestService.grade(live, p), p.bitter());
        helper.assertTrue(ProduceStacks.grade(food.get(0).getTag()) == grade, "grade");
        helper.assertTrue(food.get(0).getTag().getAllKeys().stream()
                .allMatch(k -> k.equals(ProduceStacks.GRADE) || k.equals(ProduceStacks.BITTER)), "extra NBT on food");
        helper.assertTrue(d.stream().filter(s -> s.is(ModItems.MOTHER_ROOT.get())).count() == 1, "mother roots " + d);
        helper.succeed();
    }

    @GameTest(template = SmokeGameTests.FLOOR_3X3X3)
    public static void playerHarvestOfSeedsKeepsFathersAndPOut(GameTestHelper helper) {
        PlantBlockEntity be = seedPlant(helper, PlantFixtures.PLANT, 82);
        LotNbt.Lot lot = onlyLot(MotherRootGameTests.drops(helper, PlantFixtures.PLANT, helper.makeMockSurvivalPlayer()));
        helper.assertTrue(lot.fathers().size() == 3 && Math.abs(lot.pOut() - 0.6f) < 1e-6, "fathers/p_out " + lot);
        helper.assertTrue(Arrays.equals(lot.mother().a(), be.live(helper.getLevel()).orElseThrow().genome().bits()),
                "mother genome");
        helper.succeed();
    }

    @GameTest(template = SmokeGameTests.FLOOR_3X3X3)
    public static void fakePlayerGivesALotWithoutFathers(GameTestHelper helper) {
        seedPlant(helper, PlantFixtures.PLANT, 83);
        LotNbt.Lot lot = onlyLot(MotherRootGameTests.drops(helper, PlantFixtures.PLANT,
                FakePlayerFactory.getMinecraft(helper.getLevel())));
        helper.assertTrue(lot.fathers().isEmpty() && lot.pOut() == 0, "fake player kept the fathers");
        helper.succeed();
    }

    /** Waits, then checks the lot dropped as an item entity around {@code rel}. */
    static void expectLotWithoutFathers(GameTestHelper helper, BlockPos rel, int delay) {
        helper.runAfterDelay(delay, () -> {
            AABB box = new AABB(helper.absolutePos(rel)).inflate(4);
            List<ItemStack> items = helper.getLevel().getEntitiesOfClass(ItemEntity.class, box).stream()
                    .map(ItemEntity::getItem).toList();
            helper.assertTrue(PlantFixtures.stage(helper, rel) < 0, "plant still there: " + helper.getBlockState(rel)
                   );
            LotNbt.Lot lot = onlyLot(items);
            helper.assertTrue(lot.fathers().isEmpty() && lot.pOut() == 0, "automation kept the fathers");
            helper.succeed();
        });
    }

    @GameTest(template = SmokeGameTests.FLOOR_3X3X3, timeoutTicks = 100)
    public static void pistonGivesALotWithoutFathers(GameTestHelper helper) {
        seedPlant(helper, PlantFixtures.PLANT, 84);
        helper.setBlock(new BlockPos(0, 2, 1), Blocks.PISTON.defaultBlockState().setValue(PistonBaseBlock.FACING, Direction.EAST));
        helper.setBlock(new BlockPos(0, 2, 0), Blocks.REDSTONE_BLOCK);
        expectLotWithoutFathers(helper, PlantFixtures.PLANT, 10);
    }

    @GameTest(template = SmokeGameTests.FLOOR_3X3X3, timeoutTicks = 100)
    public static void waterGivesALotWithoutFathers(GameTestHelper helper) {
        seedPlant(helper, PlantFixtures.PLANT, 85);
        // Water in this GameTest world does not spread (neither scheduled nor a manual fluid tick; see
        // the WR-134 report). The test runs what WaterFluid#beforeDestroyingBlock does when water takes
        // a crop: Block.dropResources(state, level, pos, blockEntity), without an entity, then the water.
        BlockPos abs = helper.absolutePos(PlantFixtures.PLANT);
        ServerLevel level = helper.getLevel();
        Block.dropResources(level.getBlockState(abs), level, abs, level.getBlockEntity(abs));
        level.setBlock(abs, Blocks.WATER.defaultBlockState(), 3);
        expectLotWithoutFathers(helper, PlantFixtures.PLANT, 2);
    }

    @GameTest(template = SmokeGameTests.FLOOR_3X3X3, timeoutTicks = 100)
    public static void explosionGivesALotWithoutFathers(GameTestHelper helper) {
        seedPlant(helper, PlantFixtures.PLANT, 86);
        BlockPos abs = helper.absolutePos(PlantFixtures.PLANT);
        helper.getLevel().explode(null, abs.getX() + 0.5, abs.getY() + 0.5, abs.getZ() + 0.5, 1.0f,
                Level.ExplosionInteraction.BLOCK);
        expectLotWithoutFathers(helper, PlantFixtures.PLANT, 2);
    }

    /** SEC-26: a young plant (stage 1–2) drops its realised genome; planting it again gives the same plant. */
    @GameTest(template = SmokeGameTests.FLOOR_3X3X3)
    public static void youngPlantDropsItsRealisedGenome(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Player player = helper.makeMockSurvivalPlayer();
        for (int stage : new int[] {1, 2}) {
            PlantBlockEntity be = PlantFixtures.plant(PlantFixtures.place(helper, PlantFixtures.PLANT, stage), 87 + stage);
            PlantBlockEntity.Live before = be.live(level).orElseThrow();
            List<ItemStack> d = MotherRootGameTests.drops(helper, PlantFixtures.PLANT, player);
            LotNbt.Lot lot = onlyLot(d);
            helper.assertTrue(lot.seedling() && d.get(0).getCount() == 1, "not a seedling: " + d);
            helper.setBlock(PlantFixtures.PLANT, Blocks.AIR);
            ItemStack s = d.get(0);
            helper.assertTrue(PlantingGameTests.use(helper, player, s, PlantFixtures.PLANT).consumesAction(), "replant");
            PlantBlockEntity.Live after = ((PlantBlockEntity) helper.getBlockEntity(PlantFixtures.PLANT)).live(level).orElseThrow();
            helper.assertTrue(after.genome().equals(before.genome()) && after.noise().equals(before.noise()),
                    "replanting the seedling rerolled at stage " + stage);
            helper.setBlock(PlantFixtures.PLANT, Blocks.AIR);
        }
        helper.succeed();
    }

    /** GDD §6.2 item 6: a cultivar drops its harvest item and lots with its id. */
    @GameTest(template = SmokeGameTests.FLOOR_3X3X3)
    public static void cultivarDropsCultivarItems(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Player player = helper.makeMockSurvivalPlayer();
        Classification carrot = new Classification(java.util.Optional.of(CultivarId.of("wildroots:carrot")), 2, false);
        Consumer<PlantBlockEntity> init = be -> be.init(Definitions.current().genetics(), new PlantBlockEntity.Init(
                PlantFixtures.SP, PlantFixtures.genome(90), PlantFixtures.noise(91), Passport.PLAYER, new UUID(5, 6),
                carrot, null, 2, 0, 0));
        init.accept(PlantFixtures.place(helper, PlantFixtures.PLANT, FieldPlantBlock.HARVEST_STAGE));
        List<ItemStack> d = MotherRootGameTests.drops(helper, PlantFixtures.PLANT, player);
        helper.assertTrue(d.stream().anyMatch(s -> s.is(ModItems.CARROT.get())), "no wildroots:carrot in " + d);
        helper.assertTrue(d.stream().noneMatch(s -> s.is(ModItems.WILD_CARROT_ROOT.get())), "wild root from a cultivar");
        init.accept(PlantFixtures.place(helper, PlantFixtures.PLANT, FieldPlantBlock.SEED_STAGE));
        LotNbt.Lot lot = onlyLot(MotherRootGameTests.drops(helper, PlantFixtures.PLANT, player));
        helper.assertTrue(lot.cultivar().equals(java.util.Optional.of("wildroots:carrot")), "lot cultivar " + lot.cultivar());
        helper.succeed();
    }

    /** ADR-0007 invariant (WR-136): a segregating 5×5 bed gives at most 6 distinct stacks per harvest item. */
    @GameTest(template = SmokeGameTests.FLOOR_3X3X3, timeoutTicks = 200)
    public static void segregatingBedGivesAtMostSixStackVariants(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Player player = helper.makeMockSurvivalPlayer();
        ItemStack lot = PlantingGameTests.lot(level, 25);
        Map<Item, Set<CompoundTag>> variants = new HashMap<>();
        Set<String> genomes = new HashSet<>();
        PlantFixtures.farmland(helper, PlantFixtures.PLANT);
        BlockPos abs = helper.absolutePos(PlantFixtures.PLANT);
        for (int i = 0; i < 25; i++) {
            helper.assertTrue(PlantingGameTests.use(helper, player, lot, PlantFixtures.PLANT).consumesAction(), "plant " + i);
            PlantBlockEntity be = (PlantBlockEntity) helper.getBlockEntity(PlantFixtures.PLANT);
            genomes.add(Arrays.toString(be.live(level).orElseThrow().genome().bits()));
            level.setBlock(abs, level.getBlockState(abs).setValue(FieldPlantBlock.STAGE, FieldPlantBlock.HARVEST_STAGE), 2);
            for (ItemStack s : MotherRootGameTests.drops(helper, PlantFixtures.PLANT, player)) {
                if (s.getItem() instanceof ProduceItem) {
                    variants.computeIfAbsent(s.getItem(), k -> new HashSet<>()).add(s.getTag());
                }
            }
            helper.setBlock(PlantFixtures.PLANT, Blocks.AIR);
        }
        helper.assertTrue(genomes.size() >= 20, "bed does not segregate: " + genomes.size() + " genomes");
        helper.assertTrue(!variants.isEmpty(), "no harvest");
        variants.forEach((item, tags) -> helper.assertTrue(tags.size() <= 6, item + ": " + tags.size() + " variants"));
        helper.succeed();
    }
}
