package io.github.slugblue.wildroots.gametest;

import io.github.slugblue.wildroots.WildRoots;
import io.github.slugblue.wildroots.core.registry.ModItems;
import io.github.slugblue.wildroots.lens.Lens;
import io.github.slugblue.wildroots.lens.LensNetwork;
import io.github.slugblue.wildroots.lens.LensRow;
import io.github.slugblue.wildroots.lens.LensWindowPacket;
import io.github.slugblue.wildroots.plant.PlantBlockEntity;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundPlayerChatPacket;
import net.minecraft.network.protocol.game.ClientboundSystemChatPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * Playtest 2, item 3: the hand lens on a plant sends the lens window packet
 * and nothing to chat; the lens cooldown still holds a second use back; the
 * lens on the seeds in the other hand sends the lot's window with the
 * offspring band. One test per batch: the recorder replaces the shared
 * sender while it runs.
 */
@GameTestHolder(WildRoots.MOD_ID)
@PrefixGameTestTemplate(false)
public final class LensWindowGameTests {

    private LensWindowGameTests() {
    }

    /** The packets a player's listener was asked to send, and the lens windows sent to it. */
    static final class Sent implements AutoCloseable {
        final List<Packet<?>> packets = new ArrayList<>();
        final List<LensWindowPacket> windowPackets = new ArrayList<>();
        private final java.util.function.BiConsumer<ServerPlayer, LensWindowPacket> old =
                LensNetwork.setSender((p, w) -> windowPackets.add(w));

        long windows() {
            return windowPackets.size();
        }

        void clear() {
            packets.clear();
            windowPackets.clear();
        }

        @Override
        public void close() {
            LensNetwork.setSender(old);
        }

        long chat() {
            return packets.stream().filter(p -> (p instanceof ClientboundSystemChatPacket s && !s.overlay())
                    || p instanceof ClientboundPlayerChatPacket).count();
        }
    }

    /** A survival player whose connection records every packet instead of sending it. */
    static ServerPlayer recording(GameTestHelper helper, String name, Sent sent) {
        ServerPlayer p = JournalGameTests.survival(helper, name);
        new net.minecraft.server.network.ServerGamePacketListenerImpl(p.getServer(),
                new net.minecraft.network.Connection(net.minecraft.network.protocol.PacketFlow.SERVERBOUND), p) {
            @Override
            public void send(Packet<?> packet) {
                sent.packets.add(packet);
            }

            @Override
            public void send(Packet<?> packet, net.minecraft.network.PacketSendListener listener) {
                sent.packets.add(packet);
            }
        };
        return p;
    }

    @GameTest(template = SmokeGameTests.FLOOR_3X3X3, batch = "b9_lens_window_plant")
    public static void lensOnAPlantSendsTheWindowNotChatAndKeepsTheCooldown(GameTestHelper helper) {
        Sent sent = new Sent();
        ServerPlayer p = recording(helper, "b9_window", sent);
        try {
            ServerLevel level = helper.getLevel();
            PlantBlockEntity be = PlantFixtures.plant(PlantFixtures.place(helper, PlantFixtures.PLANT, 2), 77);
            helper.assertTrue(be.live(level).isPresent(), "no plant");
            BlockPos abs = helper.absolutePos(PlantFixtures.PLANT);
            ItemStack lens = new ItemStack(ModItems.HAND_LENS.get());
            p.setItemInHand(InteractionHand.MAIN_HAND, lens);
            p.setShiftKeyDown(true); // the item's use, not the block's
            BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(abs), Direction.UP, abs, false);

            sent.clear();
            p.gameMode.useItemOn(p, level, lens, InteractionHand.MAIN_HAND, hit);
            helper.assertTrue(sent.windows() == 1, "lens window packets: " + sent.windows());
            helper.assertTrue(sent.chat() == 0, "chat lines: " + sent.chat());
            helper.assertTrue(p.getCooldowns().isOnCooldown(ModItems.HAND_LENS.get()), "no cooldown");

            // the reading in the packet is the plant's: its row parses and the plant stands on farmland
            LensWindowPacket w = sent.windowPackets.get(0);
            helper.assertTrue(w.equals(Lens.readPlant(level, abs, null).orElseThrow().packet().orElseThrow()), "window " + w);
            helper.assertTrue(w.kind() == LensWindowPacket.Kind.PLANT && LensRow.parse(w.row()).isPresent()
                    && w.state() != LensWindowPacket.NO_MODEL && w.size() <= LensWindowPacket.MAX_BYTES,
                    "window " + w);

            // a second use during the cooldown sends nothing
            sent.clear();
            p.gameMode.useItemOn(p, level, lens, InteractionHand.MAIN_HAND, hit);
            helper.assertTrue(sent.windows() == 0, "window during the cooldown: " + sent.windows());
            helper.assertTrue(sent.chat() == 0, "chat during the cooldown");

            // after the cooldown it works again
            p.getCooldowns().removeCooldown(ModItems.HAND_LENS.get());
            sent.clear();
            p.gameMode.useItemOn(p, level, lens, InteractionHand.MAIN_HAND, hit);
            helper.assertTrue(sent.windows() == 1 && sent.chat() == 0, "second window " + sent.windows());
        } finally {
            sent.close();
            p.discard();
        }
        helper.succeed();
    }

    @GameTest(template = SmokeGameTests.FLOOR_3X3X3, batch = "b9_lens_window_lot")
    public static void lensOnSeedsInTheOtherHandSendsTheLotWindowWithBands(GameTestHelper helper) {
        Sent sent = new Sent();
        ServerPlayer p = recording(helper, "b9_lot", sent);
        try {
            ServerLevel level = helper.getLevel();
            ItemStack lot = PlantingGameTests.lot(level, 4);
            p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.HAND_LENS.get()));
            p.setItemInHand(InteractionHand.OFF_HAND, lot);
            sent.clear();
            p.getItemInHand(InteractionHand.MAIN_HAND).use(level, p, InteractionHand.MAIN_HAND);
            helper.assertTrue(sent.windows() == 1 && sent.chat() == 0,
                    "lot window " + sent.windows() + ", chat " + sent.chat());
            LensWindowPacket w = sent.windowPackets.get(0);
            helper.assertTrue(w.equals(Lens.lotWindow(p.getItemInHand(InteractionHand.OFF_HAND)).orElseThrow()),
                    "lot window " + w);
            LensRow row = LensRow.parse(w.row()).orElseThrow();
            int tokens = LensRow.tokens(row.traits()).size();
            helper.assertTrue(w.kind() == LensWindowPacket.Kind.LOT && tokens > 0
                    && row.bands().length() == 2 * tokens, "no band on the lot row: " + row);
            helper.assertTrue(w.state() == LensWindowPacket.NO_MODEL && w.env() == 0, "lot window with a model or env");
        } finally {
            sent.close();
            p.discard();
        }
        helper.succeed();
    }
}
