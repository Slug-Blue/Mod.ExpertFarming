package io.github.slugblue.wildroots.archfixtures.feature;

import io.github.slugblue.wildroots.archfixtures.network.FakeServerPacketGuard;

/** Violates SEC-04: a C→S registration outside the network package. */
public class RegistersC2sOutsideNetwork {
    void init() {
        FakeServerPacketGuard.register(String.class);
    }
}
