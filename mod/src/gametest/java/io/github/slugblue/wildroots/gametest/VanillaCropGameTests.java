package io.github.slugblue.wildroots.gametest;

import io.github.slugblue.wildroots.WildRoots;
import io.github.slugblue.wildroots.core.config.ServerConfig;
import io.github.slugblue.wildroots.core.registry.ModBlocks;
import io.github.slugblue.wildroots.plant.FieldPlantBlock;
import io.github.slugblue.wildroots.plant.Passport;
import io.github.slugblue.wildroots.plant.PlantBlockEntity;
import io.github.slugblue.wildroots.vanilla.LineMarker;
import io.github.slugblue.wildroots.vanilla.VanillaCrops;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.ForgeHooks;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * WR-162 (GDD §9.1–9.2; architecture §11.2; SEC-23, SEC-35): the vanilla
 * carrot of the loaded carrot line. Strict (default): planting is cancelled
 * for any entity, fake players included; soft: a player plants it and it
 * grows as vanilla. On its growth tick a vanilla carrot becomes a wild plant,
 * or a village landrace only in a chunk without the line in its marker and
 * with a bell within 32 blocks; at most 16 conversions per level per tick.
 * Each test has its own batch: they change the mode, the chunk marker and
 * the conversion budget of the level.
 */
@GameTestHolder(WildRoots.MOD_ID)
@PrefixGameTestTemplate(false)
public final class VanillaCropGameTests {

    static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(VanillaCropGameTests.class);

    private VanillaCropGameTests() {
    }

    static BlockState carrots(int age) {
        return Blocks.CARROTS.defaultBlockState().setValue(CropBlock.AGE, age);
    }

    /** A vanilla carrot at {@code rel} on moist farmland, without shape updates (dark areas). */
    static void vanilla(GameTestHelper helper, BlockPos rel, int age) {
        PlantFixtures.farmland(helper, rel);
        helper.getLevel().setBlock(helper.absolutePos(rel), carrots(age), 2);
    }

    /** The growth tick of the crop through Forge (the event vanilla {@code randomTick} posts). */
    static boolean growTick(GameTestHelper helper, BlockPos rel) {
        BlockPos abs = helper.absolutePos(rel);
        return ForgeHooks.onCropsGrowPre(helper.getLevel(), abs, helper.getLevel().getBlockState(abs), true);
    }

    static boolean use(Player p, ItemStack stack, GameTestHelper helper, BlockPos plantRel) {
        BlockPos farm = helper.absolutePos(plantRel.below());
        p.setItemInHand(InteractionHand.MAIN_HAND, stack);
        stack.useOn(new UseOnContext(p, InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(farm).add(0, 0.5, 0), Direction.UP, farm, false)));
        return helper.getBlockState(plantRel).is(Blocks.CARROTS);
    }

    @GameTest(template = SmokeGameTests.FLOOR_3X3X3, batch = "wr162_strict", timeoutTicks = 100)
    public static void strictCancelsVanillaCarrotPlantingByAnyone(GameTestHelper helper) {
        BlockPos a = new BlockPos(0, 2, 0);
        BlockPos b = new BlockPos(2, 2, 2);
        PlantFixtures.litFarmland(helper, a);
        PlantFixtures.litFarmland(helper, b);
        PlantFixtures.whenLit(helper, List.of(a, b), () -> {
            helper.assertTrue(ServerConfig.MODE.get() == ServerConfig.Mode.STRICT, "default mode is not strict");
            ServerLevel level = helper.getLevel();
            Player fake = FakePlayerFactory.getMinecraft(level);
            ItemStack seeds = new ItemStack(Items.CARROT, 4);
            BlockPos absA = helper.absolutePos(a);
            level.setBlock(absA, carrots(0), 2);
            boolean manual = net.minecraftforge.event.ForgeEventFactory.onBlockPlace(fake,
                    net.minecraftforge.common.util.BlockSnapshot.create(level.dimension(), level, absA), Direction.UP);
            level.setBlock(absA, Blocks.AIR.defaultBlockState(), 2);
            // Forge keeps captured block snapshots per level; an earlier test that threw out of an item's
            // useOn leaves them behind, and the next placement would post them as a multi-place event
            LOG.info("WR-162 strict: capture flag {}, stale snapshots {}", level.captureBlockSnapshots,
                    level.capturedBlockSnapshots.size());
            level.captureBlockSnapshots = false;
            level.capturedBlockSnapshots.clear();
            boolean planted = use(fake, seeds, helper, a);
            helper.assertTrue(!planted, "a fake player planted a vanilla carrot; manual event cancelled " + manual
                    + ", tag " + carrots(0).is(VanillaCrops.REMOVED_BLOCKS) + ", count " + seeds.getCount());
            helper.assertTrue(seeds.getCount() == 4, "the carrot was spent: " + seeds.getCount());
            // a real player through the right-click path: denied on farmland
            ServerPlayer p = JournalGameTests.survival(helper, "wr162_strict");
            try {
                ItemStack mine = new ItemStack(Items.CARROT, 2);
                p.setItemInHand(InteractionHand.MAIN_HAND, mine);
                BlockPos farm = helper.absolutePos(b.below());
                p.gameMode.useItemOn(p, level, mine, InteractionHand.MAIN_HAND,
                        new BlockHitResult(Vec3.atCenterOf(farm).add(0, 0.5, 0), Direction.UP, farm, false));
                helper.assertTrue(!helper.getBlockState(b).is(Blocks.CARROTS) && mine.getCount() == 2,
                        "player planted a vanilla carrot");
            } finally {
                p.discard();
            }
            helper.succeed();
        }, 0);
    }

    @GameTest(template = SmokeGameTests.FLOOR_3X3X3, batch = "wr162_soft", timeoutTicks = 100)
    public static void softLetsThePlayerPlantAVanillaCarrotThatStaysVanilla(GameTestHelper helper) {
        BlockPos a = new BlockPos(1, 2, 1);
        PlantFixtures.litFarmland(helper, a);
        PlantFixtures.whenLit(helper, List.of(a), () -> {
            ServerConfig.Mode old = ServerConfig.MODE.get();
            ServerConfig.MODE.set(ServerConfig.Mode.SOFT);
            ServerPlayer p = JournalGameTests.survival(helper, "wr162_soft");
            try {
                ItemStack mine = new ItemStack(Items.CARROT, 2);
                helper.assertTrue(use(p, mine, helper, a), "soft: the player could not plant a vanilla carrot");
                helper.assertTrue(mine.getCount() == 1, "carrot not spent");
                helper.assertTrue(growTick(helper, a), "soft: vanilla growth denied");
                helper.assertTrue(helper.getBlockState(a).is(Blocks.CARROTS), "soft: converted");
            } finally {
                ServerConfig.MODE.set(old);
                p.discard();
            }
            helper.succeed();
        }, 0);
    }

    @GameTest(template = SmokeGameTests.FLOOR_3X3X3, batch = "wr162_wild")
    public static void vanillaCarrotInANewChunkBecomesAWildPlant(GameTestHelper helper) {
        VanillaCrops.clearCaches();
        ServerLevel level = helper.getLevel();
        LevelChunk chunk = level.getChunkAt(helper.absolutePos(PlantFixtures.PLANT));
        helper.assertTrue(LineMarker.of(chunk).map(m -> m.has("carrot")).orElse(false),
                "a chunk generated with the carrot line has no marker: " + LineMarker.of(chunk).flatMap(LineMarker::lines));
        BlockPos ripe = new BlockPos(0, 2, 0);
        BlockPos young = new BlockPos(2, 2, 2);
        vanilla(helper, ripe, 7);
        vanilla(helper, young, 3);
        helper.assertTrue(!growTick(helper, ripe) && !growTick(helper, young), "vanilla growth not denied");
        for (BlockPos rel : new BlockPos[] {ripe, young}) {
            BlockState s = helper.getBlockState(rel);
            helper.assertTrue(s.is(ModBlocks.WILD_CARROT.get()), "not converted at " + rel + ": " + s);
            helper.assertTrue(!s.getValue(FieldPlantBlock.VILLAGE) && s.getValue(FieldPlantBlock.ACTIVE), "flags " + s);
            PlantBlockEntity be = (PlantBlockEntity) helper.getBlockEntity(rel);
            helper.assertTrue(be.live(level).isPresent(), "plant not realised");
            helper.assertTrue(be.data().passport().origin() == Passport.Origin.WILD, "not wild: " + be.data().passport());
            helper.assertTrue(!be.data().hasPlanter(), "a converted plant has a planter");
        }
        helper.assertTrue(FieldPlantBlock.stage(helper.getBlockState(ripe)) == FieldPlantBlock.HARVEST_STAGE, "ripe stage");
        helper.assertTrue(FieldPlantBlock.stage(helper.getBlockState(young)) == VanillaCrops.stageOf(3, 7)
                && VanillaCrops.stageOf(3, 7) == 1, "young stage");
        helper.succeed();
    }

    @GameTest(template = SmokeGameTests.FLOOR_3X3X3, batch = "wr162_village")
    public static void villageLandraceOnlyInAnOldChunkWithABell(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        LevelChunk chunk = level.getChunkAt(helper.absolutePos(PlantFixtures.PLANT));
        LineMarker marker = LineMarker.of(chunk).orElseThrow();
        Optional<List<String>> saved = marker.lines();
        BlockPos bell = new BlockPos(2, 2, 2);
        try {
            // old chunk (no marker) and a bell: a village landrace
            marker.set(null);
            VanillaCrops.clearCaches();
            helper.setBlock(bell, Blocks.BELL);
            helper.assertTrue(VanillaCrops.meetingNear(level, helper.absolutePos(PlantFixtures.PLANT)), "bell POI");
            BlockPos a = new BlockPos(0, 2, 0);
            vanilla(helper, a, 7);
            growTick(helper, a);
            BlockState s = helper.getBlockState(a);
            helper.assertTrue(s.is(ModBlocks.WILD_CARROT.get()) && s.getValue(FieldPlantBlock.VILLAGE), "not village: " + s);
            PlantBlockEntity be = (PlantBlockEntity) helper.getBlockEntity(a);
            helper.assertTrue(be.data().passport().origin() == Passport.Origin.VILLAGE
                    && be.data().passport().landrace().equals("wildroots:eastern_carrot"), "passport " + be.data().passport());
            helper.assertTrue(be.live(level).isPresent() && be.data().genome() != null, "no village genome");

            // a chunk generated with the line and a bell: wild
            marker.set(List.of("carrot"));
            VanillaCrops.clearCaches();
            BlockPos b = new BlockPos(0, 2, 2);
            vanilla(helper, b, 7);
            growTick(helper, b);
            helper.assertTrue(!helper.getBlockState(b).getValue(FieldPlantBlock.VILLAGE), "village in a marked chunk");

            // an old chunk without a bell, a composter and a bed of a player: wild
            marker.set(null);
            helper.setBlock(bell, Blocks.AIR);
            helper.setBlock(new BlockPos(2, 2, 0), Blocks.COMPOSTER);
            helper.setBlock(new BlockPos(2, 2, 1), Blocks.RED_BED);
            VanillaCrops.clearCaches();
            helper.assertTrue(!VanillaCrops.meetingNear(level, helper.absolutePos(PlantFixtures.PLANT)),
                    "another meeting POI within 32 blocks of the test");
            BlockPos c = new BlockPos(1, 2, 1);
            vanilla(helper, c, 7);
            growTick(helper, c);
            BlockState cs = helper.getBlockState(c);
            helper.assertTrue(cs.is(ModBlocks.WILD_CARROT.get()) && !cs.getValue(FieldPlantBlock.VILLAGE),
                    "village without a bell: " + cs);
        } finally {
            marker.set(saved.orElse(null));
            helper.setBlock(bell, Blocks.AIR);
            VanillaCrops.clearCaches();
        }
        helper.succeed();
    }

    @GameTest(template = SmokeGameTests.FLOOR_3X3X3, batch = "wr162_budget", timeoutTicks = 40)
    public static void atMostSixteenConversionsPerTick(GameTestHelper helper) {
        VanillaCrops.clearCaches();
        List<BlockPos> cells = new ArrayList<>();
        // a checkerboard in 4 layers (20 cells): no carrot is a direct neighbour of another, so the
        // shape updates of a conversion never reach a carrot in the dark test area
        for (int layer = 0; layer < 4; layer++) {
            for (int x = 0; x < 3; x++) {
                for (int z = 0; z < 3; z++) {
                    if ((x + z) % 2 != 0) {
                        continue;
                    }
                    BlockPos rel = new BlockPos(x, 2 + 2 * layer, z);
                    PlantFixtures.farmland(helper, rel);
                    cells.add(rel);
                }
            }
        }
        // all farmland first: its shape updates would pop a carrot already set in the dark
        for (BlockPos rel : cells) {
            helper.getLevel().setBlock(helper.absolutePos(rel), carrots(7), 2);
        }
        for (BlockPos rel : cells) {
            growTick(helper, rel);
        }
        long converted = cells.stream().filter(r -> helper.getBlockState(r).is(ModBlocks.WILD_CARROT.get())).count();
        long waiting = cells.stream().filter(r -> helper.getBlockState(r).is(Blocks.CARROTS)).count();
        helper.assertTrue(converted == VanillaCrops.MAX_PER_TICK && waiting == cells.size() - converted,
                "one tick converted " + converted + ", waiting " + waiting);
        helper.runAfterDelay(1, () -> {
            for (BlockPos rel : cells) {
                if (helper.getBlockState(rel).is(Blocks.CARROTS)) {
                    growTick(helper, rel);
                }
            }
            helper.assertTrue(cells.stream().allMatch(r -> helper.getBlockState(r).is(ModBlocks.WILD_CARROT.get())),
                    "the rest did not convert on the next tick");
            helper.succeed();
        });
    }
}
