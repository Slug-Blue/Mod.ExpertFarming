package io.github.slugblue.wildroots.gametest;

import com.mojang.authlib.GameProfile;
import io.github.slugblue.wildroots.WildRoots;
import io.github.slugblue.wildroots.core.config.ServerConfig;
import io.github.slugblue.wildroots.core.registry.ModItems;
import io.github.slugblue.wildroots.journal.JournalData;
import io.github.slugblue.wildroots.journal.JournalSyncPacket;
import io.github.slugblue.wildroots.journal.Journals;
import io.github.slugblue.wildroots.journal.ToastPacket;
import io.github.slugblue.wildroots.network.ServerPacketGuard;
import io.github.slugblue.wildroots.network.WrNetwork;
import io.github.slugblue.wildroots.network.c2s.JournalActionPacket;
import io.github.slugblue.wildroots.plant.Passport;
import io.github.slugblue.wildroots.plant.PlantBlockEntity;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ServerboundCustomPayloadPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.network.NetworkDirection;

/**
 * WR-180: the journal capability, the journal handed out at the first wild
 * harvest, persistence over death and relog, a new journal opening the same
 * entries, and SEC-02 on the real {@code JournalAction} packet (carry-over
 * from WR-127).
 */
@GameTestHolder(WildRoots.MOD_ID)
@PrefixGameTestTemplate(false)
public final class JournalGameTests {

    private JournalGameTests() {
    }

    /** What the journal sent while a test ran. */
    static final class Recorder {
        final List<JournalSyncPacket> syncs = new ArrayList<>();
        final List<ToastPacket> toasts = new ArrayList<>();
        private final Journals.Sender old;

        Recorder() {
            old = Journals.setSender(Journals.recording((p, s) -> syncs.add(s), (p, t) -> toasts.add(t)));
        }

        void close() {
            Journals.setSender(old);
        }
    }

    /** A survival server player without a connection, next to the plant. */
    static ServerPlayer survival(GameTestHelper helper, String name) {
        ServerLevel level = helper.getLevel();
        ServerPlayer p = new ServerPlayer(level.getServer(), level, new GameProfile(UUID.randomUUID(), name));
        p.setPos(Vec3.atCenterOf(helper.absolutePos(PlantFixtures.PLANT)).add(1, 0, 0));
        p.gameMode.changeGameModeForPlayer(GameType.SURVIVAL);
        mute(p);
        return p;
    }

    /**
     * A play listener over a channel-less connection that drops every packet:
     * cooldowns, chat and menu sync send packets, and the player is not in
     * the player list (nothing reaches a socket).
     */
    static void mute(ServerPlayer p) {
        new net.minecraft.server.network.ServerGamePacketListenerImpl(p.getServer(),
                new net.minecraft.network.Connection(net.minecraft.network.protocol.PacketFlow.SERVERBOUND), p) {
            @Override
            public void send(net.minecraft.network.protocol.Packet<?> packet) {
            }

            @Override
            public void send(net.minecraft.network.protocol.Packet<?> packet,
                             net.minecraft.network.PacketSendListener listener) {
            }
        };
    }

    static JournalData journal(ServerPlayer p) {
        return Journals.of(p).orElseThrow(() -> new IllegalStateException("no journal capability"));
    }

    /** A wild carrot of the starter patch at harvest ripeness at {@code rel}. */
    static PlantBlockEntity wildMature(GameTestHelper helper, BlockPos rel, long patch) {
        PlantBlockEntity be = PlantFixtures.place(helper, rel, 3);
        be.initWild(PlantFixtures.SP, Passport.wild(patch, "meadow", false, false));
        return be;
    }

    static int journals(ServerPlayer p) {
        int n = 0;
        for (ItemStack s : p.getInventory().items) {
            if (s.is(ModItems.FIELD_JOURNAL.get())) {
                n += s.getCount();
            }
        }
        return n;
    }

    @GameTest(template = SmokeGameTests.FLOOR_3X3X3, batch = "wr18x_journal")
    public static void firstWildHarvestGivesTheJournalOnce(GameTestHelper helper) {
        Recorder rec = new Recorder();
        ServerPlayer p = survival(helper, "wr180_first");
        try {
            helper.assertTrue(ServerConfig.GIVE_JOURNAL_ON_FIRST_HARVEST.get(), "config default is not true");
            wildMature(helper, PlantFixtures.PLANT, helper.absolutePos(PlantFixtures.PLANT).asLong());
            helper.assertTrue(p.gameMode.destroyBlock(helper.absolutePos(PlantFixtures.PLANT)), "not broken");
            helper.assertTrue(journals(p) == 1, "journals after the first wild harvest: " + journals(p));
            JournalData j = journal(p);
            helper.assertTrue(j.given(), "journal not marked given");
            helper.assertTrue(j.has("hint.welcome"), "no welcome entry");
            helper.assertTrue(j.has("species.wild_carrot"), "species not discovered");
            helper.assertTrue(j.entry("cultivar.eastern_carrot").map(e -> e.is(JournalData.LOCKED)).orElse(false),
                    "no silhouette of eastern carrot");
            helper.assertTrue(!rec.syncs.isEmpty(), "nothing synced to the client");
            // a second wild harvest gives no second journal
            wildMature(helper, PlantFixtures.PLANT, 99L);
            helper.assertTrue(p.gameMode.destroyBlock(helper.absolutePos(PlantFixtures.PLANT)), "not broken twice");
            helper.assertTrue(journals(p) == 1, "journals after the second harvest: " + journals(p));
            helper.assertTrue(j.entry("species.wild_carrot").orElseThrow().rows().stream()
                    .filter(r -> r.key().equals("wildroots.journal.species.found_at")).count() == 2,
                    "found-at rows " + j.entry("species.wild_carrot").orElseThrow().rows());
        } finally {
            rec.close();
            p.discard();
        }
        helper.succeed();
    }

    @GameTest(template = SmokeGameTests.FLOOR_3X3X3, batch = "wr18x_journal")
    public static void configOffGivesNoItemButTheWelcomeEntry(GameTestHelper helper) {
        Recorder rec = new Recorder();
        ServerPlayer p = survival(helper, "wr180_noitem");
        boolean old = ServerConfig.GIVE_JOURNAL_ON_FIRST_HARVEST.get();
        try {
            ServerConfig.GIVE_JOURNAL_ON_FIRST_HARVEST.set(false);
            wildMature(helper, PlantFixtures.PLANT, 7L);
            helper.assertTrue(p.gameMode.destroyBlock(helper.absolutePos(PlantFixtures.PLANT)), "not broken");
            helper.assertTrue(journals(p) == 0, "a journal was given with giveJournalOnFirstHarvest = false");
            helper.assertTrue(journal(p).has("hint.welcome"), "welcome entry not created");
        } finally {
            ServerConfig.GIVE_JOURNAL_ON_FIRST_HARVEST.set(old);
            rec.close();
            p.discard();
        }
        helper.succeed();
    }

    @GameTest(template = SmokeGameTests.FLOOR_3X3X3, batch = "wr18x_journal")
    public static void entriesSurviveDeathRelogAndANewJournalOpensThem(GameTestHelper helper) {
        Recorder rec = new Recorder();
        ServerLevel level = helper.getLevel();
        ServerPlayer p = survival(helper, "wr180_persist");
        ServerPlayer relogged = null;
        ServerPlayer respawned = null;
        try {
            JournalData j = journal(p);
            j.markGiven();
            Journals.unlock(p, "hint.welcome", "\u0001species.wild_carrot");
            Journals.unlock(p, "hint.bees");
            j.apply(new ResourceLocation("wildroots", "hint.bees"), JournalActionPacket.PIN);
            CompoundTag before = j.save();
            helper.assertTrue(io.github.slugblue.wildroots.core.safety.NbtLimits.journal(before).ok(), "over 64 KiB");

            // relog: the player is saved with ForgeCaps and loaded into a new entity
            CompoundTag saved = p.saveWithoutId(new CompoundTag());
            relogged = new ServerPlayer(level.getServer(), level, p.getGameProfile());
            relogged.load(saved);
            helper.assertTrue(journal(relogged).save().equals(before), "relog lost the journal: " + journal(relogged).save());

            // death: Forge clones the player
            respawned = new ServerPlayer(level.getServer(), level, p.getGameProfile());
            mute(respawned);
            MinecraftForge.EVENT_BUS.post(new PlayerEvent.Clone(respawned, p, true));
            helper.assertTrue(journal(respawned).save().equals(before), "death lost the journal");

            // a new journal (crafted from a book and a lot) opens the same entries
            var recipe = level.getRecipeManager().byKey(new ResourceLocation("wildroots", "field_journal"));
            helper.assertTrue(recipe.isPresent()
                    && recipe.get().getResultItem(level.registryAccess()).is(ModItems.FIELD_JOURNAL.get()),
                    "no book + lot recipe");
            ItemStack book = new ItemStack(ModItems.FIELD_JOURNAL.get());
            helper.assertTrue(!book.hasTag(), "a journal item carries NBT");
            respawned.setItemInHand(InteractionHand.MAIN_HAND, book);
            rec.syncs.clear();
            book.use(level, respawned, InteractionHand.MAIN_HAND);
            helper.assertTrue(rec.syncs.size() == 1 && rec.syncs.get(0).full() && rec.syncs.get(0).open(),
                    "using the journal did not send a full sync with open: " + rec.syncs);
            List<String> ids = rec.syncs.get(0).entries().stream().map(JournalData.Entry::id).toList();
            helper.assertTrue(ids.equals(List.of("hint.welcome", "hint.bees")), "opened entries " + ids);
            helper.assertTrue(rec.syncs.get(0).entries().get(1).is(JournalData.PINNED), "pin lost");
        } finally {
            rec.close();
            p.discard();
            if (relogged != null) {
                relogged.discard();
            }
            if (respawned != null) {
                respawned.discard();
            }
        }
        helper.succeed();
    }

    /** SEC-02 on the real C→S packet: 50 JournalAction packets in one tick, at most 5 handled. */
    @GameTest(template = SmokeGameTests.FLOOR_3X3X3, batch = "wr18x_journal")
    public static void fiftyJournalActionsInOneTickAtMostFiveHandled(GameTestHelper helper) {
        Recorder rec = new Recorder();
        PacketGuardGameTests.Probe p = PacketGuardGameTests.Probe.connect(helper, "wr180_burst");
        try {
            JournalData j = journal(p.player());
            j.unlock("hint.welcome", List.of(), List.of());
            int before = j.revision();
            ResourceLocation id = new ResourceLocation("wildroots", "hint.welcome");
            long t0 = System.nanoTime();
            for (int i = 0; i < 50; i++) {
                // PIN and UNPIN alternate, so every handled packet changes the journal once
                JournalActionPacket msg = new JournalActionPacket(id, i % 2 == 0 ? JournalActionPacket.PIN
                        : JournalActionPacket.UNPIN);
                p.listener().handleCustomPayload((ServerboundCustomPayloadPacket) WrNetwork.CHANNEL
                        .toVanillaPacket(msg, NetworkDirection.PLAY_TO_SERVER));
            }
            long elapsed = System.nanoTime() - t0;
            int handled = j.revision() - before;
            int bound = 5 + PacketGuardGameTests.refillDuring(elapsed);
            helper.assertTrue(handled >= 1, "no JournalAction was handled: the real path is broken");
            helper.assertTrue(handled <= bound, "handled " + handled + " of 50 in one tick, bound " + bound);
            helper.assertTrue(p.disconnects().isEmpty(), "45 drops must not disconnect");
            helper.assertTrue(rec.syncs.size() == handled, "deltas " + rec.syncs.size() + " for " + handled + " actions");
        } finally {
            ServerPacketGuard.LIMITER.forget(p.player().getUUID());
            rec.close();
        }
        helper.succeed();
    }

    /** A JournalAction for an entry the player does not have changes nothing and is not answered. */
    @GameTest(template = SmokeGameTests.FLOOR_3X3X3, batch = "wr18x_journal")
    public static void journalActionForAForeignEntryIsDropped(GameTestHelper helper) {
        Recorder rec = new Recorder();
        PacketGuardGameTests.Probe p = PacketGuardGameTests.Probe.connect(helper, "wr180_foreign");
        try {
            JournalData j = journal(p.player());
            int before = j.revision();
            for (String path : List.of("hint.welcome", "hint.not_there", "species.wild_carrot")) {
                p.listener().handleCustomPayload((ServerboundCustomPayloadPacket) WrNetwork.CHANNEL.toVanillaPacket(
                        new JournalActionPacket(new ResourceLocation("wildroots", path), JournalActionPacket.PIN),
                        NetworkDirection.PLAY_TO_SERVER));
            }
            helper.assertTrue(j.revision() == before && j.entries().isEmpty(), "a foreign action changed the journal");
            helper.assertTrue(rec.syncs.isEmpty(), "a refused action was answered");
        } finally {
            ServerPacketGuard.LIMITER.forget(p.player().getUUID());
            rec.close();
        }
        helper.succeed();
    }
}
