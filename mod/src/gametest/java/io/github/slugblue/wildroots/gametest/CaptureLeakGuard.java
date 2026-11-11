package io.github.slugblue.wildroots.gametest;

import io.github.slugblue.wildroots.WildRoots;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Review 1 of batch 6a (minor 7): Forge's block snapshot capture
 * ({@code Level#captureBlockSnapshots}) is switched on only inside
 * {@code ForgeHooks.onPlaceItemIntoWorld}, never across ticks. A test that
 * throws out of an item's {@code useOn} leaves it on for the level: block
 * updates are deferred for every later test and stale snapshots are posted
 * with the next placement. At the start of every server tick of the GameTest
 * server this guard resets such a leak and fails the run loudly.
 */
@Mod.EventBusSubscriber(modid = WildRoots.MOD_ID)
public final class CaptureLeakGuard {

    private CaptureLeakGuard() {
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.START || event.getServer() == null) {
            return;
        }
        for (ServerLevel level : event.getServer().getAllLevels()) {
            check(level);
        }
    }

    /** Resets a leaked capture of {@code level} and throws; a no-op when the capture is off and empty. */
    static void check(ServerLevel level) {
        if (level.captureBlockSnapshots || !level.capturedBlockSnapshots.isEmpty()) {
            int stale = level.capturedBlockSnapshots.size();
            level.captureBlockSnapshots = false;
            level.capturedBlockSnapshots.clear();
            throw new IllegalStateException("a GameTest left Forge block snapshot capture on in " + level.dimension()
                    + " (" + stale + " stale snapshots); reset it where it catches an exception out of useOn");
        }
    }
}
