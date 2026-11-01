package io.github.slugblue.wildroots.genetics.breeding;

import io.github.slugblue.wildroots.genetics.model.Genome;

/** Clone (GDD §3.8, §5.5): the genotype is copied whole, without meiosis or mutation. */
public final class Cloning {

    private Cloning() {
    }

    public static Genome clone(Genome g) {
        return new Genome(g.species(), g.layout(), g.bits());
    }
}
