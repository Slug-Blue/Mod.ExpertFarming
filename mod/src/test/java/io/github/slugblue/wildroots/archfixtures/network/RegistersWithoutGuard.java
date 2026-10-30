package io.github.slugblue.wildroots.archfixtures.network;
/** SEC-01 violation: registers a packet consumer outside the guard. */
public class RegistersWithoutGuard {
    void f(net.minecraftforge.network.simple.SimpleChannel channel) {
        channel.messageBuilder(String.class, 0).consumerMainThread((msg, ctx) -> { });
    }
}
