package io.github.slugblue.wildroots.gametest.perf;

import io.github.slugblue.wildroots.WildRoots;
import io.github.slugblue.wildroots.plant.FieldPlantBlock;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * WR-028 (S9), ported in WR-195: plain counters (server thread only) to check the bench
 * load against architecture §10.5. {@code CropGrowEvent.Post} is fired by vanilla
 * {@code CropBlock} and by {@code GrowthLogic.randomTick} of the real plant; only the
 * bench layer counts, and real-plant stage changes and entries into seeds are split out.
 */
@Mod.EventBusSubscriber(modid = WildRoots.MOD_ID)
public final class BenchCounters {

    static long cropGrowPost;
    static long realStageChanges;
    /** Real plants that reached stage 5 (seed set ran on 4 → 5). */
    static long realSeedSets;
    /** Crop growth outside the bench layer (village farms of the world). */
    static long cropGrowPostOutside;

    private BenchCounters() {
    }

    static void reset() {
        cropGrowPost = 0;
        realStageChanges = 0;
        realSeedSets = 0;
        cropGrowPostOutside = 0;
    }

    static String snapshot() {
        return "cropGrowPost=" + cropGrowPost + " (outsideLayer=" + cropGrowPostOutside + ") realStageChanges="
                + realStageChanges + " realSeedSets=" + realSeedSets;
    }

    @SubscribeEvent
    public static void onCropGrowPost(BlockEvent.CropGrowEvent.Post event) {
        if (event.getPos().getY() != BenchDriver.Y_PLANT) {
            cropGrowPostOutside++;
            return;
        }
        cropGrowPost++;
        if (event.getState().getBlock() instanceof FieldPlantBlock) {
            realStageChanges++;
            if (event.getLevel().getBlockState(event.getPos()).getBlock() instanceof FieldPlantBlock
                    && FieldPlantBlock.stage(event.getLevel().getBlockState(event.getPos())) == FieldPlantBlock.SEED_STAGE) {
                realSeedSets++;
            }
        }
    }
}
