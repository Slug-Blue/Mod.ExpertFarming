package io.github.slugblue.wildroots.genetics.codec;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.slugblue.wildroots.genetics.api.Environment;
import io.github.slugblue.wildroots.genetics.api.Phenotype;
import io.github.slugblue.wildroots.genetics.api.SpeciesDef;
import io.github.slugblue.wildroots.genetics.model.Genome;
import io.github.slugblue.wildroots.genetics.model.GenomeLayout;
import io.github.slugblue.wildroots.genetics.model.Locus;
import io.github.slugblue.wildroots.genetics.model.TraitId;
import io.github.slugblue.wildroots.genetics.phenotype.CompiledSpecies;
import io.github.slugblue.wildroots.genetics.phenotype.NoiseVector;
import io.github.slugblue.wildroots.genetics.phenotype.PhenotypeCalculator;
import io.github.slugblue.wildroots.genetics.random.SplitMix64;
import io.github.slugblue.wildroots.genetics.testutil.Carrot;
import io.github.slugblue.wildroots.genetics.testutil.RandomGenomes;
import org.junit.jupiter.api.Test;

/**
 * WR-701: the released M1a layout (no {@code color_r}) migrates into the layout with the colour locus; the
 * locus is added as {@code RR} through {@code legacy_default}, so no saved plant changes colour; and the
 * layout sizes of the doc (142 to 144 bits, three longs) hold.
 */
class ColourMigrationTest {

    static final SpeciesDef M1A = MigrationGoldens.species("wild_carrot_m1a");
    static final GenomeLayout OLD = GenomeLayout.of(M1A);
    static final GenomeLayout NEW = Carrot.LAYOUT;
    static final long OLD_LH = 0xcee0bc2c52916ae3L;
    static final long NEW_LH = 0x7b203d634fab35cbL;
    static final TraitId R = TraitId.of("color_r");

    static LayoutDictionary dict() {
        LayoutDictionary d = new LayoutDictionary();
        d.register(M1A.id(), OLD);
        d.register(Carrot.SPECIES.id(), NEW);
        return d;
    }

    @Test
    void layoutSizesAndHashes() {
        assertEquals(OLD_LH, OLD.hash(), "the released layout is frozen");
        assertEquals(142, OLD.totalBits());
        assertEquals(3, OLD.longCount());
        assertEquals(NEW_LH, NEW.hash());
        assertEquals(144, NEW.totalBits());
        assertEquals(3, NEW.longCount(), "the new locus fits the same three longs");
        assertEquals(OLD.canonical().replace("c:color_p:1:2|", "c:color_p:1:2|c:color_r:1:2|"), NEW.canonical());
    }

    /** Colour of M1A rules (GDD §7.3 before WR-701): purple P_, orange y1y1 y2y2, yellow y1y1, else white. */
    static int oldColour(Phenotype p) {
        if (p.domestic(Carrot.P)) {
            return 3;
        }
        if (p.domestic(Carrot.Y1)) {
            return p.domestic(Carrot.Y2) ? 2 : 1;
        }
        return 0;
    }

    @Test
    void oldGenomesKeepEveryAlleleAndTheirColourAndGetRR() {
        CompiledSpecies oldCs = new CompiledSpecies(M1A);
        CompiledSpecies newCs = new CompiledSpecies(Carrot.SPECIES);
        SplitMix64 rng = new SplitMix64(701);
        int[] seen = new int[5];
        for (int i = 0; i < 3000; i++) {
            Genome old = RandomGenomes.uniform(M1A.id(), OLD, rng);
            GenomeRecord rec = GenomeCodec.encode(old);
            assertEquals(OLD_LH, rec.lh());
            GenomeMigrator.Result r = GenomeMigrator.migrate(rec, Carrot.SPECIES, NEW, dict());
            assertEquals(GenomeMigrator.Status.MIGRATED, r.status(), "genome " + i);
            assertTrue(r.warnings().contains("new trait color_r: legacy default"), r.warnings()::toString);
            Genome now = r.genome().orElseThrow();
            assertEquals(NEW_LH, now.layout().hash());
            Locus lr = NEW.section(R);
            assertEquals(0, now.allele(lr, 0, 0), "RR " + i);
            assertEquals(0, now.allele(lr, 0, 1), "RR " + i);
            for (Locus s : OLD.sections()) {
                Locus t = NEW.section(s.trait());
                for (int l = 0; l < s.loci(); l++) {
                    for (int w = 0; w < 2; w++) {
                        assertEquals(old.allele(s, l, w), now.allele(t, l, w), s.trait() + " " + l + "/" + w);
                    }
                }
            }
            Phenotype before = PhenotypeCalculator.compute(oldCs, old, Environment.neutral(0.6), NoiseVector.zero(7));
            Phenotype after = PhenotypeCalculator.compute(newCs, now, Environment.neutral(0.6), NoiseVector.zero(7));
            int colour = oldColour(before);
            assertEquals(colour, after.colour(), "colour of old genome " + i);
            seen[after.colour()]++;
        }
        assertTrue(seen[0] > 100 && seen[1] > 20 && seen[3] > 100, "the sample covers the colours: "
                + java.util.Arrays.toString(seen));
        assertEquals(0, seen[4], "no old plant is red: RR");
    }

    /** Without a legacy default the new locus would get background values; with rr about 2% of the plants turn red. */
    @Test
    void withoutTheLegacyDefaultSomeOldPlantsWouldTurnRed() {
        SpeciesDef noLegacy = new SpeciesDef(Carrot.SPECIES.id(), Carrot.SPECIES.tempMin(), Carrot.SPECIES.tempMax(),
                Carrot.SPECIES.quantitative(), Carrot.SPECIES.tradeoffs(), Carrot.SPECIES.budget(),
                Carrot.SPECIES.wild(), Carrot.SPECIES.wildF(),
                Carrot.SPECIES.qualitative().stream().map(q -> q.id().equals(R)
                        ? new io.github.slugblue.wildroots.genetics.api.QualTrait(q.id(), q.dominant(), q.wildFreq(),
                                q.toxic(), q.alleles(), q.selfIncompat(), q.patchAlleles()) : q).toList(),
                Carrot.SPECIES.starterFreq(), Carrot.SPECIES.load(), Carrot.SPECIES.mutation(),
                Carrot.SPECIES.pollination(), Carrot.SPECIES.timing(), Carrot.SPECIES.qualityAxes(),
                Carrot.SPECIES.colours());
        SplitMix64 rng = new SplitMix64(7011);
        int rr = 0;
        for (int i = 0; i < 3000; i++) {
            Genome old = RandomGenomes.uniform(M1A.id(), OLD, rng);
            Genome now = GenomeMigrator.migrate(GenomeCodec.encode(old), noLegacy, NEW, dict()).genome().orElseThrow();
            rr += now.allele(NEW.section(R), 0, 0) + now.allele(NEW.section(R), 0, 1) == 2 ? 1 : 0;
        }
        assertTrue(rr > 30, "background rr: " + rr);
        assertNotEquals(0, rr);
    }
}
