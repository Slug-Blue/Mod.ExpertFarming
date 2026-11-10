package io.github.slugblue.wildroots.gametest;

import io.github.slugblue.wildroots.WildRoots;
import io.github.slugblue.wildroots.genetics.api.Classification;
import io.github.slugblue.wildroots.journal.JournalData;
import io.github.slugblue.wildroots.journal.Journals;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * Review 1, m5 and test gaps: only a real harvest counts. A creative break,
 * a fake player and a cancelled break give no journal, no entry and no
 * counter. Stagnation goes through the real path: harvests of a named line
 * → {@code LineProgress.observe} → the hint.
 */
@GameTestHolder(WildRoots.MOD_ID)
@PrefixGameTestTemplate(false)
public final class JournalBreakGameTests {

    private JournalBreakGameTests() {
    }

    static void assertUntouched(GameTestHelper helper, ServerPlayer p, String what) {
        JournalData j = JournalGameTests.journal(p);
        helper.assertTrue(JournalGameTests.journals(p) == 0, what + ": a journal was given");
        helper.assertTrue(j.entries().isEmpty() && j.counters().isEmpty() && !j.given(),
                what + ": entries " + j.entries() + " counters " + j.counters());
    }

    @GameTest(template = SmokeGameTests.FLOOR_3X3X3, batch = "wr18x_journal")
    public static void creativeBreakIsNoHarvest(GameTestHelper helper) {
        JournalGameTests.Recorder rec = new JournalGameTests.Recorder();
        ServerPlayer p = JournalGameTests.survival(helper, "wr18x_creative");
        try {
            p.gameMode.changeGameModeForPlayer(GameType.CREATIVE);
            JournalGameTests.wildMature(helper, PlantFixtures.PLANT, 31L);
            helper.assertTrue(p.gameMode.destroyBlock(helper.absolutePos(PlantFixtures.PLANT)), "not broken");
            assertUntouched(helper, p, "creative");
        } finally {
            rec.close();
            p.discard();
        }
        helper.succeed();
    }

    @GameTest(template = SmokeGameTests.FLOOR_3X3X3, batch = "wr18x_journal")
    public static void fakePlayerBreakIsNoHarvest(GameTestHelper helper) {
        JournalGameTests.Recorder rec = new JournalGameTests.Recorder();
        try {
            FakePlayer fake = FakePlayerFactory.getMinecraft(helper.getLevel());
            JournalGameTests.wildMature(helper, PlantFixtures.PLANT, 32L);
            BlockPos abs = helper.absolutePos(PlantFixtures.PLANT);
            MinecraftForge.EVENT_BUS.post(new BlockEvent.BreakEvent(helper.getLevel(), abs, helper.getLevel().getBlockState(abs), fake));
            Journals.of(fake).ifPresent(j -> helper.assertTrue(j.entries().isEmpty() && !j.given(),
                    "a fake player harvested: " + j.entries()));
            helper.assertTrue(rec.syncs.isEmpty(), "journal traffic for a fake player");
        } finally {
            rec.close();
        }
        helper.succeed();
    }

    @GameTest(template = SmokeGameTests.FLOOR_3X3X3, batch = "wr18x_journal")
    public static void cancelledBreakIsNoHarvest(GameTestHelper helper) {
        JournalGameTests.Recorder rec = new JournalGameTests.Recorder();
        ServerPlayer p = JournalGameTests.survival(helper, "wr18x_cancelled");
        Consumer<BlockEvent.BreakEvent> cancel = e -> {
            if (e.getPlayer() == p) {
                e.setCanceled(true);
            }
        };
        MinecraftForge.EVENT_BUS.addListener(EventPriority.HIGHEST, false, BlockEvent.BreakEvent.class, cancel);
        try {
            JournalGameTests.wildMature(helper, PlantFixtures.PLANT, 33L);
            helper.assertTrue(!p.gameMode.destroyBlock(helper.absolutePos(PlantFixtures.PLANT)), "broken despite the cancel");
            assertUntouched(helper, p, "cancelled");
        } finally {
            MinecraftForge.EVENT_BUS.unregister(cancel);
            rec.close();
            p.discard();
        }
        helper.succeed();
    }

    /**
     * Stagnation by real harvests: the same plant (same score) of a named
     * line harvested in generations 1–5; generations 2, 3 and 4 do not beat
     * generation 1, so the harvest of generation 5 (closing generation 4,
     * the third stalled one) unlocks the hint, and not earlier.
     */
    @GameTest(template = SmokeGameTests.FLOOR_3X3X3, batch = "wr18x_journal")
    public static void stagnationThroughRealHarvests(GameTestHelper helper) {
        JournalGameTests.Recorder rec = new JournalGameTests.Recorder();
        ServerPlayer p = JournalGameTests.survival(helper, "wr18x_stag");
        try {
            BlockPos abs = helper.absolutePos(PlantFixtures.PLANT);
            for (int gen = 1; gen <= 5; gen++) {
                boolean before = stagnated(p);
                helper.assertTrue(!before, "stagnation before the harvest of generation " + gen);
                JournalHintGameTests.ownCarrot(helper, p, 820, 0, Classification.WILD, "Stillwater", gen);
                helper.assertTrue(p.gameMode.destroyBlock(abs), "generation " + gen + " not harvested");
            }
            helper.assertTrue(stagnated(p), "no stagnation after three stalled generations: "
                    + JournalGameTests.journal(p).counters());
            helper.assertTrue(JournalGameTests.journal(p).entries().stream()
                    .filter(e -> e.id().startsWith("hint.stagnation.")).findFirst().orElseThrow()
                    .args().equals(java.util.List.of("Stillwater")), "line name not in the hint");
        } finally {
            rec.close();
            p.discard();
        }
        helper.succeed();
    }

    static boolean stagnated(ServerPlayer p) {
        return JournalGameTests.journal(p).entries().stream().anyMatch(e -> e.id().startsWith("hint.stagnation."));
    }
}
