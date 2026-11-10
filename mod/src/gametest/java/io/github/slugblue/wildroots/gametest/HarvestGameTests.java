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
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
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
        BlockPos abs = helper.absolutePos(PlantFixtures.PLANT);
        AABB area = new AABB(abs).inflate(2);
        for (int stage : new int[] {0, 1, 2}) {
            PlantBlockEntity be = PlantFixtures.plant(PlantFixtures.place(helper, PlantFixtures.PLANT, stage), 87 + stage);
            PlantBlockEntity.Live before = be.live(level).orElseThrow();
            // the real break path: Level#destroyBlock by the player, drops as item entities
            level.getEntitiesOfClass(ItemEntity.class, area).forEach(ItemEntity::discard);
            helper.assertTrue(level.destroyBlock(abs, true, player), "not broken at stage " + stage);
            List<ItemEntity> dropped = level.getEntitiesOfClass(ItemEntity.class, area);
            List<ItemStack> d = dropped.stream().map(ItemEntity::getItem).toList();
            LotNbt.Lot lot = onlyLot(d);
            helper.assertTrue(lot.seedling() && d.size() == 1 && d.get(0).getCount() == 1, "not a seedling: " + d);
            ItemStack s = d.get(0).copy();
            dropped.forEach(ItemEntity::discard);
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

    /** ADR-0007 invariant (WR-136, WR-136a): a segregating 5×5 bed gives ≤ 6 distinct stacks per harvest item, ≤ 12 with root colours. */
    @GameTest(template = SmokeGameTests.FLOOR_3X3X3, timeoutTicks = 200)
    public static void segregatingBedStaysWithinTheStackVariantBound(GameTestHelper helper) {
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
        // ≤ 6 per item, ≤ 12 for an item with root colours (grade × bitter × colour, WR-136a)
        variants.forEach((item, tags) -> {
            int bound = item instanceof ProduceItem p && p.rootColours() ? 12 : 6;
            helper.assertTrue(tags.size() <= bound, item + ": " + tags.size() + " variants, bound " + bound);
        });
        helper.succeed();
    }

    /** Minor 3: an explosion whose source is a player is automation: no fathers. */
    @GameTest(template = SmokeGameTests.FLOOR_3X3X3, timeoutTicks = 100)
    public static void explosionWithAPlayerSourceGivesNoFathers(GameTestHelper helper) {
        seedPlant(helper, PlantFixtures.PLANT, 92);
        BlockPos abs = helper.absolutePos(PlantFixtures.PLANT);
        Player player = helper.makeMockSurvivalPlayer();
        helper.getLevel().explode(player, abs.getX() + 0.5, abs.getY() + 0.5, abs.getZ() + 0.5, 1.0f,
                Level.ExplosionInteraction.BLOCK);
        expectLotWithoutFathers(helper, PlantFixtures.PLANT, 2);
    }

    /**
     * Batch 3 mutation pass (survivor M6): the explosion test above passes even without the
     * EXPLOSION_RADIUS check, because the vanilla explosion path never reaches it with a Player as
     * THIS_ENTITY. Here the loot params carry both: a player and an explosion radius is automation
     * (no fathers); the same params without the radius are a player harvest (fathers kept).
     */
    @GameTest(template = SmokeGameTests.FLOOR_3X3X3)
    public static void explosionRadiusWithAPlayerEntityIsAutomation(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        PlantBlockEntity be = seedPlant(helper, PlantFixtures.PLANT, 93);
        BlockPos abs = helper.absolutePos(PlantFixtures.PLANT);
        Player player = helper.makeMockSurvivalPlayer();
        net.minecraft.world.level.block.state.BlockState state = level.getBlockState(abs);
        LotNbt.Lot exploded = onlyLot(HarvestService.drops(state, params(level, abs, be, player)
                .withParameter(LootContextParams.EXPLOSION_RADIUS, 1.0f), RandomSource.create(93)));
        helper.assertTrue(exploded.fathers().isEmpty() && exploded.pOut() == 0, "explosion with a player kept the fathers");
        LotNbt.Lot harvested = onlyLot(HarvestService.drops(state, params(level, abs, be, player), RandomSource.create(93)));
        helper.assertTrue(harvested.fathers().size() == 3 && harvested.pOut() > 0, "control: player harvest lost the fathers");
        helper.succeed();
    }

    /** Minor 4: getDrops has no side effects; the mutation flag is consumed only by the real break. */
    @GameTest(template = SmokeGameTests.FLOOR_3X3X3, timeoutTicks = 100)
    public static void getDropsTwiceDoesNotConsumeTheRareClaim(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Player player = helper.makeMockSurvivalPlayer();
        PlantBlockEntity be = seedPlant(helper, PlantFixtures.PLANT, 93);
        be.setPlantFlag(io.github.slugblue.wildroots.plant.PlantState.MUTANT, true);
        for (int i = 0; i < 2; i++) {
            LotNbt.Lot lot = onlyLot(MotherRootGameTests.drops(helper, PlantFixtures.PLANT, player));
            helper.assertTrue(lot.rare() == LotNbt.RareSource.MUTATION, "preview " + i + " lost the flag");
            helper.assertTrue(be.rareAvailable(), "getDrops consumed the claim");
        }
        BlockPos abs = helper.absolutePos(PlantFixtures.PLANT);
        helper.assertTrue(level.destroyBlock(abs, true, player), "not broken");
        List<ItemStack> items = level.getEntitiesOfClass(ItemEntity.class, new AABB(abs).inflate(2)).stream()
                .map(ItemEntity::getItem).toList();
        helper.assertTrue(mutationLots(items) == 1, "mutation lots after Level#destroyBlock: " + mutationLots(items));
        helper.assertTrue(be.data().has(io.github.slugblue.wildroots.plant.PlantState.RARE_CLAIMED),
                "the real break did not consume the claim");
        helper.succeed();
    }

    /** Breaking an inert or a sleeping plant drops nothing, so its data is never copied (§6.1). */
    @GameTest(template = SmokeGameTests.FLOOR_3X3X3, batch = "wr13x_inert_drops", timeoutTicks = 200)
    public static void inertOrSleepingPlantDropsNothing(GameTestHelper helper) {
        Player player = helper.makeMockSurvivalPlayer();
        for (int stage : new int[] {1, 3, 5}) {
            PlantBlockEntity be = seedPlant(helper, PlantFixtures.PLANT, 94 + stage);
            helper.getLevel().setBlock(helper.absolutePos(PlantFixtures.PLANT), helper.getBlockState(PlantFixtures.PLANT)
                    .setValue(FieldPlantBlock.STAGE, stage), 2);
            CompoundTag tag = be.saveWithFullMetadata();
            CompoundTag future = tag.copy();
            future.getCompound(io.github.slugblue.wildroots.plant.PlantNbt.ROOT).putInt("v", 99);
            be.load(future);
            helper.assertTrue(MotherRootGameTests.drops(helper, PlantFixtures.PLANT, player).isEmpty(),
                    "inert plant dropped at stage " + stage);
            CompoundTag lost = tag.copy();
            lost.getCompound(io.github.slugblue.wildroots.plant.PlantNbt.ROOT).putString("sp", "wildroots:gt_gone");
            lost.getCompound(io.github.slugblue.wildroots.plant.PlantNbt.ROOT).getCompound("g").putString("sp", "wildroots:gt_gone");
            be.load(lost);
            helper.assertTrue(be.status() == PlantBlockEntity.Status.SLEEPING, "not sleeping");
            helper.assertTrue(MotherRootGameTests.drops(helper, PlantFixtures.PLANT, player).isEmpty(),
                    "sleeping plant dropped at stage " + stage);
        }
        helper.succeed();
    }

    /** A player and automation harvest the same plant with the same RNG: the same seed count, only the fathers differ. */
    @GameTest(template = SmokeGameTests.FLOOR_3X3X3)
    public static void playerAndAutomationGetTheSameSeedCount(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Player player = helper.makeMockSurvivalPlayer();
        PlantBlockEntity be = seedPlant(helper, PlantFixtures.PLANT, 99);
        BlockPos abs = helper.absolutePos(PlantFixtures.PLANT);
        BlockState state = level.getBlockState(abs);
        for (long seed = 1; seed <= 20; seed++) {
            List<ItemStack> a = HarvestService.drops(state, params(level, abs, be, player), RandomSource.create(seed));
            List<ItemStack> b = HarvestService.drops(state, params(level, abs, be, FakePlayerFactory.getMinecraft(level)),
                    RandomSource.create(seed));
            helper.assertTrue(a.get(0).getCount() == b.get(0).getCount(), "seed " + seed + ": " + a + " vs " + b);
            helper.assertTrue(onlyLot(a).fathers().size() == 3 && onlyLot(b).fathers().isEmpty(), "fathers");
        }
        helper.succeed();
    }

    static LootParams.Builder params(ServerLevel level, BlockPos abs, PlantBlockEntity be, net.minecraft.world.entity.Entity who) {
        return new LootParams.Builder(level).withParameter(LootContextParams.ORIGIN, net.minecraft.world.phys.Vec3.atCenterOf(abs))
                .withParameter(LootContextParams.TOOL, ItemStack.EMPTY)
                .withOptionalParameter(LootContextParams.THIS_ENTITY, who)
                .withOptionalParameter(LootContextParams.BLOCK_ENTITY, be);
    }

    static long mutationLots(List<ItemStack> items) {
        return items.stream().filter(s -> s.is(ModItems.SEED_LOT.get()))
                .filter(s -> LotStacks.read(s) instanceof LotNbt.Lot l && l.rare() == LotNbt.RareSource.MUTATION).count();
    }

    static PlantBlockEntity mutant(GameTestHelper helper, long key) {
        PlantBlockEntity be = seedPlant(helper, PlantFixtures.PLANT, key);
        be.setPlantFlag(io.github.slugblue.wildroots.plant.PlantState.MUTANT, true);
        return be;
    }

    static List<ItemStack> itemsAround(GameTestHelper helper) {
        return helper.getLevel().getEntitiesOfClass(ItemEntity.class,
                new AABB(helper.absolutePos(PlantFixtures.PLANT)).inflate(4)).stream().map(ItemEntity::getItem).toList();
    }

    /**
     * N1: a survival ServerPlayer breaks a mutant through
     * ServerPlayerGameMode#destroyBlock (removeBlock, then playerDestroy):
     * the lot carries MUTATION exactly once and the claim is stored.
     */
    @GameTest(template = SmokeGameTests.FLOOR_3X3X3, batch = "wr13x_server_player", timeoutTicks = 100)
    public static void survivalPlayerBreakGrantsMutationOnce(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        PlantBlockEntity be = mutant(helper, 120);
        // a plain survival ServerPlayer (makeMockServerPlayerInLevel needs a network channel in 1.20.1)
        net.minecraft.server.level.ServerPlayer player = new net.minecraft.server.level.ServerPlayer(level.getServer(),
                level, new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(), "wr-survival"));
        player.setPos(net.minecraft.world.phys.Vec3.atCenterOf(helper.absolutePos(PlantFixtures.PLANT)).add(1, 0, 0));
        try {
            player.gameMode.changeGameModeForPlayer(net.minecraft.world.level.GameType.SURVIVAL);
            helper.assertTrue(!player.gameMode.isCreative(), "player is in creative");
            BlockPos abs = helper.absolutePos(PlantFixtures.PLANT);
            helper.assertTrue(player.gameMode.destroyBlock(abs), "not broken");
            helper.assertTrue(PlantFixtures.stage(helper, PlantFixtures.PLANT) < 0, "plant still there");
            List<ItemStack> items = itemsAround(helper);
            helper.assertTrue(mutationLots(items) == 1, "mutation lots " + mutationLots(items) + " in " + items);
            helper.assertTrue(be.data().has(io.github.slugblue.wildroots.plant.PlantState.RARE_CLAIMED), "claim not stored");
        } finally {
            player.discard();
        }
        helper.succeed();
    }

    /** N1 for automation: an explosion and a piston give exactly one MUTATION lot. */
    @GameTest(template = SmokeGameTests.FLOOR_3X3X3, timeoutTicks = 100)
    public static void explosionOfAMutantGivesOneMutationLot(GameTestHelper helper) {
        mutant(helper, 121);
        BlockPos abs = helper.absolutePos(PlantFixtures.PLANT);
        helper.getLevel().explode(null, abs.getX() + 0.5, abs.getY() + 0.5, abs.getZ() + 0.5, 1.0f,
                Level.ExplosionInteraction.BLOCK);
        helper.runAfterDelay(2, () -> {
            helper.assertTrue(mutationLots(itemsAround(helper)) == 1, "mutation lots " + itemsAround(helper));
            helper.succeed();
        });
    }

    @GameTest(template = SmokeGameTests.FLOOR_3X3X3, timeoutTicks = 100)
    public static void pistonOnAMutantGivesOneMutationLot(GameTestHelper helper) {
        mutant(helper, 122);
        helper.setBlock(new BlockPos(0, 2, 1), Blocks.PISTON.defaultBlockState().setValue(PistonBaseBlock.FACING, Direction.EAST));
        helper.setBlock(new BlockPos(0, 2, 0), Blocks.REDSTONE_BLOCK);
        helper.runAfterDelay(10, () -> {
            helper.assertTrue(PlantFixtures.stage(helper, PlantFixtures.PLANT) < 0, "plant still there");
            helper.assertTrue(mutationLots(itemsAround(helper)) == 1, "mutation lots " + itemsAround(helper));
            helper.succeed();
        });
    }

    /** N1: a stage change (setBlock with the same block) claims nothing. */
    @GameTest(template = SmokeGameTests.FLOOR_3X3X3)
    public static void stageChangeClaimsNothing(GameTestHelper helper) {
        PlantBlockEntity be = mutant(helper, 123);
        BlockPos abs = helper.absolutePos(PlantFixtures.PLANT);
        ServerLevel level = helper.getLevel();
        level.setBlock(abs, level.getBlockState(abs).setValue(FieldPlantBlock.STAGE, 4), 3);
        level.setBlock(abs, level.getBlockState(abs).setValue(FieldPlantBlock.STAGE, 5), 3);
        helper.assertTrue(helper.getBlockEntity(PlantFixtures.PLANT) == be, "block entity replaced");
        helper.assertTrue(be.rareAvailable() && !be.data().has(io.github.slugblue.wildroots.plant.PlantState.RARE_CLAIMED), "a stage change claimed the flag");
        helper.succeed();
    }

    /** Throws at one position: another mod's crop event handler failing inside our random tick. */
    public static final class ThrowingCropProbe {
        final BlockPos pos;

        ThrowingCropProbe(BlockPos pos) {
            this.pos = pos;
        }

        @net.minecraftforge.eventbus.api.SubscribeEvent
        public void onPre(net.minecraftforge.event.level.BlockEvent.CropGrowEvent.Pre e) {
            if (e.getPos().equals(pos)) {
                throw new IllegalStateException("test failure inside the random tick");
            }
        }
    }

    /**
     * SEC-34: logic that throws in a random tick makes the plant inert (the
     * onFailure of Guarded runs before strict mode rethrows); it drops nothing.
     */
    @GameTest(template = SmokeGameTests.FLOOR_3X3X3)
    public static void throwingRandomTickMakesThePlantInert(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        PlantBlockEntity be = PlantFixtures.plant(PlantFixtures.place(helper, PlantFixtures.PLANT, 1), 124);
        be.live(level).orElseThrow();
        be.setGrowthChance(1f);
        BlockPos abs = helper.absolutePos(PlantFixtures.PLANT);
        ThrowingCropProbe probe = new ThrowingCropProbe(abs);
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.register(probe);
        boolean thrown = false;
        try {
            level.getBlockState(abs).randomTick(level, abs, RandomSource.create(1));
        } catch (RuntimeException e) {
            thrown = true; // strict mode (dev and GameTest) rethrows after onFailure
        } finally {
            net.minecraftforge.common.MinecraftForge.EVENT_BUS.unregister(probe);
        }
        helper.assertTrue(thrown || !io.github.slugblue.wildroots.core.safety.Guarded.shared().strict(), "no exception");
        helper.assertTrue(be.status() == PlantBlockEntity.Status.INERT, "status " + be.status());
        helper.assertTrue(MotherRootGameTests.drops(helper, PlantFixtures.PLANT, helper.makeMockSurvivalPlayer()).isEmpty(),
                "a failed plant dropped items");
        // the data was not lost: a reload of the saved root gives a live plant again
        PlantBlockEntity reloaded = PlantFixtures.saveLoad(be);
        helper.assertTrue(reloaded.live(level).isPresent(), "data lost after the failure");
        helper.succeed();
    }

    /** A root whose reading throws (outside the decoder's own checks). */
    static final class ThrowingRoot extends CompoundTag {
        @Override
        public int getInt(String key) {
            throw new IllegalStateException("test failure while loading");
        }
    }

    /** SEC-34, load path: a load that throws leaves the plant inert and keeps its raw root through save/load. */
    @GameTest(template = SmokeGameTests.FLOOR_3X3X3)
    public static void throwingLoadKeepsTheRawRoot(GameTestHelper helper) {
        PlantBlockEntity be = PlantFixtures.plant(PlantFixtures.place(helper, PlantFixtures.PLANT, 1), 125);
        CompoundTag tag = be.saveWithFullMetadata();
        CompoundTag root = tag.getCompound(io.github.slugblue.wildroots.plant.PlantNbt.ROOT).copy();
        ThrowingRoot bad = new ThrowingRoot();
        for (String k : root.getAllKeys()) {
            bad.put(k, root.get(k).copy());
        }
        tag.put(io.github.slugblue.wildroots.plant.PlantNbt.ROOT, bad);
        PlantBlockEntity target = (PlantBlockEntity) BlockEntity.loadStatic(be.getBlockPos(), be.getBlockState(),
                be.saveWithFullMetadata());
        try {
            target.load(tag);
        } catch (RuntimeException e) {
            // strict mode rethrows after onFailure
        }
        helper.assertTrue(target.status() == PlantBlockEntity.Status.INERT, "status " + target.status());
        CompoundTag saved = target.saveWithoutMetadata().getCompound(io.github.slugblue.wildroots.plant.PlantNbt.ROOT);
        helper.assertTrue(saved.equals(root), "raw root not kept: " + saved);
        PlantBlockEntity again = PlantFixtures.saveLoad(target);
        helper.assertTrue(again.live(helper.getLevel()).isPresent(), "data lost through save/load");
        helper.succeed();
    }
}
