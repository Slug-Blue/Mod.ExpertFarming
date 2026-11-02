package io.github.slugblue.wildroots.core.safety;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.LongArrayTag;
import net.minecraft.nbt.StringTag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** WR-122, SEC-07: only well-formed disp lines survive encode and decode. */
class DispCodecTest {

    @Test
    void roundTrip() {
        List<DispCodec.Line> in = List.of(DispCodec.Line.of("wildroots.tooltip.lot.mother", "0.45"),
                DispCodec.Line.of("wildroots.tooltip.lot.generation", "12"),
                DispCodec.Line.of("wildroots.tooltip.lot.from_wild"));
        assertEquals(in, DispCodec.decode(DispCodec.encode(in)));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "minecraft.tooltip", "wildroots.", "Wildroots.x", "wildroots.Upper", "wildroots.a b",
        "wildroots.a/b", "wildroots.a-b", "wildroots.а", "wildroots.x\n"})
    void invalidKeysAreDropped(String key) {
        assertFalse(DispCodec.valid(DispCodec.Line.of(key)));
        assertTrue(DispCodec.encode(List.of(DispCodec.Line.of(key))).isEmpty());
    }

    @Test
    void keyLengthBoundary() {
        assertTrue(DispCodec.valid(DispCodec.Line.of("wildroots." + "a".repeat(96))));
        assertFalse(DispCodec.valid(DispCodec.Line.of("wildroots." + "a".repeat(97))));
    }

    @Test
    void argumentRules() {
        String k = "wildroots.tooltip.x";
        assertTrue(DispCodec.valid(DispCodec.Line.of(k, "1", "2", "3", "4")));
        assertFalse(DispCodec.valid(DispCodec.Line.of(k, "1", "2", "3", "4", "5")));
        assertTrue(DispCodec.valid(DispCodec.Line.of(k, "x".repeat(64))));
        assertFalse(DispCodec.valid(DispCodec.Line.of(k, "x".repeat(65))));
        assertFalse(DispCodec.valid(DispCodec.Line.of(k, "a‮b")));
        assertFalse(DispCodec.valid(DispCodec.Line.of(k, "a\nb")));
        assertFalse(DispCodec.valid(DispCodec.Line.of(k, "§kx")));
    }

    @Test
    void atMostTwentyFourLines() {
        List<DispCodec.Line> in = new ArrayList<>();
        for (int i = 0; i < 30; i++) {
            in.add(DispCodec.Line.of("wildroots.tooltip.l" + i));
        }
        assertEquals(24, DispCodec.encode(in).size());
        ListTag raw = new ListTag();
        for (int i = 0; i < 30; i++) {
            raw.add(StringTag.valueOf("tooltip.l" + i));
        }
        assertEquals(24, DispCodec.decode(raw).size());
    }

    @Test
    void storedFormIsOneStringPerLine() {
        ListTag t = DispCodec.encode(List.of(DispCodec.Line.of("wildroots.tooltip.line", "Golden Line", "4")));
        assertEquals("tooltip.lineGolden Line4", t.getString(0));
    }

    @Test
    void untrustedNbtNeverThrows() {
        ListTag raw = new ListTag();
        raw.add(StringTag.valueOf("tooltip.a12345")); // five arguments
        raw.add(StringTag.valueOf("Tooltip.Upper"));
        raw.add(StringTag.valueOf("tooltip.ba‮b"));
        raw.add(StringTag.valueOf(""));
        raw.add(StringTag.valueOf("tooltip.c12"));
        raw.add(StringTag.valueOf("tooltip.d"));
        assertEquals(List.of(DispCodec.Line.of("wildroots.tooltip.c", "12"), DispCodec.Line.of("wildroots.tooltip.d", "", "")),
                DispCodec.decode(raw));
        assertTrue(DispCodec.decode(StringTag.valueOf("x")).isEmpty());
        assertTrue(DispCodec.decode(null).isEmpty());
        ListTag compounds = new ListTag();
        compounds.add(new CompoundTag());
        assertTrue(DispCodec.decode(compounds).isEmpty());
        ListTag longs = new ListTag();
        longs.add(new LongArrayTag(new long[] {1}));
        assertTrue(DispCodec.decode(longs).isEmpty());
    }
}
