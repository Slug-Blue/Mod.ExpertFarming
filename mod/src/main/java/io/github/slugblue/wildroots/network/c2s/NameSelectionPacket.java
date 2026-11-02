package io.github.slugblue.wildroots.network.c2s;

import io.github.slugblue.wildroots.core.safety.PlayerText;
import io.github.slugblue.wildroots.network.BoundedReader;
import net.minecraft.network.FriendlyByteBuf;

/**
 * C→S {@code NameSelection} (docs/05-architecture.md §14 table; SEC-04,
 * SEC-06): the player names a seedling variety in the journal. Only the
 * intent: the selection id and the raw name; the server sanitises the name
 * with {@link PlayerText} and checks ownership.
 *
 * <p>Codec only: the handler and the registration come with the journal
 * task (WR-18x), through {@code ServerPacketGuard.register}.
 */
public record NameSelectionPacket(long selectionId, String name) {

    /** Marker returned by the guarded decoder for a broken buffer. */
    public static final NameSelectionPacket MALFORMED = new NameSelectionPacket(Long.MIN_VALUE, "");

    public void encode(FriendlyByteBuf buf) {
        buf.writeLong(selectionId);
        buf.writeUtf(name, PlayerText.readUtfLimit(PlayerText.CULTIVAR_NAME_MAX));
    }

    public static NameSelectionPacket decode(FriendlyByteBuf buf) {
        long id = buf.readLong();
        String name = BoundedReader.readText(buf, PlayerText.CULTIVAR_NAME_MAX);
        BoundedReader.requireFullyRead(buf);
        return new NameSelectionPacket(id, name);
    }
}
