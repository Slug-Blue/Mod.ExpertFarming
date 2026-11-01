package io.github.slugblue.wildroots.genetics.codec;

import io.github.slugblue.wildroots.genetics.model.Genome;

/**
 * Outcome of decoding a stored genome (docs/05-architecture.md §5.2, SEC-10):
 * either a valid genome or an inert object with the reason. The raw data is
 * never touched; the caller keeps it as it was.
 */
public sealed interface DecodeResult permits DecodeResult.Valid, DecodeResult.Inert {

    /**
     * Decoded genome; {@code migrated} when the layout was re-encoded (warnings
     * for the log). {@code storedLayout} is the layout the record was written
     * with, if known (empty for an unknown, regenerated layout): the mod layer
     * passes it to {@code GeneticsService.migrateNoise} without looking the
     * {@code lh} up itself.
     */
    record Valid(Genome genome, boolean migrated, java.util.List<String> warnings,
                 java.util.Optional<io.github.slugblue.wildroots.genetics.model.GenomeLayout> storedLayout)
            implements DecodeResult {

        public Valid {
            warnings = java.util.List.copyOf(warnings);
            storedLayout = storedLayout == null ? java.util.Optional.empty() : storedLayout;
        }

        public Valid(Genome genome) {
            this(genome, false, java.util.List.of(), java.util.Optional.of(genome.layout()));
        }
    }

    /** The data cannot be used now: plant sleeps, lot cannot be planted (ADR-0002). */
    record Inert(String reason) implements DecodeResult {
    }

    default boolean isValid() {
        return this instanceof Valid;
    }

    /** The genome; throws if inert (tests and engine-internal callers only). */
    default Genome genome() {
        if (this instanceof Valid v) {
            return v.genome();
        }
        throw new IllegalStateException("inert: " + ((Inert) this).reason());
    }
}
