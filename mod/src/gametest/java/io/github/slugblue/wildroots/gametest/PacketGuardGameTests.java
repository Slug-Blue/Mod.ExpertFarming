package io.github.slugblue.wildroots.gametest;

import com.mojang.authlib.GameProfile;
import io.github.slugblue.wildroots.WildRoots;
import io.github.slugblue.wildroots.core.config.ServerConfig;
import io.github.slugblue.wildroots.network.ServerPacketGuard;
import io.github.slugblue.wildroots.network.c2s.GameTestProbePacket;
import io.netty.buffer.Unpooled;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.game.ServerboundCustomPayloadPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.network.NetworkDirection;

/**
 * WR-127, SEC-02 GameTest (TAE): C→S packets go the whole server path —
 * {@code ServerGamePacketListenerImpl#handleCustomPayload} → Forge dispatch →
 * guarded decoder → {@code ServerPacketGuard} with the live limiter and
 * SERVER config → handler — on the {@link GuardProbeChannel} probe.
 *
 * <p>Players: {@code makeMockServerPlayerInLevel} fails in Forge 47, so a
 * player is built directly with a play listener over a channel-less
 * {@link Connection} and is not added to the player list (nothing is sent to
 * it). The listener records disconnects instead of closing a socket.
 */
@GameTestHolder(WildRoots.MOD_ID)
@PrefixGameTestTemplate(false)
public final class PacketGuardGameTests {

    private PacketGuardGameTests() {
    }

    /** A connected-looking player whose disconnects are recorded. */
    record Probe(ServerPlayer player, ServerGamePacketListenerImpl listener, List<Component> disconnects) {

        static Probe connect(GameTestHelper helper, String name) {
            MinecraftServer server = helper.getLevel().getServer();
            ServerPlayer player = new ServerPlayer(server, helper.getLevel(), new GameProfile(UUID.randomUUID(), name));
            List<Component> disconnects = new ArrayList<>();
            ServerGamePacketListenerImpl listener = new ServerGamePacketListenerImpl(server,
                    new Connection(PacketFlow.SERVERBOUND), player) {
                @Override
                public void disconnect(Component reason) {
                    disconnects.add(reason);
                }
            };
            return new Probe(player, listener, disconnects);
        }

        void send(int n) {
            listener.handleCustomPayload((ServerboundCustomPayloadPacket) GuardProbeChannel.channel()
                    .toVanillaPacket(new GameTestProbePacket(n), NetworkDirection.PLAY_TO_SERVER));
        }

        void sendRaw(byte... bytes) {
            FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
            buf.writeBytes(bytes);
            listener.handleCustomPayload(new ServerboundCustomPayloadPacket(GuardProbeChannel.NAME, buf));
        }

        int handled() {
            return GuardProbeChannel.handled(player.getUUID());
        }

        void forget() {
            ServerPacketGuard.LIMITER.forget(player.getUUID());
        }
    }

    /** Tokens refilled while a burst of {@code nanos} was being sent. */
    static int refillDuring(long nanos) {
        return (int) (nanos * ServerConfig.PACKET_REFILL_PER_SECOND.get() / 1_000_000_000L);
    }

    @GameTest(template = SmokeGameTests.FLOOR_3X3X3)
    public static void fiftyPacketsInOneTickAtMostFiveHandled(GameTestHelper helper) {
        helper.assertTrue(GuardProbeChannel.channel() != null, "probe channel not registered");
        helper.assertTrue(ServerConfig.PACKET_BUCKET_CAPACITY.get() == 5, "capacity is not the default 5");
        Probe p = Probe.connect(helper, "wr127_burst");
        try {
            long t0 = System.nanoTime();
            for (int i = 0; i < 50; i++) {
                p.send(i);
            }
            long elapsed = System.nanoTime() - t0;
            int bound = 5 + refillDuring(elapsed); // exactly 5 unless the burst took ≥ 200 ms
            helper.assertTrue(p.handled() >= 1, "nothing was handled: the probe path is broken");
            helper.assertTrue(p.handled() <= bound, "handled " + p.handled() + " of 50 in one tick, bound "
                    + bound + " (burst " + elapsed / 1_000_000 + " ms)");
            helper.assertTrue(p.disconnects().isEmpty(), "45 drops must not disconnect: " + p.disconnects());
        } finally {
            p.forget();
        }
        helper.succeed();
    }

    @GameTest(template = SmokeGameTests.FLOOR_3X3X3)
    public static void floodDisconnectsOnceWithTheFloodMessage(GameTestHelper helper) {
        int threshold = ServerConfig.PACKET_FLOOD_DISCONNECT_THRESHOLD.get();
        helper.assertTrue(threshold > 0, "flood disconnect is off in the GameTest config");
        Probe p = Probe.connect(helper, "wr127_flood");
        try {
            int allowed = ServerConfig.PACKET_BUCKET_CAPACITY.get();
            for (int i = 0; i < allowed + threshold - 1; i++) {
                p.send(i);
            }
            helper.assertTrue(p.disconnects().isEmpty(), "disconnected before the threshold");
            for (int i = 0; i < 50; i++) {
                p.send(i);
            }
            helper.assertTrue(p.disconnects().size() == 1, "disconnects: " + p.disconnects().size());
            helper.assertTrue(p.disconnects().get(0).getContents() instanceof TranslatableContents t
                    && ServerPacketGuard.FLOOD_KEY.equals(t.getKey()), "reason " + p.disconnects().get(0));
        } finally {
            p.forget();
        }
        helper.succeed();
    }

    @GameTest(template = SmokeGameTests.FLOOR_3X3X3)
    public static void malformedPacketsAreDroppedButTheConnectionSurvives(GameTestHelper helper) {
        Probe p = Probe.connect(helper, "wr127_malformed");
        try {
            p.sendRaw((byte) 0, (byte) 7, (byte) 1); // index 0, n = 7, one extra byte
            p.sendRaw((byte) 0, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0x01); // bad VarInt
            p.sendRaw((byte) 0); // no payload at all
            helper.assertTrue(p.handled() == 0, "a malformed packet reached the handler");
            helper.assertTrue(p.disconnects().isEmpty(), "three malformed packets disconnected");
            p.send(1);
            helper.assertTrue(p.handled() == 1, "a valid packet after malformed ones was not handled");
        } finally {
            p.forget();
        }
        helper.succeed();
    }

    /** The NameSelection extra bucket follows {@code nameSelectionPerMinute} of the SERVER config (SEC-02). */
    @GameTest(template = SmokeGameTests.FLOOR_3X3X3)
    public static void nameSelectionLimitFollowsTheConfig(GameTestHelper helper) {
        int old = ServerConfig.NAME_SELECTION_PER_MINUTE.get();
        UUID id = UUID.randomUUID();
        int allowed = 0;
        try {
            ServerConfig.NAME_SELECTION_PER_MINUTE.set(1);
            for (int i = 0; i < 3; i++) {
                if (ServerPacketGuard.LIMITER.tryAcquire(id, ServerPacketGuard.NAME_SELECTION).allowed()) {
                    allowed++;
                }
            }
        } finally {
            ServerConfig.NAME_SELECTION_PER_MINUTE.set(old);
            ServerPacketGuard.LIMITER.forget(id);
        }
        helper.assertTrue(allowed == 1, "nameSelectionPerMinute = 1 but " + allowed + " NameSelection packets passed");
        helper.succeed();
    }
}
