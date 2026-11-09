package io.github.slugblue.wildroots.gametest;

import io.github.slugblue.wildroots.WildRoots;
import io.github.slugblue.wildroots.data.Definitions;
import io.github.slugblue.wildroots.genetics.api.Classification;
import io.github.slugblue.wildroots.genetics.api.CultivarId;
import io.github.slugblue.wildroots.journal.CultivarBred;
import io.github.slugblue.wildroots.journal.HintTriggers;
import io.github.slugblue.wildroots.journal.JournalData;
import io.github.slugblue.wildroots.journal.JournalTriggers;
import io.github.slugblue.wildroots.journal.Journals;
import io.github.slugblue.wildroots.journal.ToastPacket;
import io.github.slugblue.wildroots.plant.FieldPlantBlock;
import io.github.slugblue.wildroots.plant.Passport;
import io.github.slugblue.wildroots.plant.PlantBlockEntity;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * WR-182: hints fire by their triggers once per player, the "New entry"
 * toast at most once in 30 s while the rest accumulate silently, hints of
 * later waves never fire, the per-line repeat rule, and the first-cultivar
 * toast to the player who planted the plant (GDD §6.2 item 4).
 */
@GameTestHolder(WildRoots.MOD_ID)
@PrefixGameTestTemplate(false)
public final class JournalHintGameTests {

    private JournalHintGameTests() {
    }

    record Toasted(ServerPlayer player, ToastPacket toast) {
    }

    static final class Rec {
        final List<Toasted> toasts = new ArrayList<>();
        final Journals.Sender old = Journals.setSender(Journals.recording((p, s) -> { },
                (p, t) -> toasts.add(new Toasted(p, t))));

        void close() {
            Journals.setSender(old);
        }
    }

    @GameTest(template = SmokeGameTests.FLOOR_3X3X3, batch = "wr18x_journal")
    public static void newEntryToastAtMostOnceIn30Seconds(GameTestHelper helper) {
        Rec rec = new Rec();
        ServerPlayer p = JournalGameTests.survival(helper, "wr182_toast");
        try {
            long t = 1000;
            for (String id : List.of("hint.bees", "hint.inbreeding", "hint.bitter")) {
                helper.assertTrue(Journals.unlock(p, id, List.of(), List.of(), t) == JournalData.Unlock.NEW, id);
            }
            helper.assertTrue(rec.toasts.size() == 1, "three entries in one tick gave " + rec.toasts.size() + " toasts");
            Journals.unlock(p, "hint.mutation", List.of(), List.of(), t + JournalData.TOAST_INTERVAL_TICKS - 1);
            helper.assertTrue(rec.toasts.size() == 1, "a toast before 30 s");
            helper.assertTrue(JournalGameTests.journal(p).has("hint.mutation"), "the silent entry was not kept");
            Journals.unlock(p, "hint.bone_meal", List.of(), List.of(), t + JournalData.TOAST_INTERVAL_TICKS);
            helper.assertTrue(rec.toasts.size() == 2, "no toast after 30 s");
            helper.assertTrue(rec.toasts.get(0).toast().title().key().equals(Journals.NEW_ENTRY_KEY), "toast key");
            // the same hint again: once per player, no toast
            Journals.unlock(p, "hint.bees", List.of(), List.of(), t + 10 * JournalData.TOAST_INTERVAL_TICKS);
            helper.assertTrue(rec.toasts.size() == 2, "a repeated hint toasted");
        } finally {
            rec.close();
            p.discard();
        }
        helper.succeed();
    }

    @GameTest(template = SmokeGameTests.FLOOR_3X3X3, batch = "wr18x_journal")
    public static void laterWaveHintsDoNotFireInAWave1Build(GameTestHelper helper) {
        Rec rec = new Rec();
        ServerPlayer p = JournalGameTests.survival(helper, "wr182_wave");
        try {
            for (String id : List.of("hint.shattering", "hint.brush_for_wheat", "hint.cabbage_roads", "hint.toxic_tomato",
                    "hint.hand_pollination", "hint.genotype_lab", "hint.clone", "hint.red_flesh", "hint.village_seeds")) {
                helper.assertTrue(Journals.unlock(p, id) == JournalData.Unlock.NONE, id + " fired in wave 1");
            }
            helper.assertTrue(JournalGameTests.journal(p).entries().isEmpty(), "late entries stored");
            helper.assertTrue(rec.toasts.isEmpty(), "late entries toasted");
            helper.assertTrue(Journals.unlock(p, "hint.yellow_root") == JournalData.Unlock.NEW, "carrot hint refused");
        } finally {
            rec.close();
            p.discard();
        }
        helper.succeed();
    }

    /** An own carrot with root colour {@code variant} at harvest ripeness. */
    static PlantBlockEntity ownCarrot(GameTestHelper helper, ServerPlayer owner, long key, int variant,
                                      Classification status, String line, int gen) {
        PlantBlockEntity be = PlantFixtures.place(helper, PlantFixtures.PLANT, 3);
        be.init(Definitions.current().genetics(), new PlantBlockEntity.Init(PlantFixtures.SP, PlantFixtures.genome(key),
                PlantFixtures.noise(key + 1), Passport.PLAYER, owner.getUUID(), status, line, gen, 0, 0));
        helper.setBlock(PlantFixtures.PLANT, helper.getBlockState(PlantFixtures.PLANT)
                .setValue(FieldPlantBlock.VARIANT, variant));
        return (PlantBlockEntity) helper.getBlockEntity(PlantFixtures.PLANT);
    }

    @GameTest(template = SmokeGameTests.FLOOR_3X3X3, batch = "wr18x_journal")
    public static void carrotHintsFireOncePerPlayer(GameTestHelper helper) {
        Rec rec = new Rec();
        ServerPlayer a = JournalGameTests.survival(helper, "wr182_a");
        ServerPlayer b = JournalGameTests.survival(helper, "wr182_b");
        try {
            BlockPos abs = helper.absolutePos(PlantFixtures.PLANT);
            for (int i = 0; i < 2; i++) {
                ownCarrot(helper, a, 300 + i, 1, Classification.WILD, null, 0);
                BlockState s = helper.getBlockState(PlantFixtures.PLANT);
                JournalTriggers.harvested(helper.getLevel(), abs, s, a);
                JournalTriggers.harvested(helper.getLevel(), abs, s, b);
            }
            for (ServerPlayer p : List.of(a, b)) {
                JournalData j = JournalGameTests.journal(p);
                helper.assertTrue(j.has("hint.yellow_root") && j.has("hint.mother_plant") && j.has("species.wild_carrot"),
                        p.getGameProfile().getName() + " entries " + j.entries());
                helper.assertTrue(!j.has("hint.purple_root") && !j.has("hint.orange_root"), "wrong colour hint");
            }
            long ids = JournalGameTests.journal(a).entries().stream().filter(e -> e.id().equals("hint.yellow_root")).count();
            helper.assertTrue(ids == 1, "yellow_root entries " + ids);
            helper.assertTrue(rec.toasts.stream().filter(t -> t.player() == a).count() == 1,
                    "toasts for a: " + rec.toasts.size());
            ownCarrot(helper, a, 310, 3, Classification.WILD, null, 0);
            JournalTriggers.harvested(helper.getLevel(), abs, helper.getBlockState(PlantFixtures.PLANT), a);
            helper.assertTrue(JournalGameTests.journal(a).has("hint.purple_root"), "no purple_root");
        } finally {
            rec.close();
            a.discard();
            b.discard();
        }
        helper.succeed();
    }

    @GameTest(template = SmokeGameTests.FLOOR_3X3X3, batch = "wr18x_journal")
    public static void stagnationOncePerLineRepeatsAfterThreeGenerations(GameTestHelper helper) {
        Rec rec = new Rec();
        ServerPlayer p = JournalGameTests.survival(helper, "wr182_line");
        try {
            helper.assertTrue(Journals.unlockForLine(p, "stagnation", "Рыжик", 5), "first stagnation refused");
            helper.assertTrue(!Journals.unlockForLine(p, "stagnation", "Рыжик", 6), "repeat after 1 generation");
            helper.assertTrue(!Journals.unlockForLine(p, "stagnation", "Рыжик", 7), "repeat after 2 generations");
            helper.assertTrue(Journals.unlockForLine(p, "stagnation", "Другая", 6), "another line refused");
            JournalData j = JournalGameTests.journal(p);
            String id = j.entries().stream().filter(e -> e.id().startsWith("hint.stagnation.")
                    && e.args().equals(List.of("Рыжик"))).findFirst().orElseThrow().id();
            j.apply(io.github.slugblue.wildroots.core.Ids.mod(id), io.github.slugblue.wildroots.network.c2s.JournalActionPacket.MARK_READ);
            helper.assertTrue(Journals.unlockForLine(p, "stagnation", "Рыжик", 8), "no repeat after 3 generations");
            helper.assertTrue(j.entry(id).orElseThrow().is(JournalData.NEW), "the repeat is not marked new");
            helper.assertTrue(!Journals.unlockForLine(p, "bees", "Рыжик", 20), "a per-player hint fired per line");
        } finally {
            rec.close();
            p.discard();
        }
        helper.succeed();
    }

    @GameTest(template = SmokeGameTests.FLOOR_3X3X3, batch = "wr18x_journal")
    public static void firstCultivarToastGoesToThePlanter(GameTestHelper helper) {
        Rec rec = new Rec();
        ServerPlayer planter = JournalGameTests.survival(helper, "wr182_planter");
        ServerPlayer other = JournalGameTests.survival(helper, "wr182_other");
        try {
            Classification landrace = new Classification(Optional.of(CultivarId.of("wildroots:eastern_carrot")), 1, false);
            ownCarrot(helper, planter, 400, 1, landrace, "Рыжик", 4);
            BlockPos abs = helper.absolutePos(PlantFixtures.PLANT);
            HintTriggers.planted(helper.getLevel(), abs, planter);
            helper.assertTrue(rec.toasts.stream().anyMatch(t -> t.player() == planter
                    && t.toast().title().key().equals(CultivarBred.LANDRACE_TITLE)), "no landrace toast: " + rec.toasts);
            JournalData j = JournalGameTests.journal(planter);
            helper.assertTrue(j.has("cultivar.eastern_carrot") && j.has("hint.first_landrace") && j.has("hint.plant_together"),
                    "entries " + j.entries());
            helper.assertTrue(j.entry("cultivar.eastern_carrot").orElseThrow().rows().stream()
                    .anyMatch(r -> r.key().equals("wildroots.journal.cultivar.line") && r.args().get(0).equals("Рыжик")),
                    "no line row");
            // someone else harvesting the planter's landrace gets no bred toast
            JournalTriggers.harvested(helper.getLevel(), abs, helper.getBlockState(PlantFixtures.PLANT), other);
            helper.assertTrue(rec.toasts.stream().noneMatch(t -> t.player() == other
                    && t.toast().title().key().equals(CultivarBred.LANDRACE_TITLE)), "the other player got the bred toast");
            helper.assertTrue(!JournalGameTests.journal(other).has("cultivar.eastern_carrot"), "credited to the harvester");
            // planting it again: no second toast
            int n = rec.toasts.size();
            HintTriggers.planted(helper.getLevel(), abs, planter);
            helper.assertTrue(rec.toasts.size() == n, "a second bred toast");
        } finally {
            rec.close();
            planter.discard();
            other.discard();
        }
        helper.succeed();
    }
}
