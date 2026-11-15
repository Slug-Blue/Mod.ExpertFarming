package io.github.slugblue.wildroots.lens;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.slugblue.wildroots.core.safety.DispCodec;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** Review 1, M1: the compact tier-1 row round-trips, refuses malformed input and degrades by priority. */
class LensRowTest {

    static final LensRow FULL = new LensRow("s4*y3g2f3Wc3h2d2", 'd', 'B', true, Optional.of("eastern_carrot"), false);

    @Test
    void roundTripAndSize() {
        DispCodec.Line l = FULL.line();
        assertTrue(DispCodec.valid(l), l.toString());
        assertEquals(Optional.of(FULL), LensRow.parse(l));
        List<LensRow.Token> t = LensRow.tokens(FULL.traits());
        assertEquals(7, t.size());
        assertEquals(new LensRow.Token("fruit_size", 4, "size", true), t.get(0));
        assertEquals(new LensRow.Token("flavor", 3, "sweetness", false), t.get(3));
        int stored = DispCodec.encode(List.of(l)).getString(0).getBytes(java.nio.charset.StandardCharsets.UTF_8).length;
        assertTrue(stored <= 64, "row of " + stored + " bytes");
    }

    @Test
    void malformedRowsAreNotLensRows() {
        assertTrue(LensRow.parse(DispCodec.Line.of(LensRow.KEY, "s9", "d--")).isEmpty(), "step 9");
        assertTrue(LensRow.parse(DispCodec.Line.of(LensRow.KEY, "q3", "d--")).isEmpty(), "unknown trait");
        assertTrue(LensRow.parse(DispCodec.Line.of(LensRow.KEY, "s3Q", "d--")).isEmpty(), "unknown scale");
        assertTrue(LensRow.parse(DispCodec.Line.of(LensRow.KEY, "s3", "x--")).isEmpty(), "bad flag");
        assertTrue(LensRow.parse(DispCodec.Line.of(LensRow.KEY, "s3")).isEmpty(), "no flags");
        assertTrue(LensRow.parse(DispCodec.Line.of("wildroots.tooltip.trait", "s3", "d--")).isEmpty(), "other key");
        assertEquals(Optional.of(new LensRow("", '-', '-', false, Optional.empty(), true)),
                LensRow.parse(DispCodec.Line.of(LensRow.KEY, "", "---+")));
    }

    @Test
    void degradationsDropPollinationAndInbreedingFirstThenTraitsThenLooksLike() {
        List<LensRow> d = FULL.degradations();
        assertEquals(FULL, d.get(0));
        LensRow second = d.get(1);
        assertEquals(FULL.traits(), second.traits());
        assertEquals('-', second.pollination());
        assertFalse(second.ailing());
        assertEquals(FULL.looksLike(), second.looksLike());
        assertTrue(second.more());
        assertEquals("s4*y3g2f3Wc3h2", d.get(2).traits());
        assertTrue(d.stream().skip(1).allMatch(LensRow::more), "a smaller row does not say 'and more'");
        LensRow last = d.get(d.size() - 1);
        assertEquals("", last.traits());
        assertEquals('-', last.bitter());
        assertTrue(last.looksLike().isEmpty());
        assertTrue(d.stream().allMatch(r -> DispCodec.valid(r.line())));
    }

    @Test
    void bandsRoundTripThroughDispAndDropFirst() {
        LensRow banded = FULL.withBands(new int[][] {{3, 5}, {2, 3}, {2, 2}, {3, 3}, {2, 4}, {1, 2}, {2, 2}});
        assertEquals("35232233241222", banded.bands());
        DispCodec.Line l = banded.line();
        assertTrue(DispCodec.valid(l), l.toString());
        assertEquals("dBa~35232233241222", l.args().get(1));
        // through the stored NBT and back
        List<DispCodec.Line> back = DispCodec.decode(DispCodec.encode(List.of(l)));
        assertEquals(Optional.of(banded), LensRow.parse(back.get(0)));
        assertTrue(java.util.Arrays.equals(new int[] {1, 2}, banded.band(5).orElseThrow()));
        assertTrue(banded.band(7).isEmpty());
        assertTrue(FULL.band(0).isEmpty());
        // the band is the first thing a full lot gives up, then the old order
        List<LensRow> d = banded.degradations();
        assertEquals(banded, d.get(0));
        assertEquals(FULL, d.get(1));
        assertEquals(FULL.degradations().subList(1, FULL.degradations().size()), d.subList(2, d.size()));
        // a band that does not fit the tokens is dropped
        assertEquals("", FULL.withBands(new int[][] {{1, 2}}).bands());
        assertEquals("", new LensRow("", '-', '-', false, Optional.empty(), true).withBands(new int[0][]).bands());
        // 14 traits with a band still fit one disp argument
        String all = "s3y3g3f3c3h3d3u3l3t3i3m3b3a3";
        int[][] b = new int[14][];
        java.util.Arrays.fill(b, new int[] {1, 5});
        LensRow wide = new LensRow(all, 'w', 'N', true, Optional.of("x".repeat(64)), true).withBands(b);
        assertTrue(DispCodec.valid(wide.line()), wide.line().toString());
        assertEquals(Optional.of(wide), LensRow.parse(wide.line()));
    }

    @Test
    void malformedBandsAreNotLensRows() {
        assertTrue(LensRow.parse(DispCodec.Line.of(LensRow.KEY, "s3y2", "d--~33")).isEmpty(), "too short");
        assertTrue(LensRow.parse(DispCodec.Line.of(LensRow.KEY, "s3y2", "d--~3322x")).isEmpty(), "too long");
        assertTrue(LensRow.parse(DispCodec.Line.of(LensRow.KEY, "s3y2", "d--~3261")).isEmpty(), "step 6");
        assertTrue(LensRow.parse(DispCodec.Line.of(LensRow.KEY, "s3y2", "d--~4322")).isEmpty(), "low above high");
        assertTrue(LensRow.parse(DispCodec.Line.of(LensRow.KEY, "", "d--~")).isEmpty(), "band without traits");
        assertTrue(LensRow.parse(DispCodec.Line.of(LensRow.KEY, "s3y2", "d--+~3322")).isPresent(), "with 'and more'");
    }
}
