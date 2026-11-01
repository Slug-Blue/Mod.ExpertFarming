package io.github.slugblue.wildroots.genetics.pollination;

import io.github.slugblue.wildroots.genetics.api.QualTrait;
import io.github.slugblue.wildroots.genetics.api.SpeciesDef;
import io.github.slugblue.wildroots.genetics.model.Genome;
import io.github.slugblue.wildroots.genetics.model.Locus;

/**
 * Self-incompatibility, one model for all S-locus species: a shared S-allele
 * means rejection (GDD §3.6, §4.2).
 */
public final class SelfIncompatibility {

    private SelfIncompatibility() {
    }

    /** Whether pollen of {@code father} may fertilise {@code mother}. */
    public static boolean compatible(SpeciesDef s, Genome mother, Genome father) {
        if (s.pollination().selfCompatible()) {
            return true;
        }
        QualTrait sl = s.sLocus();
        if (sl == null) {
            return true;
        }
        Locus loc = mother.layout().section(sl.id());
        if (loc == null || !father.layout().equals(mother.layout())) {
            return false;
        }
        int m0 = mother.allele(loc, 0, 0);
        int m1 = mother.allele(loc, 0, 1);
        int f0 = father.allele(loc, 0, 0);
        int f1 = father.allele(loc, 0, 1);
        return f0 != m0 && f0 != m1 && f1 != m0 && f1 != m1;
    }
}
