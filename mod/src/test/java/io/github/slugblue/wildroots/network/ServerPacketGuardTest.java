package io.github.slugblue.wildroots.network;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.slugblue.wildroots.network.c2s.NameSelectionPacket;
import io.netty.buffer.Unpooled;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;

/** WR-127, SEC-01: the guard drops packets without a sender, isolates exceptions, always marks the packet handled. */
class ServerPacketGuardTest {

    record Player(UUID id, boolean gone) {
    }

    /** Fake network context: work runs immediately, everything is recorded. */
    static final class FakeContext implements ServerPacketGuard.Context<Player> {
        final Player sender;
        boolean handled;
        int enqueued;
        final List<Component> disconnects = new ArrayList<>();

        FakeContext(Player sender) {
            this.sender = sender;
        }

        @Override
        public Player sender() {
            return sender;
        }

        @Override
        public UUID id(Player player) {
            return player.id();
        }

        @Override
        public boolean disconnected(Player player) {
            return player.gone();
        }

        @Override
        public void enqueueWork(Runnable work) {
            enqueued++;
            work.run();
        }

        @Override
        public void setPacketHandled(boolean handled) {
            this.handled = handled;
        }

        @Override
        public void disconnect(Player player, Component reason) {
            disconnects.add(reason);
        }
    }

    final AtomicLong clock = new AtomicLong();
    final PacketRateLimiter limiter = new PacketRateLimiter(clock::get, () -> 5, () -> 5, () -> 200, Map.of());
    final List<String> log = new ArrayList<>();
    final List<NameSelectionPacket> handledPackets = new ArrayList<>();
    final NameSelectionPacket ok = new NameSelectionPacket(7, "Golden");

    void handle(NameSelectionPacket msg, FakeContext ctx) {
        ServerPacketGuard.handle("NameSelection", msg, NameSelectionPacket.MALFORMED, ctx, limiter,
                (m, p) -> handledPackets.add(m), log::add);
    }

    @Test
    void noSenderIsDroppedButHandled() {
        FakeContext ctx = new FakeContext(null);
        handle(ok, ctx);
        assertTrue(ctx.handled);
        assertEquals(0, ctx.enqueued);
        assertTrue(handledPackets.isEmpty());
    }

    @Test
    void disconnectedSenderIsDropped() {
        FakeContext ctx = new FakeContext(new Player(UUID.randomUUID(), true));
        handle(ok, ctx);
        assertTrue(ctx.handled);
        assertTrue(handledPackets.isEmpty());
    }

    @Test
    void validPacketRunsOnTheServerThread() {
        FakeContext ctx = new FakeContext(new Player(UUID.randomUUID(), false));
        handle(ok, ctx);
        assertTrue(ctx.handled);
        assertEquals(1, ctx.enqueued);
        assertEquals(List.of(ok), handledPackets);
    }

    @Test
    void handlerExceptionIsCaughtAndLogged() {
        FakeContext ctx = new FakeContext(new Player(UUID.randomUUID(), false));
        ServerPacketGuard.handle("NameSelection", ok, NameSelectionPacket.MALFORMED, ctx, limiter, (m, p) -> {
            throw new IllegalStateException("boom");
        }, log::add);
        assertTrue(ctx.handled);
        assertEquals(1, log.size());
        assertTrue(log.get(0).contains("boom"));
    }

    @Test
    void decodeExceptionGivesTheMalformedMarkerAndIsDropped() {
        Function<FriendlyByteBuf, NameSelectionPacket> decoder = ServerPacketGuard.guardedDecoder(b -> {
            throw new IllegalArgumentException("bad");
        }, NameSelectionPacket.MALFORMED);
        NameSelectionPacket decoded = decoder.apply(new FriendlyByteBuf(Unpooled.buffer()));
        assertSame(NameSelectionPacket.MALFORMED, decoded);
        FakeContext ctx = new FakeContext(new Player(UUID.randomUUID(), false));
        handle(decoded, ctx);
        assertTrue(ctx.handled);
        assertTrue(handledPackets.isEmpty());
        assertEquals(1, log.size());
        assertTrue(log.get(0).contains("malformed"), log.get(0));
    }

    @Test
    void extraBytesAreMalformed() {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        ok.encode(buf);
        buf.writeByte(1);
        assertSame(NameSelectionPacket.MALFORMED,
                ServerPacketGuard.guardedDecoder(NameSelectionPacket::decode, NameSelectionPacket.MALFORMED).apply(buf));
    }

    @Test
    void floodDisconnectsAndLogsOnce() {
        Player p = new Player(UUID.randomUUID(), false);
        FakeContext ctx = new FakeContext(p);
        for (int i = 0; i < 300; i++) {
            handle(ok, ctx);
        }
        assertEquals(5, handledPackets.size());
        assertEquals(1, ctx.disconnects.size());
        assertEquals(ServerPacketGuard.FLOOD_KEY, ((net.minecraft.network.chat.contents.TranslatableContents)
                ctx.disconnects.get(0).getContents()).getKey());
        assertEquals(2, log.size(), log::toString); // one drop line, one disconnect line
    }

    /** SEC-47: an exception text from the packet cannot forge or break a log line; one line per (type, player). */
    @Test
    void failureLinesAreEscapedAndThrottled() {
        AtomicLong ms = new AtomicLong();
        io.github.slugblue.wildroots.core.safety.LogThrottle old =
                ServerPacketGuard.setFailureThrottle(new io.github.slugblue.wildroots.core.safety.LogThrottle(ms::get, 1000));
        try {
            Player p = new Player(UUID.randomUUID(), false);
            for (int i = 0; i < 3; i++) {
                ServerPacketGuard.handle("NameSelection", ok, NameSelectionPacket.MALFORMED, new FakeContext(p), limiter,
                        (m, pl) -> {
                            throw new IllegalStateException("x\nwildroots.audit FAKE \u202E");
                        }, log::add);
            }
            assertEquals(1, log.size(), log::toString);
            String line = log.get(0);
            assertTrue(line.startsWith("packet_failed "), line);
            assertTrue(!line.contains("\n") && !line.contains("\u202E"), line);
            assertTrue(line.contains("\\u000Awildroots.audit FAKE \\u202E"), line);
            ms.addAndGet(1000);
            ServerPacketGuard.handle("NameSelection", ok, NameSelectionPacket.MALFORMED, new FakeContext(p), limiter,
                    (m, pl) -> {
                        throw new IllegalStateException("again");
                    }, log::add);
            assertEquals(2, log.size());
        } finally {
            ServerPacketGuard.setFailureThrottle(old);
        }
    }
}
