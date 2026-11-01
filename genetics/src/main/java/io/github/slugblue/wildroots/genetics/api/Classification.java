package io.github.slugblue.wildroots.genetics.api;

import java.util.Optional;

/**
 * Result of cultivar recognition (GDD §6.2).
 *
 * @param cultivar   recognised profile, empty for the wild species
 * @param tier       0 wild, 1 landrace, 2 cultivar
 * @param inherited  status kept only thanks to the hysteresis (offspring) or as a clone
 */
public record Classification(Optional<CultivarId> cultivar, int tier, boolean inherited) {

    public static final Classification WILD = new Classification(Optional.empty(), 0, false);

    public Classification {
        cultivar = cultivar == null ? Optional.empty() : cultivar;
    }
}
