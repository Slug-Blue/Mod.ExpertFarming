package io.github.slugblue.wildroots.gametest;

import io.github.slugblue.wildroots.WildRoots;
import io.github.slugblue.wildroots.core.registry.ModBlocks;
import io.github.slugblue.wildroots.plant.FieldPlantBlock;
import io.github.slugblue.wildroots.plant.PlantBlockEntity;
import io.github.slugblue.wildroots.pollination.BloomIndex;
import io.github.slugblue.wildroots.pollination.BloomTracker;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraftforge.event.level.ChunkEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/** WR-140: bloom index lifecycle (docs/05-architecture.md §10.2; ADR-0008; GDD §4.1). */
@GameTestHolder(WildRoots.MOD_ID)
@PrefixGameTestTemplate(false)
public final class BloomIndexGameTests {

    static final String FLOOR = "gametest/floor_3x3x3";
    static final String SP = PlantFixtures.SP;

    private BloomIndexGameTests() {
    }

    static boolean indexed(GameTestHelper helper, BlockPos rel) {
        return BloomTracker.contains(helper.getLevel(), helper.absolutePos(rel), SP);
    }

    static void setStage(GameTestHelper helper, BlockPos rel, int stage) {
        helper.setBlock(rel, helper.getBlockState(rel).setValue(FieldPlantBlock.STAGE, stage));
    }

    /** Entering flowering registers the plant, leaving it (seeds) removes it; the block entity stays. */
    @GameTest(template = FLOOR)
    public static void enteringAndLeavingBloom(GameTestHelper helper) {
        BlockPos rel = PlantFixtures.PLANT;
        PlantBlockEntity be = PlantFixtures.plant(PlantFixtures.place(helper, rel, FieldPlantBlock.HARVEST_STAGE), 1);
        helper.assertFalse(indexed(helper, rel), "stage 3 is not flowering");
        setStage(helper, rel, FieldPlantBlock.FLOWER_STAGE);
        helper.assertTrue(indexed(helper, rel), "flowering plant not in the index");
        setStage(helper, rel, FieldPlantBlock.SEED_STAGE);
        helper.assertFalse(indexed(helper, rel), "plant left flowering but stays in the index");
        helper.assertTrue(helper.getBlockEntity(rel) == be, "stage change replaced the block entity");
        helper.succeed();
    }

    /** Growth through GrowthLogic (the real path) also enters the index. */
    @GameTest(template = FLOOR)
    public static void growthIntoBloomRegisters(GameTestHelper helper) {
        BlockPos rel = PlantFixtures.PLANT;
        PlantFixtures.mother(PlantFixtures.place(helper, rel, FieldPlantBlock.HARVEST_STAGE), 2);
        ModBlocks.WILD_CARROT.get().growCrops(helper.getLevel(), helper.absolutePos(rel), helper.getBlockState(rel));
        helper.assertTrue(PlantFixtures.stage(helper, rel) == FieldPlantBlock.FLOWER_STAGE, "did not grow");
        helper.assertTrue(indexed(helper, rel), "grown into flowering, not indexed");
        helper.succeed();
    }

    /** Breaking the block ({@code setRemoved}) removes the entry. */
    @GameTest(template = FLOOR)
    public static void brokenPlantLeavesIndex(GameTestHelper helper) {
        BlockPos rel = PlantFixtures.PLANT;
        PlantFixtures.plant(PlantFixtures.place(helper, rel, FieldPlantBlock.FLOWER_STAGE), 3);
        helper.assertTrue(indexed(helper, rel), "flowering plant not indexed");
        helper.getLevel().destroyBlock(helper.absolutePos(rel), false);
        helper.assertFalse(indexed(helper, rel), "broken plant still indexed");
        helper.succeed();
    }

    /**
     * Chunk unload: the block entity leaves ({@code onChunkUnloaded}) and the
     * chunk entry goes ({@code ChunkEvent.Unload}); reloading the chunk
     * registers the plant again from {@code onLoad} (nothing was saved).
     */
    @GameTest(template = FLOOR)
    public static void chunkUnloadAndReload(GameTestHelper helper) {
        BlockPos rel = PlantFixtures.PLANT;
        PlantBlockEntity be = PlantFixtures.plant(PlantFixtures.place(helper, rel, FieldPlantBlock.FLOWER_STAGE), 4);
        ServerLevel level = helper.getLevel();
        BlockPos abs = helper.absolutePos(rel);
        be.onChunkUnloaded();
        helper.assertFalse(indexed(helper, rel), "onChunkUnloaded kept the entry");
        BloomTracker.set(level, abs, SP, true);
        LevelChunk chunk = level.getChunkAt(abs);
        BloomTracker.onChunkUnload(new ChunkEvent.Unload(chunk));
        helper.assertFalse(indexed(helper, rel), "ChunkEvent.Unload kept the entry");
        be.onLoad();
        helper.assertTrue(indexed(helper, rel), "onLoad did not restore the entry");
        helper.succeed();
    }

    /** Review m3: a deferred {@code onLoad} after the removal does not put the plant back. */
    @GameTest(template = FLOOR)
    public static void onLoadAfterRemovalAddsNothing(GameTestHelper helper) {
        BlockPos rel = PlantFixtures.PLANT;
        PlantBlockEntity be = PlantFixtures.plant(PlantFixtures.place(helper, rel, FieldPlantBlock.FLOWER_STAGE), 6);
        helper.getLevel().destroyBlock(helper.absolutePos(rel), false);
        helper.assertTrue(be.isRemoved(), "block entity not removed");
        be.onLoad();
        helper.assertFalse(indexed(helper, rel), "onLoad of a removed block entity re-added it");
        helper.succeed();
    }

    /** Level unload drops the whole index of the level (own batch: it empties the index for everyone). */
    @GameTest(template = FLOOR, batch = "wr140_level_unload")
    public static void levelUnloadDropsIndex(GameTestHelper helper) {
        BlockPos rel = PlantFixtures.PLANT;
        PlantFixtures.plant(PlantFixtures.place(helper, rel, FieldPlantBlock.FLOWER_STAGE), 5);
        ServerLevel level = helper.getLevel();
        helper.assertTrue(BloomIndex.peek(level) != null && indexed(helper, rel), "not indexed");
        BloomTracker.onLevelUnload(new LevelEvent.Unload(level));
        helper.assertTrue(BloomIndex.peek(level) == null, "level index survived the level unload");
        // the level goes on in the test server: the next flowering plant starts a fresh index
        ((PlantBlockEntity) helper.getBlockEntity(rel)).onLoad();
        helper.assertTrue(indexed(helper, rel), "fresh index after unload did not take the plant");
        helper.succeed();
    }
}
