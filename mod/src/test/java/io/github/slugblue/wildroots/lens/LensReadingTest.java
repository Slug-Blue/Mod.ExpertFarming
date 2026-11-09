package io.github.slugblue.wildroots.lens;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.slugblue.wildroots.genetics.api.CultivarDef;
import io.github.slugblue.wildroots.genetics.api.CultivarId;
import io.github.slugblue.wildroots.genetics.api.Phenotype;
import io.github.slugblue.wildroots.genetics.api.QualReq;
import io.github.slugblue.wildroots.genetics.api.Range;
import io.github.slugblue.wildroots.genetics.model.TraitId;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** WR-183: "looks like a cultivar" compares P and visible states with the profiles (GDD §6.2 item 5). */
class LensReadingTest {

    static final TraitId SIZE = TraitId.of("fruit_size");
    static final TraitId FLAVOR = TraitId.of("flavor");
    static final TraitId BITTER = TraitId.of("bitterness");
    static final TraitId Y1 = TraitId.of("color_y1");
    static final TraitId PURPLE = TraitId.of("color_p");

    static final CultivarDef EASTERN = new CultivarDef(CultivarId.of("wildroots:eastern_carrot"), "wildroots:wild_carrot", 1,
            0, Map.of(SIZE, Range.atLeast(0.40), FLAVOR, Range.atLeast(0.30)), Map.of(BITTER, QualReq.DOMESTIC),
            List.of(Map.of(Y1, QualReq.DOMESTIC), Map.of(PURPLE, QualReq.DOMESTIC)), 0.05, Map.of());
    static final CultivarDef CARROT = new CultivarDef(CultivarId.of("wildroots:carrot"), "wildroots:wild_carrot", 2, 0,
            Map.of(SIZE, Range.atLeast(0.75), FLAVOR, Range.atLeast(0.60)), Map.of(BITTER, QualReq.DOMESTIC), List.of(),
            0.05, Map.of());

    static Phenotype pheno(double size, double flavor, boolean bitterFree, boolean yellow) {
        return new Phenotype(List.of(SIZE, FLAVOR), new double[] {size, flavor}, new double[] {size, flavor},
                new double[] {size, flavor}, 1.0, List.of(BITTER, Y1, PURPLE), new boolean[] {bitterFree, yellow, false},
                !bitterFree);
    }

    @Test
    void looksLikeTheHighestProfileThePhenotypePasses() {
        List<CultivarDef> all = List.of(EASTERN, CARROT);
        assertEquals(Optional.of(EASTERN), LensReading.looksLike(all, pheno(0.45, 0.35, true, true), Optional.empty(), 0));
        assertEquals(Optional.of(CARROT), LensReading.looksLike(all, pheno(0.80, 0.65, true, true), Optional.empty(), 0));
        assertTrue(LensReading.looksLike(all, pheno(0.45, 0.35, false, true), Optional.empty(), 0).isEmpty(), "bitter");
        assertTrue(LensReading.looksLike(all, pheno(0.45, 0.35, true, false), Optional.empty(), 0).isEmpty(), "no colour");
        assertTrue(LensReading.looksLike(all, pheno(0.39, 0.35, true, true), Optional.empty(), 0).isEmpty(), "too small");
        // the plant already is the eastern carrot by its genotype: nothing to suggest
        assertTrue(LensReading.looksLike(all, pheno(0.45, 0.35, true, true),
                Optional.of("wildroots:eastern_carrot"), 1).isEmpty());
    }
}
