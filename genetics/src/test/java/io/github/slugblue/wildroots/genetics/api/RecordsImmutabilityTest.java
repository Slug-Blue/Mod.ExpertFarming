package io.github.slugblue.wildroots.genetics.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.slugblue.wildroots.genetics.model.Genome;
import io.github.slugblue.wildroots.genetics.model.TraitId;
import io.github.slugblue.wildroots.genetics.testutil.Carrot;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * WR-102: {@code SpeciesDef}, {@code CultivarDef}, {@code Environment},
 * {@code PatchPassport}, {@code LotRecord}, {@code Phenotype},
 * {@code Classification} are immutable: final classes with final fields,
 * collections copied into unmodifiable views, arrays copied in and out.
 */
class RecordsImmutabilityTest {

    private static final List<Class<?>> TYPES = List.of(SpeciesDef.class, CultivarDef.class, Environment.class,
            PatchPassport.class, LotRecord.class, Phenotype.class, Classification.class, Genome.class);

    @Test
    void allFieldsAreFinalAndClassesFinal() {
        for (Class<?> c : TYPES) {
            assertTrue(Modifier.isFinal(c.getModifiers()), c + " is not final");
            for (Field f : c.getDeclaredFields()) {
                if (!Modifier.isStatic(f.getModifiers())) {
                    assertTrue(Modifier.isFinal(f.getModifiers()), c.getSimpleName() + "." + f.getName());
                }
            }
        }
    }

    @Test
    void speciesCollectionsAreCopiedAndUnmodifiable() {
        List<QuantTrait> quant = new ArrayList<>(Carrot.SPECIES.quantitative());
        Map<TraitId, Double> axes = new HashMap<>(Carrot.SPECIES.qualityAxes());
        SpeciesDef s = new SpeciesDef("x", 0, 1, quant, List.of(), Budget.NONE, Carrot.SPECIES.wild(), 0,
                List.of(), Map.of(), Defaults.LOAD, Defaults.MUTATION, Carrot.SPECIES.pollination(),
                Carrot.SPECIES.timing(), axes);
        quant.clear();
        axes.clear();
        assertEquals(7, s.quantitative().size());
        assertEquals(2, s.qualityAxes().size());
        assertThrows(UnsupportedOperationException.class, () -> s.quantitative().clear());
        assertThrows(UnsupportedOperationException.class, () -> s.qualityAxes().clear());
        assertThrows(UnsupportedOperationException.class, () -> s.budget().weights().clear());
        assertThrows(UnsupportedOperationException.class, () -> Carrot.CARROT.quantitative().clear());
        assertThrows(UnsupportedOperationException.class, () -> Carrot.EASTERN.anyOf().get(0).clear());
    }

    @Test
    void phenotypeAndGenomeCopyArrays() {
        double[] p = {0.5};
        boolean[] d = {true};
        Phenotype ph = new Phenotype(List.of(TraitId.of("a")), p, p, p, 1, List.of(TraitId.of("b")), d, false);
        p[0] = 9;
        d[0] = false;
        assertEquals(0.5, ph.p(TraitId.of("a")));
        assertTrue(ph.domestic(TraitId.of("b")));
        ph.pArray()[0] = 7;
        assertEquals(0.5, ph.p(TraitId.of("a")));

        long[] bits = new long[Carrot.LAYOUT.longCount()];
        Genome g = new Genome(Carrot.SPECIES.id(), Carrot.LAYOUT, bits);
        bits[0] = -1;
        assertEquals(0, g.bits()[0]);
        g.bits()[0] = -1;
        assertEquals(0, g.bits()[0]);
    }

    @Test
    void lotFathersAreCopied() {
        Genome g = new Genome(Carrot.SPECIES.id(), Carrot.LAYOUT, new long[3]);
        List<FatherPick> fathers = new ArrayList<>(List.of(new FatherPick(g, 1, false)));
        LotRecord lot = new LotRecord(g, fathers, 0.5);
        fathers.clear();
        assertEquals(1, lot.fathers().size());
        assertThrows(UnsupportedOperationException.class, () -> lot.fathers().clear());
    }

    @Test
    void genomeConstructorRejectsOutOfRangeBits() {
        long[] bad = new long[Carrot.LAYOUT.longCount()];
        bad[0] = 0b11; // quantitative allele 3
        assertThrows(IllegalArgumentException.class, () -> new Genome(Carrot.SPECIES.id(), Carrot.LAYOUT, bad));
        long[] tail = new long[Carrot.LAYOUT.longCount()];
        tail[2] = 1L << 63;
        assertThrows(IllegalArgumentException.class, () -> new Genome(Carrot.SPECIES.id(), Carrot.LAYOUT, tail));
        assertThrows(IllegalArgumentException.class,
                () -> new Genome(Carrot.SPECIES.id(), Carrot.LAYOUT, new long[2]));
    }
}
