package io.github.slugblue.wildroots.archfixtures.network;
/** Allowed: the guard itself registers consumers. */
public class FakeServerPacketGuard {
    void f(net.minecraftforge.network.simple.SimpleChannel channel) {
        channel.messageBuilder(String.class, 0).consumerMainThread((msg, ctx) -> { });
    }
}
