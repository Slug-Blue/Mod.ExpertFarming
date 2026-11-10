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
}
