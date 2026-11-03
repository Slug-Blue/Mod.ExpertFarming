package io.github.slugblue.wildroots.network;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaModifier;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import io.github.slugblue.wildroots.core.Ids;
import io.github.slugblue.wildroots.core.safety.PlayerText;
import io.github.slugblue.wildroots.network.c2s.JournalActionPacket;
import io.github.slugblue.wildroots.network.c2s.NameSelectionPacket;
import io.netty.buffer.Unpooled;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.Test;

/**
 * WR-127, SEC-03, SEC-04 (TAE): every C→S packet class is
 * fuzzed; exact length boundaries of each C→S field (code units, code
 * points and UTF-8 bytes of {@code readUtf}).
 */
class DecoderBoundaryTest {

    static final String C2S = "io.github.slugblue.wildroots.network.c2s";

    /** C→S packets with a fuzz test in {@link DecoderFuzzTest}; a new packet must be added there and here. */
    static final Set<String> FUZZED = Set.of(NameSelectionPacket.class.getName(), JournalActionPacket.class.getName());

    @Test
    void everyC2sPacketIsFuzzedAndHasTheGuardContract() {
        List<JavaClass> packets = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages(C2S).stream()
                .filter(c -> c.getPackageName().equals(C2S) && c.isTopLevelClass())
                .toList();
        assertFalse(packets.isEmpty(), "no C→S packets found: the scan is broken");
        assertEquals(FUZZED, packets.stream().map(JavaClass::getName).collect(Collectors.toSet()),
                "a C→S packet has no fuzz test in DecoderFuzzTest");
        for (JavaClass p : packets) {
            assertTrue(p.tryGetField("MALFORMED").filter(f -> f.getModifiers().contains(JavaModifier.STATIC)).isPresent(),
                    p.getName() + " has no static MALFORMED marker for the guarded decoder");
            assertTrue(p.tryGetMethod("decode", FriendlyByteBuf.class)
                    .filter(m -> m.getModifiers().contains(JavaModifier.STATIC)).isPresent(), p.getName() + " has no static decode");
        }
    }

    static FriendlyByteBuf buf(Consumer<FriendlyByteBuf> w) {
        FriendlyByteBuf b = new FriendlyByteBuf(Unpooled.buffer());
        w.accept(b);
        return b;
    }

    static final Function<FriendlyByteBuf, NameSelectionPacket> NAME =
            ServerPacketGuard.guardedDecoder(NameSelectionPacket::decode, NameSelectionPacket.MALFORMED);
    static final Function<FriendlyByteBuf, JournalActionPacket> JOURNAL =
            ServerPacketGuard.guardedDecoder(JournalActionPacket::decode, JournalActionPacket.MALFORMED);

    static NameSelectionPacket name(String s) {
        return NAME.apply(buf(b -> {
            b.writeLong(1);
            b.writeUtf(s, Short.MAX_VALUE);
        }));
    }

    static String cp(int codePoint) {
        return new String(Character.toChars(codePoint));
    }

    @Test
    void nameSelectionLengthBoundaries() {
        String astral = cp(0x1D400); // a letter outside the BMP: 2 UTF-16 units, 4 UTF-8 bytes
        assertEquals("ж".repeat(24), name("ж".repeat(24)).name());
        assertEquals(astral.repeat(24), name(astral.repeat(24)).name(), "24 code points = 48 units pass");
        assertEquals("田".repeat(24), name("田".repeat(24)).name(), "72 UTF-8 bytes pass");
        assertSame(NameSelectionPacket.MALFORMED, name("ж".repeat(25)), "25 BMP code points");
        assertSame(NameSelectionPacket.MALFORMED, name(astral.repeat(25)), "25 astral code points (50 units)");
        assertSame(NameSelectionPacket.MALFORMED, name(astral.repeat(24) + "ж"), "49 units");
        assertSame(NameSelectionPacket.MALFORMED, name("x".repeat(48)), "48 units, 48 code points");
        assertSame(NameSelectionPacket.MALFORMED, name("x".repeat(49)), "49 units");
        assertEquals("", name("").name(), "empty passes the decoder; PlayerText refuses it in the handler");
    }

    @Test
    void nameSelectionByteLengthPrefixIsCheckedBeforeReading() {
        int maxBytes = 3 * PlayerText.readUtfLimit(PlayerText.CULTIVAR_NAME_MAX); // readUtf: bytes ≤ 3·max
        byte[] cjk = "田".repeat(48).getBytes(StandardCharsets.UTF_8);
        assertEquals(maxBytes, cjk.length);
        assertSame(NameSelectionPacket.MALFORMED, NAME.apply(buf(b -> {
            b.writeLong(1);
            b.writeVarInt(maxBytes);
            b.writeBytes(cjk);
        })), "144 bytes = 48 code points");
        assertSame(NameSelectionPacket.MALFORMED, NAME.apply(buf(b -> {
            b.writeLong(1);
            b.writeVarInt(maxBytes + 1);
            b.writeBytes(new byte[maxBytes + 1]);
        })), "145 bytes");
        assertSame(NameSelectionPacket.MALFORMED, NAME.apply(buf(b -> {
            b.writeLong(1);
            b.writeVarInt(-1);
        })), "negative length");
        assertSame(NameSelectionPacket.MALFORMED, NAME.apply(buf(b -> b.writeInt(1))), "truncated long");
        // a surrogate encoded in UTF-8 decodes to U+FFFD, never to a lone surrogate
        NameSelectionPacket p = NAME.apply(buf(b -> {
            b.writeLong(1);
            b.writeVarInt(3);
            b.writeBytes(new byte[] {(byte) 0xED, (byte) 0xA0, (byte) 0x80});
        }));
        assertNotSame(NameSelectionPacket.MALFORMED, p);
        assertTrue(p.name().chars().noneMatch(c -> Character.isSurrogate((char) c)), "lone surrogate leaked out of readUtf");
        assertFalse(PlayerText.sanitize(p.name(), PlayerText.CULTIVAR_NAME_MAX).accepted());
    }

    static JournalActionPacket journal(String id, int action) {
        return JOURNAL.apply(buf(b -> {
            b.writeUtf(id, Short.MAX_VALUE);
            b.writeVarInt(action);
        }));
    }

    @Test
    void journalActionBoundaries() {
        String id128 = "wildroots:" + "a".repeat(BoundedReader.MAX_ID_LENGTH - "wildroots:".length());
        assertEquals(128, id128.length());
        assertEquals(id128, journal(id128, 0).entry().toString());
        assertSame(JournalActionPacket.MALFORMED, journal(id128 + "a", 0), "129 characters");
        assertSame(JournalActionPacket.MALFORMED, journal("Wildroots:Upper", 0), "invalid resource location");
        assertSame(JournalActionPacket.MALFORMED, journal("wildroots:a b", 0), "space in id");
        assertEquals(JournalActionPacket.MAX_ACTION, journal("wildroots:x", JournalActionPacket.MAX_ACTION).action());
        assertSame(JournalActionPacket.MALFORMED, journal("wildroots:x", JournalActionPacket.MAX_ACTION + 1));
        assertSame(JournalActionPacket.MALFORMED, journal("wildroots:x", -1));
        assertSame(JournalActionPacket.MALFORMED, journal("wildroots:x", Integer.MIN_VALUE));
        assertSame(JournalActionPacket.MALFORMED, JOURNAL.apply(buf(b -> {
            b.writeUtf("wildroots:x");
            b.writeBytes(new byte[] {(byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0x01});
        })), "VarInt longer than 5 bytes");
        assertSame(JournalActionPacket.MALFORMED, JOURNAL.apply(buf(b -> b.writeUtf("wildroots:x"))), "missing action");
    }

    @Test
    void largestValidC2sPayloadsStayUnderTheVanillaLimit() {
        FriendlyByteBuf n = buf(b -> new NameSelectionPacket(Long.MIN_VALUE + 1, cp(0x1D400).repeat(24)).encode(b));
        FriendlyByteBuf j = buf(b -> new JournalActionPacket(
                Ids.mod("a".repeat(BoundedReader.MAX_ID_LENGTH - 10)), JournalActionPacket.MAX_ACTION).encode(b));
        assertTrue(n.readableBytes() <= BoundedReader.MAX_C2S_PAYLOAD);
        assertTrue(j.readableBytes() <= BoundedReader.MAX_C2S_PAYLOAD);
    }
}
