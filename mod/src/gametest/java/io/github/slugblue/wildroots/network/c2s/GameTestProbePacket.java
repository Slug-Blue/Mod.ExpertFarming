package io.github.slugblue.wildroots.network.c2s;

import io.github.slugblue.wildroots.network.BoundedReader;
import net.minecraft.network.FriendlyByteBuf;

/**
 * GameTest-only C→S probe (TAE, SEC-02): one int, nothing else. It lives in
 * {@code network.c2s} of the GameTest source set because
 * {@code ServerPacketGuard.register} accepts only that package (SEC-04); it
 * never ships in the jar.
 */
public record GameTestProbePacket(int n) {

    public static final GameTestProbePacket MALFORMED = new GameTestProbePacket(Integer.MIN_VALUE);

    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(n);
    }

    public static GameTestProbePacket decode(FriendlyByteBuf buf) {
        int n = buf.readVarInt();
        BoundedReader.requireFullyRead(buf);
        return new GameTestProbePacket(n);
    }
}
