package io.github.slugblue.wildroots.network;

import io.github.slugblue.wildroots.journal.Journals;
import io.github.slugblue.wildroots.network.c2s.JournalActionPacket;

/**
 * Every production C→S packet, registered on {@link WrNetwork#CHANNEL}
 * through {@link ServerPacketGuard#register} (SEC-01, SEC-04; ArchUnit
 * {@code c2sRegistrationOnlyInNetwork}): guarded decoder, rate limit, sender
 * check and exception isolation for each. A packet is registered together
 * with its handler, never before (docs/05-architecture.md §14).
 */
public final class C2sPackets {

    private C2sPackets() {
    }

    static void register() {
        ServerPacketGuard.register(WrNetwork.CHANNEL, WrNetwork.nextIndex(), JournalActionPacket.class,
                JournalActionPacket::encode, JournalActionPacket::decode, JournalActionPacket.MALFORMED,
                Journals::onAction);
    }
}
