package io.github.slugblue.wildroots.gametest;

import io.github.slugblue.wildroots.WildRoots;
import io.github.slugblue.wildroots.core.safety.Guarded;
import io.github.slugblue.wildroots.core.safety.LoadedLevelAccess;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/** WR-121: LoadedLevelAccess never loads chunks (SEC-32); Guarded isolates handler exceptions (SEC-34). */
@GameTestHolder(WildRoots.MOD_ID)
@PrefixGameTestTemplate(false)
public final class SafetyGameTests {

    private SafetyGameTests() {
    }

    @GameTest(template = SmokeGameTests.FLOOR_3X3X3)
    public static void loadedLevelAccessDoesNotLoadChunks(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos far = helper.absolutePos(BlockPos.ZERO).offset(1_600_000, 0, 1_600_000);
        helper.assertFalse(level.getChunkSource().getChunkNow(far.getX() >> 4, far.getZ() >> 4) != null,
                "the far chunk is unexpectedly loaded");
        int before = level.getChunkSource().getLoadedChunksCount();
        helper.assertTrue(LoadedLevelAccess.blockState(level, far).isEmpty(), "block state of an unloaded chunk");
        helper.assertTrue(LoadedLevelAccess.blockEntity(level, far).isEmpty(), "block entity of an unloaded chunk");
        helper.assertFalse(LoadedLevelAccess.isLoaded(level, far), "unloaded chunk reported as loaded");
        int after = level.getChunkSource().getLoadedChunksCount();
        helper.assertTrue(before == after, "loaded chunk count changed: " + before + " -> " + after);
        helper.assertTrue(level.getChunkSource().getChunkNow(far.getX() >> 4, far.getZ() >> 4) == null,
                "the far chunk got loaded");

        BlockPos floor = helper.absolutePos(new BlockPos(1, 1, 1));
        helper.assertTrue(LoadedLevelAccess.blockState(level, floor).filter(s -> !s.isAir()).isPresent(),
                "floor of the test structure is not readable");
        helper.assertTrue(LoadedLevelAccess.blockState(level, floor.above())
                .filter(s -> s.is(Blocks.AIR)).isPresent(), "air above the floor is not readable");
        helper.succeed();
    }

    /** Event fired by the test; its handler throws inside {@link Guarded}. */
    public static final class ProbeEvent extends Event {
        final Guarded guard;
        int handled;

        public ProbeEvent(Guarded guard) {
            this.guard = guard;
        }
    }

    private static boolean listening;

    private static void onProbe(ProbeEvent e) {
        e.guard.execute("probe handler", "gametest", () -> {
            e.handled++;
            throw new IllegalStateException("handler bug");
        }, null);
    }

    @GameTest(template = SmokeGameTests.FLOOR_3X3X3)
    public static void guardedHandlerExceptionDoesNotCrash(GameTestHelper helper) {
        if (!listening) {
            MinecraftForge.EVENT_BUS.addListener(SafetyGameTests::onProbe);
            listening = true;
        }
        List<String> lines = new ArrayList<>();
        Guarded lenient = new Guarded(false, System::currentTimeMillis, lines::add);
        ProbeEvent first = new ProbeEvent(lenient);
        MinecraftForge.EVENT_BUS.post(first);
        MinecraftForge.EVENT_BUS.post(new ProbeEvent(lenient));
        helper.assertTrue(first.handled == 1, "handler did not run");
        helper.assertTrue(lines.size() == 1, "expected one log line for two failures, got " + lines);

        Guarded strict = new Guarded(true, System::currentTimeMillis, s -> { });
        boolean rethrown = false;
        try {
            MinecraftForge.EVENT_BUS.post(new ProbeEvent(strict));
        } catch (IllegalStateException e) {
            rethrown = "handler bug".equals(e.getMessage());
        }
        helper.assertTrue(rethrown, "strict mode did not rethrow");
        helper.assertTrue(Guarded.shared().strict(), "GameTest runs with -Dwildroots.strictErrors=true");
        helper.succeed();
    }
}
