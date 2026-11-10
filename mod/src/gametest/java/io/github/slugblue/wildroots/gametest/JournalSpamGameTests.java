package io.github.slugblue.wildroots.gametest;

import io.github.slugblue.wildroots.WildRoots;
import io.github.slugblue.wildroots.core.registry.ModItems;
import io.github.slugblue.wildroots.item.FieldJournalItem;
import io.github.slugblue.wildroots.item.HandLensItem;
import io.github.slugblue.wildroots.journal.JournalSyncPacket;
import io.github.slugblue.wildroots.journal.Journals;
import io.netty.buffer.Unpooled;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * Review 1, M2: using the journal or the lens many times in one tick (a
 * modified client sends use packets freely) costs at most one action per
 * 10 ticks, enforced by the server's item cooldown, and opening the journal
 * sends only "open" when the client is up to date.
 */
@GameTestHolder(WildRoots.MOD_ID)
@PrefixGameTestTemplate(false)
public final class JournalSpamGameTests {

    private JournalSpamGameTests() {
    }

    static int bytes(JournalSyncPacket p) {
        FriendlyByteBuf b = new FriendlyByteBuf(Unpooled.buffer());
        p.encode(b);
        return b.readableBytes();
    }

    static InteractionResult use(ServerPlayer p, ItemStack stack) {
        p.setItemInHand(InteractionHand.MAIN_HAND, stack);
        return p.gameMode.useItem(p, p.serverLevel(), stack, InteractionHand.MAIN_HAND);
    }

    @GameTest(template = SmokeGameTests.FLOOR_3X3X3, batch = "wr18x_journal")
    public static void fiftyJournalUsesInATickSendAtMostOneFullSync(GameTestHelper helper) {
        JournalGameTests.Recorder rec = new JournalGameTests.Recorder();
        ServerPlayer p = JournalGameTests.survival(helper, "wr18x_spam");
        try {
            for (int i = 0; i < 40; i++) {
                Journals.unlock(p, "note.filler_" + i);
            }
            rec.syncs.clear();
            ItemStack book = new ItemStack(ModItems.FIELD_JOURNAL.get());
            // the client never got a full sync (no login here): the first use sends it, the cooldown stops the rest
            for (int i = 0; i < 50; i++) {
                use(p, book);
            }
            long full = rec.syncs.stream().filter(JournalSyncPacket::full).count();
            helper.assertTrue(full <= 1 && rec.syncs.size() == 1, "syncs " + rec.syncs.size() + ", full " + full);
            int fullBytes = bytes(rec.syncs.get(0));
            int total = rec.syncs.stream().mapToInt(JournalSpamGameTests::bytes).sum();
            helper.assertTrue(total <= fullBytes, "S→C bytes " + total + " for one full sync of " + fullBytes);
            helper.assertTrue(p.getCooldowns().isOnCooldown(ModItems.FIELD_JOURNAL.get()), "no cooldown");

            // after the cooldown, with the client up to date: only "open", an empty delta
            for (int t = 0; t < FieldJournalItem.COOLDOWN_TICKS; t++) {
                p.getCooldowns().tick();
            }
            rec.syncs.clear();
            for (int i = 0; i < 50; i++) {
                use(p, book);
            }
            helper.assertTrue(rec.syncs.size() == 1 && !rec.syncs.get(0).full() && rec.syncs.get(0).open()
                    && rec.syncs.get(0).entries().isEmpty(), "second burst sent " + rec.syncs);
            helper.assertTrue(bytes(rec.syncs.get(0)) < 64, "open-only packet of " + bytes(rec.syncs.get(0)) + " B");
        } finally {
            rec.close();
            p.discard();
        }
        helper.succeed();
    }

    @GameTest(template = SmokeGameTests.FLOOR_3X3X3, batch = "wr18x_journal")
    public static void fiftyLensUsesInATickInspectOnce(GameTestHelper helper) {
        JournalGameTests.Recorder rec = new JournalGameTests.Recorder();
        ServerPlayer p = JournalGameTests.survival(helper, "wr18x_lens_spam");
        try {
            ServerLevel level = helper.getLevel();
            p.setItemInHand(InteractionHand.OFF_HAND, PlantingGameTests.lot(level, 3));
            ItemStack lens = new ItemStack(ModItems.HAND_LENS.get());
            int done = 0;
            for (int i = 0; i < 50; i++) {
                if (use(p, lens).consumesAction()) {
                    done++;
                }
            }
            helper.assertTrue(done == 1, "lens worked " + done + " times in one tick");
            helper.assertTrue(p.getCooldowns().isOnCooldown(ModItems.HAND_LENS.get()), "no lens cooldown");
            for (int t = 0; t < HandLensItem.COOLDOWN_TICKS; t++) {
                p.getCooldowns().tick();
            }
            helper.assertTrue(use(p, lens).consumesAction(), "lens refused after the cooldown");
        } finally {
            rec.close();
            p.discard();
        }
        helper.succeed();
    }
}
