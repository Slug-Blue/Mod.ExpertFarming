package io.github.slugblue.wildroots.plant;

import io.github.slugblue.wildroots.genetics.codec.GenomeRecord;

/**
 * Stored data of one plant, as {@link PlantNbt} reads and writes it
 * (docs/05-architecture.md §6.1). Mutable, owned by one
 * {@link PlantBlockEntity}; the genome stays a {@link GenomeRecord} until the
 * first server access decodes it (ADR-0003, lazy decoding).
 */
public final class PlantState {

    /** Plant flags ({@code pf}). */
    public static final int MOTHER = 1;
    public static final int MUTANT = 2;
    public static final int RARE_CLAIMED = 4;

    String species;
    /** Stored genome, {@code null} for a wild or village plant not touched yet. */
    GenomeRecord genome;
    /** ε, one byte per quantitative trait; {@code null} together with {@link #genome}. */
    byte[] noise;
    Passport passport = Passport.PLAYER;
    long planterMost;
    long planterLeast;
    /** Recognised cultivar or landrace id, {@code null} for the wild species. */
    String cultivar;
    byte tier;
    boolean inherited;
    String lineName;
    int generation;
    /** Flags of the ancestor lot ({@code LotFlags}). */
    byte lotFlags;
    /** {@link #MOTHER}, {@link #MUTANT}, {@link #RARE_CLAIMED}. */
    byte plantFlags;
    PendingLot pending;

    public String species() {
        return species;
    }

    public GenomeRecord genome() {
        return genome;
    }

    public byte[] noise() {
        return noise == null ? null : noise.clone();
    }

    public Passport passport() {
        return passport;
    }

    public String cultivar() {
        return cultivar;
    }

    public int tier() {
        return tier;
    }

    public boolean inherited() {
        return inherited;
    }

    public String lineName() {
        return lineName;
    }

    public int generation() {
        return generation;
    }

    public int lotFlags() {
        return lotFlags;
    }

    public boolean has(int plantFlag) {
        return (plantFlags & plantFlag) != 0;
    }

    public PendingLot pending() {
        return pending;
    }

    public boolean hasPlanter() {
        return planterMost != 0 || planterLeast != 0;
    }

    public java.util.UUID planter() {
        return hasPlanter() ? new java.util.UUID(planterMost, planterLeast) : null;
    }
}
