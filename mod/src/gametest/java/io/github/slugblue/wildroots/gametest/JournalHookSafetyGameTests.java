package io.github.slugblue.wildroots.gametest;

import io.github.slugblue.wildroots.WildRoots;
import io.github.slugblue.wildroots.journal.JournalSyncPacket;
import io.github.slugblue.wildroots.journal.Journals;
import io.github.slugblue.wildroots.journal.ToastPacket;
import io.github.slugblue.wildroots.plant.PlantBlockEntity;
import io.github.slugblue.wildroots.pollination.BeePollen;
import io.github.slugblue.wildroots.pollination.PollinationService;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Bee;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * Review 1, M3: a journal hook that throws never changes the main action.
 * Planting still consumes exactly one seed; a bee visit still leaves the
 * plant marked, the pollen collected and the nectar given.
 */
@GameTestHolder(WildRoots.MOD_ID)
@PrefixGameTestTemplate(false)
public final class JournalHookSafetyGameTests {

    private JournalHookSafetyGameTests() {
    }

    /** A sender whose every packet throws: any hint that fires fails. */
    static Journals.Sender throwing() {
        return new Journals.Sender() {
            @Override
            public void sync(ServerPlayer player, JournalSyncPacket packet) {
                throw new IllegalStateException("test: journal sync fails");
            }

            @Override
            public void toast(ServerPlayer player, ToastPacket packet) {
                throw new IllegalStateException("test: toast fails");
            }
        };
    }

    @GameTest(template = SmokeGameTests.FLOOR_3X3X3, batch = "wr18x_journal")
    public static void aThrowingHintDuringPlantingStillConsumesTheSeed(GameTestHelper helper) {
        Journals.Sender old = Journals.setSender(throwing());
        ServerPlayer p = JournalGameTests.survival(helper, "wr18x_plant_throw");
        try {
            PlantFixtures.farmland(helper, PlantFixtures.PLANT);
            ItemStack lot = PlantingGameTests.lot(helper.getLevel(), 5);
            // strict mode (dev, GameTest) rethrows after isolating; production returns CONSUME
            try {
                helper.assertTrue(PlantingGameTests.use(helper, p, lot, PlantFixtures.PLANT).consumesAction(),
                        "planting failed because of the journal");
            } catch (IllegalStateException e) {
                helper.assertTrue(io.github.slugblue.wildroots.core.safety.Guarded.shared().strict(), "thrown: " + e);
            }
            helper.assertTrue(lot.getCount() == 4, "seeds left " + lot.getCount() + ", expected 4");
            helper.assertTrue(PlantFixtures.stage(helper, PlantFixtures.PLANT) == 0, "no plant");
        } finally {
            Journals.setSender(old);
            p.discard();
        }
        helper.succeed();
    }

    @GameTest(template = SeedSetGameTests.FIELD, setupTicks = SeedSetGameTests.SETUP, batch = "wr18x_journal_bee")
    public static void aThrowingHintDuringABeeVisitLeavesPollenAndNectarConsistent(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer planter = JournalGameTests.survival(helper, "wr18x_bee_throw");
        Journals.Sender old = Journals.setSender(throwing());
        var oldOnline = Journals.setExtraOnline(id -> planter);
        try {
            BlockPos a = new BlockPos(3, 2, 3);
            PlantBlockEntity pa = SeedSetGameTests.flowering(helper, a, 451);
            Bee bee = helper.spawn(EntityType.BEE, new BlockPos(4, 4, 4));
            bee.setNoAi(true);
            BeePollen pollen = BeeGameTests.pollen(bee);
            BlockPos aa = helper.absolutePos(a);
            try {
                helper.assertTrue(PollinationService.beeVisit(level, bee, aa) == PollinationService.Visit.VISITED,
                        "the visit failed because of the journal");
            } catch (IllegalStateException e) {
                helper.assertTrue(io.github.slugblue.wildroots.core.safety.Guarded.shared().strict(), "thrown: " + e);
            }
            helper.assertTrue(bee.hasNectar(), "no nectar");
            helper.assertTrue(PlantFixtures.SP.equals(pollen.species()) && pollen.source() == aa.asLong(),
                    "pollen not collected");
            helper.assertTrue(pa.data().pending() != null && pa.data().pending().bees(), "plant not marked bee-visited");
            bee.discard();
        } finally {
            Journals.setExtraOnline(oldOnline);
            Journals.setSender(old);
            planter.discard();
        }
        helper.succeed();
    }
}
