package io.github.slugblue.wildroots.genetics.pollination;

import io.github.slugblue.wildroots.genetics.model.Genome;

/**
 * A flowering plant of the same species near the mother (docs/05-architecture.md
 * §4.1): its genome and offset. The engine does not know where it came from
 * (bloom index, village field).
 */
public record Candidate(Genome genome, int dx, int dy, int dz) {

    /** Squared horizontal distance; {@code d²} of the weight {@code 1/(1+d²)} (GDD §4.1). */
    public long horizontalDistanceSq() {
        return (long) dx * dx + (long) dz * dz;
    }
}
