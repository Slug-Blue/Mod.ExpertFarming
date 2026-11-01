package io.github.slugblue.wildroots.genetics.testutil;

import io.github.slugblue.wildroots.genetics.api.SpeciesDef;
import java.util.List;

/**
 * Species of the released lines present in the engine's test data. Wave 1
 * has the carrot only; each wave adds its species here (variants used by
 * migration tests are not lines).
 */
public final class Lines {

    public static final List<SpeciesDef> SPECIES = List.of(Carrot.SPECIES);

    private Lines() {
    }
}
