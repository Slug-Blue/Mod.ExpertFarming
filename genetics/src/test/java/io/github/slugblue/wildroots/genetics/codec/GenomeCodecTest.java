package io.github.slugblue.wildroots.genetics.codec;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.slugblue.wildroots.genetics.model.Genome;
import io.github.slugblue.wildroots.genetics.model.GenomeLayout;
import io.github.slugblue.wildroots.genetics.model.Locus;
import io.github.slugblue.wildroots.genetics.model.TraitId;
import io.github.slugblue.wildroots.genetics.model.TraitKind;
import io.github.slugblue.wildroots.genetics.random.SplitMix64;
import io.github.slugblue.wildroots.genetics.testutil.Carrot;
import io.github.slugblue.wildroots.genetics.testutil.RandomGenomes;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

/** WR-103: GenomeRecord codec (docs/05-architecture.md §5.1–5.2, ADR-0002, SEC-09, SEC-10). */
class GenomeCodecTest {

    private static final String SP = Carrot.SPECIES.id();
    private static final GenomeLayout L = Carrot.LAYOUT;

    @Test
    void encodeDecodeIsIdentityOnTenThousandRandomCarrots() {
        SplitMix64 rng = new SplitMix64(103);
        for (int i = 0; i < 10_000; i++) {
            Genome g = RandomGenomes.carrot(rng);
            GenomeRecord r = GenomeCodec.encode(g);
            assertEquals(GenomeCodec.CURRENT_VERSION, r.v());
            assertEquals(L.hash(), r.lh());
            DecodeResult d = GenomeCodec.decode(r, SP, L);
            assertInstanceOf(DecodeResult.Valid.class, d, () -> d.toString());
            assertEquals(g, d.genome());
        }
    }

    @Test
    void wrongLengthIsInert() {
        Genome g = RandomGenomes.carrot(new SplitMix64(1));
        long[] a = g.bits();
        assertInert(new GenomeRecord(1, SP, L.hash(), Arrays.copyOf(a, 2)));
        assertInert(new GenomeRecord(1, SP, L.hash(), Arrays.copyOf(a, 4)));
        assertInert(new GenomeRecord(1, SP, L.hash(), new long[0]));
        assertInert(new GenomeRecord(1, SP, L.hash(), null));
    }

    @Test
    void moreThanEightLongsIsInertEvenBeforeLayoutCheck() {
        assertInert(new GenomeRecord(1, SP, L.hash(), new long[9]));
        assertInert(new GenomeRecord(1, SP, L.hash(), new long[100_000]));
        assertTrue(GenomeCodec.precheck(new GenomeRecord(1, SP, 0, new long[9]), SP).contains("> 8"));
    }

    @Test
    void versionOutsideOneToCurrentIsInert() {
        long[] a = RandomGenomes.carrot(new SplitMix64(2)).bits();
        assertInert(new GenomeRecord(0, SP, L.hash(), a));
        assertInert(new GenomeRecord(-5, SP, L.hash(), a));
        assertInert(new GenomeRecord(GenomeCodec.CURRENT_VERSION + 1, SP, L.hash(), a));
        assertInert(new GenomeRecord(Integer.MAX_VALUE, SP, L.hash(), a));
    }

    @Test
    void damagedContentIsInertWithoutExceptions() {
        long[] a = RandomGenomes.carrot(new SplitMix64(3)).bits();
        assertInert(new GenomeRecord(1, "wildroots:other", L.hash(), a));
        assertInert(new GenomeRecord(1, null, L.hash(), a));
        assertInert(new GenomeRecord(1, SP, L.hash() ^ 1, a));
        long[] tail = a.clone();
        tail[2] |= 1L << 63; // bit 191 is past the 142 used bits
        assertInert(new GenomeRecord(1, SP, L.hash(), tail));
        long[] badQuant = new long[3];
        Arrays.fill(badQuant, 0);
        badQuant[0] = 0b11; // quantitative allele value 3
        assertInert(new GenomeRecord(1, SP, L.hash(), badQuant));
        SplitMix64 rng = new SplitMix64(4);
        for (int i = 0; i < 10_000; i++) {
            long[] junk = new long[rng.nextInt(10)];
            for (int j = 0; j < junk.length; j++) {
                junk[j] = rng.nextLong();
            }
            GenomeCodec.decode(new GenomeRecord(rng.nextInt(4) - 1, SP, rng.nextBoolean() ? L.hash() : rng.nextLong(),
                    junk), SP, L);
        }
    }

    @Test
    void tenAllelesTakeFourBitsPerAllele() {
        GenomeLayout l = GenomeLayout.of(List.of(
                new GenomeLayout.SectionSpec(TraitId.of("yield"), TraitKind.QUANTITATIVE, 6, 3),
                new GenomeLayout.SectionSpec(TraitId.of("self_incompat"), TraitKind.QUALITATIVE, 1, 10)));
        Locus s = l.section(TraitId.of("self_incompat"));
        assertEquals(4, s.bitsPerAllele());
        assertEquals(8, s.bits());
        assertEquals(24 + 8, l.totalBits());
        for (int x = 0; x < 10; x++) {
            Genome g = Genome.builder("sp", l).set(s, 0, 0, x).set(s, 0, 1, 9 - x).build();
            Genome back = GenomeCodec.decode(GenomeCodec.encode(g), "sp", l).genome();
            assertEquals(x, back.allele(s, 0, 0));
            assertEquals(9 - x, back.allele(s, 0, 1));
        }
        long[] eleven = new long[1];
        GenomeLayout.set(eleven, s, 0, 0, 9);
        eleven[0] |= 0b1010L << s.bitOf(0, 1); // allele value 10 is out of range
        assertInert(new GenomeRecord(1, "sp", l.hash(), eleven), "sp", l);
    }

    @Test
    void layoutHashIsALong() throws NoSuchMethodException {
        assertEquals(long.class, GenomeRecord.class.getMethod("lh").getReturnType());
        assertEquals(long.class, FatherRecord.class.getMethod("lh").getReturnType());
        long big = 0x8000_0001_2345_6789L;
        assertEquals(big, new GenomeRecord(1, SP, big, new long[3]).lh());
        assertTrue(Long.compareUnsigned(L.hash(), 0xFFFF_FFFFL) > 0 || L.hash() < 0,
                "carrot lh needs more than 32 bits");
    }

    @Test
    void fatherIsStoredAsLhAndBitsAndDecodedWithLotSpeciesAndVersion() throws NoSuchMethodException {
        Genome father = RandomGenomes.carrot(new SplitMix64(5));
        FatherRecord f = GenomeCodec.encodeFather(father);
        assertEquals(2, FatherRecord.class.getDeclaredFields().length, "father keeps only lh and a");
        assertEquals(L.hash(), f.lh());
        DecodeResult d = GenomeCodec.decodeFather(f, GenomeCodec.CURRENT_VERSION, SP, L);
        assertEquals(father, d.genome());
        assertInstanceOf(DecodeResult.Inert.class, GenomeCodec.decodeFather(f, 99, SP, L));
        assertInstanceOf(DecodeResult.Inert.class, GenomeCodec.decodeFather(null, 1, SP, L));
    }

    private static void assertInert(GenomeRecord r) {
        assertInert(r, SP, L);
    }

    private static void assertInert(GenomeRecord r, String sp, GenomeLayout l) {
        DecodeResult d = GenomeCodec.decode(r, sp, l);
        assertInstanceOf(DecodeResult.Inert.class, d, () -> "expected inert: " + r);
    }
}
