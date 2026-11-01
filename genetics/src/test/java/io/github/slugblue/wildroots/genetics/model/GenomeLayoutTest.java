package io.github.slugblue.wildroots.genetics.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.slugblue.wildroots.genetics.api.SpeciesDef;
import io.github.slugblue.wildroots.genetics.testutil.Carrot;
import io.github.slugblue.wildroots.genetics.testutil.DefJson;
import io.github.slugblue.wildroots.genetics.testutil.MiniJson;
import java.util.List;
import org.junit.jupiter.api.Test;

/** WR-102: carrot layout of GDD §3.9 and the 64-bit layout hash (docs/05-architecture.md §5.1, S10). */
class GenomeLayoutTest {

    @Test
    void carrotIs142BitsInThreeLongs() {
        GenomeLayout l = Carrot.LAYOUT;
        assertEquals(142, l.totalBits());
        assertEquals(3, l.longCount());
        assertEquals(116, l.quantitative().stream().mapToInt(Locus::bits).sum());
        assertEquals(16, l.load().bits());
        assertEquals(10, l.qualitative().stream().mapToInt(Locus::bits).sum());
    }

    @Test
    void sectionsAreQuantitativeByIdThenLoadThenQualitativeById() {
        List<String> order = Carrot.LAYOUT.sections().stream().map(s -> s.kind().code() + ":" + s.trait()).toList();
        assertEquals(List.of("q:cold_tolerance", "q:drought_tolerance", "q:flavor", "q:fruit_size",
                "q:growth_speed", "q:heat_tolerance", "q:yield", "l:load", "c:bitterness", "c:color_p",
                "c:color_y1", "c:color_y2", "c:dormancy"), order);
        int offset = 0;
        for (Locus s : Carrot.LAYOUT.sections()) {
            assertEquals(offset, s.bitOffset(), s.toString());
            offset += s.bits();
        }
    }

    @Test
    void carrotLayoutHashMatchesGolden() {
        String[] golden = MiniJson.resourceText("/golden/layout_wild_carrot.txt").trim().split("\t");
        assertEquals(golden[1], Carrot.LAYOUT.canonical());
        assertEquals(Long.parseUnsignedLong(golden[0], 16), Carrot.LAYOUT.hash());
    }

    @Test
    void permutedJsonKeysGiveTheSameHash() {
        SpeciesDef permuted = DefJson.species("/species/wild_carrot_permuted.json");
        assertEquals(Carrot.LAYOUT.canonical(), GenomeLayout.of(permuted).canonical());
        assertEquals(Carrot.LAYOUT.hash(), GenomeLayout.of(permuted).hash());
    }

    @Test
    void canonicalStringCarriesAlleleCountOfQualitativeTraits() {
        GenomeLayout two = GenomeLayout.of(List.of(new GenomeLayout.SectionSpec(TraitId.of("s"),
                TraitKind.QUALITATIVE, 1, 2)));
        GenomeLayout ten = GenomeLayout.of(List.of(new GenomeLayout.SectionSpec(TraitId.of("s"),
                TraitKind.QUALITATIVE, 1, 10)));
        assertEquals("c:s:1:2", two.canonical());
        assertEquals("c:s:1:10", ten.canonical());
        assertTrue(two.hash() != ten.hash());
    }

    @Test
    void parseRoundTripsAndRejectsGarbage() {
        assertEquals(Carrot.LAYOUT, GenomeLayout.parse(Carrot.LAYOUT.canonical()).orElseThrow());
        for (String bad : new String[] {"", "x:1", "q:a", "q:a:0", "c:s:1:17", "q:b:2|q:a:2", "q:a:2|q:a:2",
                "q:a:99999999999", "q:big:64|q:big2:64|q:big3:64"}) {
            assertTrue(GenomeLayout.parse(bad).isEmpty(), bad);
        }
    }

    @Test
    void allelesReadBackAcrossWordBoundaries() {
        GenomeLayout l = GenomeLayout.of(List.of(new GenomeLayout.SectionSpec(TraitId.of("a"),
                TraitKind.QUANTITATIVE, 15, 3), new GenomeLayout.SectionSpec(TraitId.of("b"),
                TraitKind.QUALITATIVE, 1, 2), new GenomeLayout.SectionSpec(TraitId.of("z"),
                TraitKind.QUALITATIVE, 1, 10)));
        long[] bits = new long[l.longCount()];
        Locus a = l.section(TraitId.of("a"));
        Locus z = l.section(TraitId.of("z"));
        assertEquals(62, z.bitOffset(), "the S-allele straddles the first word boundary");
        for (int i = 0; i < 15; i++) {
            GenomeLayout.set(bits, a, i, 0, i % 3);
            GenomeLayout.set(bits, a, i, 1, (i + 1) % 3);
        }
        GenomeLayout.set(bits, z, 0, 0, 9);
        GenomeLayout.set(bits, z, 0, 1, 5);
        for (int i = 0; i < 15; i++) {
            assertEquals(i % 3, GenomeLayout.get(bits, a, i, 0));
            assertEquals((i + 1) % 3, GenomeLayout.get(bits, a, i, 1));
        }
        assertEquals(9, GenomeLayout.get(bits, z, 0, 0));
        assertEquals(5, GenomeLayout.get(bits, z, 0, 1));
    }
}
