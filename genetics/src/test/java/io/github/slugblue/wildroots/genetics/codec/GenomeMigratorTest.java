package io.github.slugblue.wildroots.genetics.codec;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.slugblue.wildroots.genetics.api.AlleleFreq;
import io.github.slugblue.wildroots.genetics.api.Budget;
import io.github.slugblue.wildroots.genetics.api.Dominance;
import io.github.slugblue.wildroots.genetics.api.LoadDef;
import io.github.slugblue.wildroots.genetics.api.MutationRates;
import io.github.slugblue.wildroots.genetics.api.Pollination;
import io.github.slugblue.wildroots.genetics.api.QualTrait;
import io.github.slugblue.wildroots.genetics.api.QuantTrait;
import io.github.slugblue.wildroots.genetics.api.SpeciesDef;
import io.github.slugblue.wildroots.genetics.api.Timing;
import io.github.slugblue.wildroots.genetics.api.WildFrequencies;
import io.github.slugblue.wildroots.genetics.model.Genome;
import io.github.slugblue.wildroots.genetics.model.GenomeLayout;
import io.github.slugblue.wildroots.genetics.model.GenomeLayout.SectionSpec;
import io.github.slugblue.wildroots.genetics.model.Locus;
import io.github.slugblue.wildroots.genetics.model.TraitId;
import io.github.slugblue.wildroots.genetics.model.TraitKind;
import io.github.slugblue.wildroots.genetics.random.SplitMix64;
import io.github.slugblue.wildroots.genetics.testutil.Carrot;
import io.github.slugblue.wildroots.genetics.testutil.RandomGenomes;
import io.github.slugblue.wildroots.genetics.validation.Problem;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** WR-111: genome migration (architecture §5.3, ADR-0002 rev. 2, S10). */
class GenomeMigratorTest {

    static final SpeciesDef CARROT = Carrot.SPECIES;
    static final SpeciesDef YIELD8 = MigrationGoldens.species("wild_carrot_yield8");

    static LayoutDictionary dict(SpeciesDef... species) {
        LayoutDictionary d = new LayoutDictionary();
        for (SpeciesDef s : species) {
            assertTrue(d.register(s.id(), GenomeLayout.of(s)).isEmpty());
        }
        return d;
    }

    @Test
    void yieldSixToEightKeepsOldAllelesAndFillsNewLociDeterministically() {
        GenomeLayout cur = GenomeLayout.of(YIELD8);
        LayoutDictionary d = dict(CARROT, YIELD8);
        SplitMix64 rng = new SplitMix64(111);
        Locus oldYield = Carrot.LAYOUT.section(Carrot.YIELD);
        Locus newYield = cur.section(Carrot.YIELD);
        int[] newLociHist = new int[3];
        for (int i = 0; i < 1000; i++) {
            Genome g = RandomGenomes.carrot(rng);
            GenomeRecord r = GenomeCodec.encode(g);
            GenomeMigrator.Result a = GenomeMigrator.migrate(r, YIELD8, cur, d);
            GenomeMigrator.Result b = GenomeMigrator.migrate(r, YIELD8, cur, d);
            assertEquals(GenomeMigrator.Status.MIGRATED, a.status());
            assertEquals(a.genome(), b.genome(), "deterministic");
            Genome m = a.genome().orElseThrow();
            for (Locus s : Carrot.LAYOUT.sections()) {
                Locus n = cur.section(s.trait());
                for (int l = 0; l < s.loci(); l++) {
                    for (int w = 0; w < 2; w++) {
                        assertEquals(g.allele(s, l, w), m.allele(n, l, w));
                    }
                }
            }
            for (int l = oldYield.loci(); l < newYield.loci(); l++) {
                newLociHist[m.allele(newYield, l, 0)]++;
                newLociHist[m.allele(newYield, l, 1)]++;
            }
            assertTrue(a.warnings().contains("trait yield new loci 6..7: background"), a.warnings().toString());
        }
        // new loci follow the background frequencies 0.60 / 0.35 / 0.05 (4000 alleles)
        assertEquals(0.60, newLociHist[0] / 4000.0, 0.03);
        assertEquals(0.35, newLociHist[1] / 4000.0, 0.03);
    }

    @Test
    void newLociDependOnlyOnGenomeKeyTraitLocusAllele() {
        Locus y = GenomeLayout.of(YIELD8).section(Carrot.YIELD);
        assertEquals(GenomeMigrator.background(YIELD8, y, 5, 7, 1), GenomeMigrator.background(YIELD8, y, 5, 7, 1));
        int differ = 0;
        for (long key = 0; key < 200; key++) {
            differ += GenomeMigrator.background(YIELD8, y, key, 7, 0) != GenomeMigrator.background(YIELD8, y, key + 1000, 7, 0) ? 1 : 0;
        }
        assertTrue(differ > 30);
    }

    @Test
    void removedTraitIsDroppedWithWarning() {
        SpeciesDef noFlavor = MigrationGoldens.species("wild_carrot_noflavor");
        GenomeMigrator.Result r = GenomeMigrator.migrate(GenomeCodec.encode(RandomGenomes.carrot(new SplitMix64(1))),
                noFlavor, GenomeLayout.of(noFlavor), dict(CARROT));
        assertEquals(GenomeMigrator.Status.MIGRATED, r.status());
        assertTrue(r.warnings().contains("trait flavor removed: dropped"));
    }

    @Test
    void unknownLayoutRegeneratesWildWithoutException() {
        Genome g8 = RandomGenomes.uniform(YIELD8.id(), GenomeLayout.of(YIELD8), new SplitMix64(2));
        GenomeMigrator.Result r = GenomeMigrator.migrate(GenomeCodec.encode(g8), CARROT, Carrot.LAYOUT,
                new LayoutDictionary());
        assertEquals(GenomeMigrator.Status.REGENERATED_UNKNOWN_LAYOUT, r.status());
        assertEquals(1, r.warnings().size());
        assertEquals(Carrot.LAYOUT, r.genome().orElseThrow().layout());
        assertEquals(r.genome(), GenomeMigrator.migrate(GenomeCodec.encode(g8), CARROT, Carrot.LAYOUT,
                new LayoutDictionary()).genome(), "regeneration is deterministic");
    }

    @Test
    void dictionaryIsKeyedBySpeciesAndHashAndRejectsCollisions() {
        LayoutDictionary d = new LayoutDictionary();
        assertTrue(d.register(new LayoutDescriptor("wildroots:a", 42L, "q:x:1")).isEmpty());
        assertTrue(d.register(new LayoutDescriptor("wildroots:a", 42L, "q:x:1")).isEmpty(), "same string again");
        Optional<Problem> clash = d.register(new LayoutDescriptor("wildroots:a", 42L, "q:y:1"));
        assertTrue(clash.isPresent() && clash.get().isError(), "same lh, other string of the same species");
        assertTrue(d.register(new LayoutDescriptor("wildroots:b", 42L, "q:y:1")).isEmpty(), "other species");
        assertEquals(List.of(new LayoutDescriptor("wildroots:a", 42L, "q:x:1"),
                new LayoutDescriptor("wildroots:b", 42L, "q:y:1")), d.entries());
        // lookup re-checks the hash: a forged entry under a wrong lh is not used
        assertTrue(d.lookup("wildroots:a", 42L).isEmpty());
        assertTrue(d.lookup(CARROT.id(), Carrot.LAYOUT.hash()).isEmpty());
        d.register(CARROT.id(), Carrot.LAYOUT);
        assertEquals(Carrot.LAYOUT, d.lookup(CARROT.id(), Carrot.LAYOUT.hash()).orElseThrow());
        assertTrue(d.lookup("wildroots:other", Carrot.LAYOUT.hash()).isEmpty());
    }

    @Test
    void narrowingAndKindChangeUseBackgroundWithWarningWideningKeeps() {
        SpeciesDef s10 = MigrationGoldens.species("wild_carrot_s10");
        SpeciesDef s6 = MigrationGoldens.species("wild_carrot_s6");
        SpeciesDef s16 = MigrationGoldens.species("wild_carrot_s16");
        TraitId sid = TraitId.of("self_incompat");
        GenomeLayout l10 = GenomeLayout.of(s10);
        Locus sl = l10.section(sid);
        Genome g = RandomGenomes.uniform(s10.id(), l10, new SplitMix64(3)).toBuilder().set(sl, 0, 0, 8)
                .set(sl, 0, 1, 3).build();
        GenomeMigrator.Result narrow = GenomeMigrator.migrate(GenomeCodec.encode(g), s6, GenomeLayout.of(s6),
                dict(s10));
        Genome n = narrow.genome().orElseThrow();
        Locus s6l = n.layout().section(sid);
        assertEquals(3, n.allele(s6l, 0, 1), "in range: kept");
        assertTrue(n.allele(s6l, 0, 0) < 6, "out of range: background");
        assertTrue(narrow.warnings().contains("trait self_incompat locus 0 allele 8 >= 6: background"));

        Genome g6 = RandomGenomes.uniform(s6.id(), GenomeLayout.of(s6), new SplitMix64(4));
        Genome wide = GenomeMigrator.migrate(GenomeCodec.encode(g6), s16, GenomeLayout.of(s16), dict(s6))
                .genome().orElseThrow();
        Locus a = GenomeLayout.of(s6).section(sid);
        Locus b = wide.layout().section(sid);
        assertEquals(4, b.bitsPerAllele());
        assertEquals(g6.allele(a, 0, 0), wide.allele(b, 0, 0));
        assertEquals(g6.allele(a, 0, 1), wide.allele(b, 0, 1));

        SpeciesDef dq = MigrationGoldens.species("wild_carrot_dormancy_quant");
        GenomeMigrator.Result kind = GenomeMigrator.migrate(GenomeCodec.encode(RandomGenomes.carrot(new SplitMix64(5))),
                dq, GenomeLayout.of(dq), dict(CARROT));
        assertTrue(kind.warnings().contains("trait dormancy changed kind QUALITATIVE -> QUANTITATIVE: background"));
    }

    @Test
    void futureVersionAndDamageAreInert() {
        GenomeRecord ok = GenomeCodec.encode(RandomGenomes.carrot(new SplitMix64(6)));
        GenomeRecord future = new GenomeRecord(2, ok.species(), ok.lh(), ok.a());
        assertEquals(GenomeMigrator.Status.INERT, GenomeMigrator.migrate(future, CARROT, Carrot.LAYOUT, dict()).status());
        GenomeRecord oldDamaged = new GenomeRecord(1, CARROT.id(), Carrot.LAYOUT.hash(), new long[] {-1, -1, -1});
        assertEquals(GenomeMigrator.Status.INERT,
                GenomeMigrator.migrate(oldDamaged, YIELD8, GenomeLayout.of(YIELD8), dict(CARROT)).status());
        assertEquals(GenomeMigrator.Status.INERT, GenomeMigrator.migrate(null, CARROT, Carrot.LAYOUT, dict()).status());
    }

    @Test
    void lotMotherAndFathersAllMigrate() {
        // nested genomes of one root: the mother is a full record, fathers are {lh, a} with the lot's sp and v
        LayoutDictionary d = dict(CARROT, YIELD8);
        GenomeLayout cur = GenomeLayout.of(YIELD8);
        SplitMix64 rng = new SplitMix64(7);
        GenomeRecord mother = GenomeCodec.encode(RandomGenomes.carrot(rng));
        List<FatherRecord> fathers = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            fathers.add(GenomeCodec.encodeFather(RandomGenomes.carrot(rng)));
        }
        assertEquals(GenomeMigrator.Status.MIGRATED, GenomeMigrator.migrate(mother, YIELD8, cur, d).status());
        for (FatherRecord f : fathers) {
            GenomeRecord full = f.withLot(mother.v(), mother.species());
            GenomeMigrator.Result r = GenomeMigrator.migrate(full, YIELD8, cur, d);
            assertEquals(GenomeMigrator.Status.MIGRATED, r.status());
            assertEquals(cur.hash(), GenomeCodec.encodeFather(r.genome().orElseThrow()).lh());
        }
    }

    // ------------------------------------------------------------------ property

    /** Synthetic species around a list of section specs (frequencies from Defaults-like values). */
    static SpeciesDef speciesOf(String id, List<SectionSpec> specs) {
        List<QuantTrait> q = new ArrayList<>();
        List<QualTrait> c = new ArrayList<>();
        int load = 0;
        for (SectionSpec s : specs) {
            switch (s.kind()) {
                case QUANTITATIVE -> q.add(new QuantTrait(s.trait(), s.loci(), 0.05, false));
                case LOAD -> load = s.loci();
                case QUALITATIVE -> c.add(new QualTrait(s.trait(), Dominance.WILD, 0.2, false, s.alleleCount(),
                        s.alleleCount() > 2, Math.min(6, s.alleleCount())));
            }
        }
        return new SpeciesDef(id, 0, 1, q, List.of(), Budget.NONE,
                new WildFrequencies(new AlleleFreq(0.6, 0.35, 0.05), new AlleleFreq(0.3, 0.4, 0.3), Map.of(),
                        new AlleleFreq(0.15, 0.4, 0.45), Map.of()), 0, c, Map.of(), new LoadDef(load, 0.25, 0.9),
                new MutationRates(0.004, 0.001), new Pollination("insect", true, 2, 0.5, Optional.empty(), 1),
                new Timing(10, 0), Map.of());
    }

    static List<SectionSpec> randomSpecs(SplitMix64 rng) {
        List<SectionSpec> out = new ArrayList<>();
        int bits = 0;
        int nq = 1 + rng.nextInt(8);
        for (int i = 0; i < nq; i++) {
            int loci = 1 + rng.nextInt(10);
            out.add(new SectionSpec(TraitId.of("q" + rng.nextInt(20)), TraitKind.QUANTITATIVE, loci, 3));
        }
        if (rng.nextBoolean()) {
            out.add(new SectionSpec(TraitId.LOAD, TraitKind.LOAD, 1 + rng.nextInt(8), 2));
        }
        int nc = rng.nextInt(5);
        for (int i = 0; i < nc; i++) {
            out.add(new SectionSpec(TraitId.of("c" + rng.nextInt(20)), TraitKind.QUALITATIVE, 1,
                    rng.nextInt(4) == 0 ? 2 + rng.nextInt(15) : 2));
        }
        return dedupe(out);
    }

    static List<SectionSpec> dedupe(List<SectionSpec> specs) {
        Map<TraitId, SectionSpec> m = new java.util.TreeMap<>();
        for (SectionSpec s : specs) {
            m.putIfAbsent(s.trait(), s);
        }
        List<SectionSpec> out = new ArrayList<>(m.values());
        while (bits(out) > 512) {
            out.remove(out.size() - 1);
        }
        return out;
    }

    static int bits(List<SectionSpec> specs) {
        int b = 0;
        for (SectionSpec s : specs) {
            int per = s.kind() == TraitKind.QUANTITATIVE ? 2 : s.kind() == TraitKind.LOAD ? 1
                    : 32 - Integer.numberOfLeadingZeros(s.alleleCount() - 1);
            b += s.loci() * 2 * per;
        }
        return b;
    }

    /** 1–3 random edits: add/remove loci, add/remove a trait, widen an S-locus. */
    static List<SectionSpec> edit(List<SectionSpec> in, SplitMix64 rng) {
        List<SectionSpec> out = new ArrayList<>(in);
        int edits = 1 + rng.nextInt(3);
        for (int e = 0; e < edits; e++) {
            int i = rng.nextInt(out.size());
            SectionSpec s = out.get(i);
            switch (rng.nextInt(5)) {
                case 0 -> out.set(i, new SectionSpec(s.trait(), s.kind(),
                        s.kind() == TraitKind.QUALITATIVE ? 1 : Math.min(16, s.loci() + 1 + rng.nextInt(3)),
                        s.alleleCount()));
                case 1 -> out.set(i, new SectionSpec(s.trait(), s.kind(),
                        s.kind() == TraitKind.QUALITATIVE ? 1 : Math.max(1, s.loci() - 1), s.alleleCount()));
                case 2 -> {
                    if (out.size() > 1) {
                        out.remove(i);
                    }
                }
                case 3 -> out.add(new SectionSpec(TraitId.of("n" + rng.nextInt(50)), TraitKind.QUANTITATIVE,
                        1 + rng.nextInt(6), 3));
                default -> {
                    if (s.kind() == TraitKind.QUALITATIVE) {
                        out.set(i, new SectionSpec(s.trait(), s.kind(), 1, Math.min(16, s.alleleCount() + 1
                                + rng.nextInt(4))));
                    }
                }
            }
        }
        return dedupe(out);
    }

    @Test
    void hundredThousandRandomMigrationsLoseNoSharedAllele() {
        SplitMix64 rng = new SplitMix64(1111);
        int migrations = 0;
        long checked = 0;
        for (int layoutN = 0; layoutN < 2000; layoutN++) {
            List<SectionSpec> before = randomSpecs(rng);
            List<SectionSpec> after = edit(before, rng);
            SpeciesDef sOld = speciesOf("t:s", before);
            SpeciesDef sNew = speciesOf("t:s", after);
            GenomeLayout lo = GenomeLayout.of(before);
            GenomeLayout ln = GenomeLayout.of(after);
            LayoutDictionary d = new LayoutDictionary();
            d.register("t:s", lo);
            d.register("t:s", ln);
            for (int k = 0; k < 50; k++) {
                Genome g = RandomGenomes.uniform(sOld.id(), lo, rng);
                GenomeMigrator.Result r = GenomeMigrator.migrate(GenomeCodec.encode(g), sNew, ln, d);
                migrations++;
                Genome m = r.genome().orElseThrow(() -> new AssertionError(r.warnings().toString()));
                assertEquals(lo.hash() == ln.hash() ? GenomeMigrator.Status.UNCHANGED : GenomeMigrator.Status.MIGRATED,
                        r.status());
                for (Locus s : ln.sections()) {
                    Locus o = lo.section(s.trait());
                    if (o == null || o.kind() != s.kind()) {
                        continue;
                    }
                    for (int l = 0; l < Math.min(o.loci(), s.loci()); l++) {
                        for (int w = 0; w < 2; w++) {
                            int v = g.allele(o, l, w);
                            if (v < s.alleleCount()) {
                                assertEquals(v, m.allele(s, l, w));
                                checked++;
                            }
                        }
                    }
                }
            }
        }
        assertEquals(100_000, migrations);
        assertTrue(checked > 1_000_000, "alleles checked " + checked);
    }

    @Test
    void noiseFollowsTraitIdsAcrossALayoutChange() {
        SpeciesDef sugar = MigrationGoldens.species("wild_carrot_sugar");
        io.github.slugblue.wildroots.genetics.phenotype.CompiledSpecies cur =
                new io.github.slugblue.wildroots.genetics.phenotype.CompiledSpecies(sugar);
        io.github.slugblue.wildroots.genetics.phenotype.CompiledSpecies old =
                new io.github.slugblue.wildroots.genetics.phenotype.CompiledSpecies(CARROT);
        byte[] e = new byte[old.quantIds().size()];
        for (int i = 0; i < e.length; i++) {
            e[i] = (byte) (10 * (i + 1));
        }
        io.github.slugblue.wildroots.genetics.phenotype.NoiseVector stored =
                new io.github.slugblue.wildroots.genetics.phenotype.NoiseVector(e);
        io.github.slugblue.wildroots.genetics.phenotype.NoiseVector m = stored.migrate(Carrot.LAYOUT, cur, 77);
        assertEquals(cur.quantIds().size(), m.size());
        for (TraitId t : old.quantIds()) {
            assertEquals(stored.eps(old.quantIndex(t)), m.eps(cur.quantIndex(t)), t.value());
        }
        int si = cur.quantIndex(TraitId.of("sugar"));
        assertEquals(m.eps(si), stored.migrate(Carrot.LAYOUT, cur, 77).eps(si), "new trait rolled deterministically");
        int differ = 0;
        for (long k = 0; k < 50; k++) {
            differ += stored.migrate(Carrot.LAYOUT, cur, k).eps(si) != m.eps(si) ? 1 : 0;
        }
        assertTrue(differ > 30, "the key matters");
        SpeciesDef noFlavor = MigrationGoldens.species("wild_carrot_noflavor");
        io.github.slugblue.wildroots.genetics.phenotype.NoiseVector dropped = stored.migrate(Carrot.LAYOUT,
                new io.github.slugblue.wildroots.genetics.phenotype.CompiledSpecies(noFlavor), 1);
        assertEquals(old.quantIds().size() - 1, dropped.size());
    }

    @Test
    void decodeResultCarriesTheStoredLayoutForNoiseMigration() {
        GenomeLayout cur = GenomeLayout.of(YIELD8);
        GenomeRecord old = GenomeCodec.encode(RandomGenomes.carrot(new SplitMix64(12)));
        DecodeResult.Valid migrated = (DecodeResult.Valid) GenomeMigrator.migrate(old, YIELD8, cur,
                dict(CARROT, YIELD8)).toDecodeResult();
        assertEquals(Optional.of(Carrot.LAYOUT), migrated.storedLayout());
        DecodeResult.Valid same = (DecodeResult.Valid) GenomeMigrator.migrate(old, CARROT, Carrot.LAYOUT,
                dict(CARROT)).toDecodeResult();
        assertEquals(Optional.of(Carrot.LAYOUT), same.storedLayout());
        DecodeResult.Valid unknown = (DecodeResult.Valid) GenomeMigrator.migrate(old, YIELD8, cur,
                new LayoutDictionary()).toDecodeResult();
        assertTrue(unknown.storedLayout().isEmpty(), "unknown layout: noise is rolled anew by the mod layer");
        // the stored layout is exactly what migrateNoise needs
        io.github.slugblue.wildroots.genetics.phenotype.CompiledSpecies cs =
                new io.github.slugblue.wildroots.genetics.phenotype.CompiledSpecies(YIELD8);
        io.github.slugblue.wildroots.genetics.phenotype.NoiseVector e =
                io.github.slugblue.wildroots.genetics.phenotype.NoiseVector.zero(7);
        assertEquals(cs.quantIds().size(), e.migrate(migrated.storedLayout().orElseThrow(), cs, 1).size());
    }
}
