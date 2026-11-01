package io.github.slugblue.wildroots.genetics.population;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.slugblue.wildroots.genetics.api.Classification;
import io.github.slugblue.wildroots.genetics.api.PatchPassport;
import io.github.slugblue.wildroots.genetics.api.Traits;
import io.github.slugblue.wildroots.genetics.classify.CultivarClassifier;
import io.github.slugblue.wildroots.genetics.model.Genome;
import io.github.slugblue.wildroots.genetics.model.Locus;
import io.github.slugblue.wildroots.genetics.model.TraitId;
import io.github.slugblue.wildroots.genetics.phenotype.CompiledSpecies;
import io.github.slugblue.wildroots.genetics.phenotype.GenotypicValue;
import io.github.slugblue.wildroots.genetics.phenotype.PhenotypeCalculator;
import io.github.slugblue.wildroots.genetics.random.Hash64;
import io.github.slugblue.wildroots.genetics.testutil.Carrot;
import io.github.slugblue.wildroots.genetics.testutil.ChiSquare;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** WR-108: wild patches, elite loci and village templates (GDD §3.2, §9.5, §11.2; worldgen §3.3, §5, §6.4). */
class PopulationTest {

    static final long SEED = 20261101L;
    static final String SP = Carrot.SPECIES.id();
    static final PatchPassport MEADOW = new PatchPassport(SP, "meadow", false, 12345L, false, SEED);

    @Test
    void eliteLociAreAFunctionOfSeedSpeciesAndCategory() {
        long k1 = WildPopulation.categoryKey(SEED, SP, "meadow");
        assertEquals(k1, WildPopulation.categoryKey(SEED, SP, "meadow"));
        assertEquals(Hash64.of(SEED, Hash64.str(SP), Hash64.str("meadow")), k1);
        for (TraitId t : List.of(Traits.YIELD, Traits.FRUIT_SIZE)) {
            assertArrayEquals(WildPopulation.eliteLoci(k1, t, 6, 2), WildPopulation.eliteLoci(k1, t, 6, 2));
            int marked = 0;
            for (boolean b : WildPopulation.eliteLoci(k1, t, 6, 2)) {
                marked += b ? 1 : 0;
            }
            assertEquals(2, marked);
        }
        // categories differ: across 12 categories the yield elite pairs are not all the same
        Set<String> patterns = new HashSet<>();
        for (String cat : List.of("meadow", "plains", "forest_edge", "flower_fields", "taiga", "savanna",
                "riverbank", "oasis", "salt_marsh", "jungle", "swamp", "upland_meadow")) {
            long k = WildPopulation.categoryKey(SEED, SP, cat);
            StringBuilder sb = new StringBuilder();
            for (Locus q : Carrot.LAYOUT.quantitative()) {
                sb.append(java.util.Arrays.toString(WildPopulation.eliteLoci(k, q.trait(), q.loci(),
                        Carrot.SPECIES.wild().eliteLoci().getOrDefault(q.trait(), 0))));
            }
            patterns.add(sb.toString());
        }
        assertEquals(12, patterns.size(), "every category has its own elite loci");
        // a different world seed moves them too
        assertTrue(WildPopulation.categoryKey(SEED + 1, SP, "meadow") != k1);
    }

    @Test
    void wildPlantIsDeterministic() {
        assertEquals(WildPopulation.wildPlant(Carrot.SPECIES, Carrot.LAYOUT, MEADOW, 99),
                WildPopulation.wildPlant(Carrot.SPECIES, Carrot.LAYOUT, MEADOW, 99));
    }

    @Test
    void tenThousandWildPlantsMatchBackgroundAndEliteFrequencies() {
        long catKey = WildPopulation.categoryKey(SEED, SP, "meadow");
        long[] bg = new long[3];
        long[] elite = new long[3];
        long[] load = new long[2];
        long hom = 0;
        long het = 0;
        long pairs = 0;
        for (int i = 0; i < 10_000; i++) {
            Genome g = WildPopulation.wildPlant(Carrot.SPECIES, Carrot.LAYOUT, MEADOW, Hash64.of(SEED, 12345L, i));
            for (Locus q : Carrot.LAYOUT.quantitative()) {
                boolean[] el = WildPopulation.eliteLoci(catKey, q.trait(), q.loci(),
                        Carrot.SPECIES.wild().eliteLoci().getOrDefault(q.trait(), 0));
                for (int l = 0; l < q.loci(); l++) {
                    int a = g.allele(q, l, 0); // maternal allele: independent across plants and loci
                    (el[l] ? elite : bg)[a]++;
                    if (!el[l]) {
                        pairs++;
                        if (g.allele(q, l, 0) == g.allele(q, l, 1)) {
                            hom++;
                        } else {
                            het++;
                        }
                    }
                }
            }
            Locus ld = Carrot.LAYOUT.load();
            for (int l = 0; l < ld.loci(); l++) {
                load[g.allele(ld, l, 0)]++;
            }
        }
        assertTrue(ChiSquare.pValue(bg, new double[] {0.60, 0.35, 0.05}) > 0.01, "background " + java.util.Arrays.toString(bg));
        assertTrue(ChiSquare.pValue(elite, new double[] {0.30, 0.40, 0.30}) > 0.01, "elite " + java.util.Arrays.toString(elite));
        assertTrue(ChiSquare.pValue(load, new double[] {0.75, 0.25}) > 0.01, "load " + java.util.Arrays.toString(load));
        // F = 1 − H_obs / H_exp at background loci; H_exp = 1 − Σp²
        double hExp = 1 - (0.36 + 0.1225 + 0.0025);
        double f = 1 - (het / (double) pairs) / hExp;
        assertEquals(Carrot.SPECIES.wildF(), 0.1);
        // TAE: the tolerance was a flat 0.015 (≈ 7σ, F = 0.085 passed); now 4σ of the
        // heterozygosity estimate over the counted pairs (σ_F ≈ 0.002 here)
        double hObsExp = (1 - Carrot.SPECIES.wildF()) * hExp;
        double sigmaF = Math.sqrt(hObsExp * (1 - hObsExp) / pairs) / hExp;
        assertTrue(sigmaF < 0.003, "sigma_F " + sigmaF);
        assertEquals(0.1, f, 4 * sigmaF, "wild_F");
        assertEquals(pairs, hom + het);
    }

    @Test
    void starterPatchUsesStarterFrequencies() {
        PatchPassport starter = new PatchPassport(SP, "meadow", false, 777L, true, SEED);
        Locus y1 = Carrot.LAYOUT.section(Carrot.Y1);
        Locus bit = Carrot.LAYOUT.section(Carrot.BITTER);
        Locus y2 = Carrot.LAYOUT.section(Carrot.Y2);
        long[] cy1 = new long[2];
        long[] cbit = new long[2];
        long[] cy2 = new long[2];
        for (int i = 0; i < 10_000; i++) {
            Genome g = WildPopulation.wildPlant(Carrot.SPECIES, Carrot.LAYOUT, starter, Hash64.of(SEED, 777L, i));
            cy1[g.allele(y1, 0, 0)]++;
            cbit[g.allele(bit, 0, 0)]++;
            cy2[g.allele(y2, 0, 0)]++;
        }
        assertTrue(ChiSquare.pValue(cy1, new double[] {0.65, 0.35}) > 0.01, "y1 " + java.util.Arrays.toString(cy1));
        assertTrue(ChiSquare.pValue(cbit, new double[] {0.55, 0.45}) > 0.01, "bitterness " + java.util.Arrays.toString(cbit));
        assertTrue(ChiSquare.pValue(cy2, new double[] {0.88, 0.12}) > 0.01, "y2 keeps wild_freq");
    }

    @Test
    void richPatchAddsEliteLociAndRichFrequencies() {
        long k = WildPopulation.categoryKey(SEED, SP, "meadow");
        boolean[] base = WildPopulation.eliteLoci(k, Traits.YIELD, 6, 2);
        boolean[] rich = WildPopulation.richLoci(k, Traits.YIELD, 6, 2, 1);
        int nb = 0;
        int nr = 0;
        for (int i = 0; i < 6; i++) {
            nb += base[i] ? 1 : 0;
            nr += rich[i] ? 1 : 0;
            assertTrue(!base[i] || rich[i], "rich keeps the category's elite loci");
        }
        assertEquals(2, nb);
        assertEquals(3, nr);
        PatchPassport richP = new PatchPassport(SP, "meadow", true, 5L, false, SEED);
        double sum = 0;
        Locus fruit = Carrot.LAYOUT.section(Traits.FRUIT_SIZE);
        for (int i = 0; i < 4000; i++) {
            sum += GenotypicValue.of(WildPopulation.wildPlant(Carrot.SPECIES, Carrot.LAYOUT, richP, i), fruit);
        }
        // 3 of 6 loci at 0.15/0.40/0.45 (mean allele 1.30), 3 at background (0.45):
        // E[G] = (3·2.6 + 3·0.9) / 24 = 0.4375; worldgen §5.1 rounds it to «≈ 0.40»
        assertEquals(0.4375, sum / 4000, 0.01);
    }

    @Test
    void villageTemplateGivesGenomeInTheMiddle() {
        VillageTemplate t = VillageTemplate.fromLandrace(Carrot.SPECIES, Carrot.EASTERN,
                Map.of(Carrot.Y2, 0.30), Map.of());
        // eastern carrot: fruit_size ≥ 0.40 → 0.45, flavor ≥ 0.30 → 0.35 (GDD §9.5)
        assertEquals(0.45, t.targets().get(Traits.FRUIT_SIZE), 1e-12);
        assertEquals(0.35, t.targets().get(Traits.FLAVOR), 1e-12);
        CompiledSpecies cs = new CompiledSpecies(Carrot.SPECIES);
        Locus fruit = Carrot.LAYOUT.section(Traits.FRUIT_SIZE);
        Locus flavor = Carrot.LAYOUT.section(Traits.FLAVOR);
        Locus y2 = Carrot.LAYOUT.section(Carrot.Y2);
        long[] cy2 = new long[2];
        Set<String> fruitArrangements = new HashSet<>();
        int exact = 0;
        for (int i = 0; i < 2000; i++) {
            Genome g = t.plant(Carrot.SPECIES, Carrot.LAYOUT, Hash64.of(SEED, 42L, i));
            // golden: G is threshold + 0.05 rounded up to the allele grid (fruit 11/24, flavor 6/16);
            // when flavor ← yield pulls G_eff below the target, flavor is raised until G_eff holds
            double gFruit = GenotypicValue.of(g, fruit);
            double gFlavor = GenotypicValue.of(g, flavor);
            double[] eff = PhenotypeCalculator.gEff(cs, g);
            assertTrue(gFruit >= 11 / 24.0 - 1e-12);
            assertTrue(gFlavor >= 6 / 16.0 - 1e-12);
            assertTrue(eff[cs.quantIndex(Traits.FLAVOR)] >= 0.35 - 1e-9 && eff[cs.quantIndex(Traits.FRUIT_SIZE)] >= 0.45 - 1e-9);
            if (Math.abs(gFlavor - 6 / 16.0) < 1e-12 && Math.abs(gFruit - 11 / 24.0) < 1e-12) {
                exact++;
            }
            Classification c = CultivarClassifier.classify(cs, g, Carrot.PROFILES, Optional.empty());
            assertEquals(Optional.of(Carrot.EASTERN.id()), c.cultivar(), "village plant is the landrace");
            cy2[g.allele(y2, 0, 0)]++;
            StringBuilder sb = new StringBuilder();
            for (int l = 0; l < 6; l++) {
                sb.append(g.allele(fruit, l, 0)).append(g.allele(fruit, l, 1));
            }
            fruitArrangements.add(sb.toString());
        }
        assertTrue(ChiSquare.pValue(cy2, new double[] {0.70, 0.30}) > 0.01, "y2 " + java.util.Arrays.toString(cy2));
        assertTrue(fruitArrangements.size() > 100, "alleles sit on varying loci");
        assertTrue(exact > 1500, "most plants sit exactly on the grid target: " + exact);
        assertEquals(0.3, t.wildF());
        assertEquals(0.15, t.loadFreq());
    }
}
