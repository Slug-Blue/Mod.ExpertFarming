package io.github.slugblue.wildroots.genetics.api;

import io.github.slugblue.wildroots.genetics.codec.DecodeResult;
import io.github.slugblue.wildroots.genetics.codec.GenomeRecord;
import io.github.slugblue.wildroots.genetics.model.Genome;
import io.github.slugblue.wildroots.genetics.model.GenomeLayout;
import io.github.slugblue.wildroots.genetics.phenotype.NoiseVector;
import io.github.slugblue.wildroots.genetics.pollination.Brush;
import io.github.slugblue.wildroots.genetics.pollination.Candidate;
import io.github.slugblue.wildroots.genetics.pollination.Donor;
import io.github.slugblue.wildroots.genetics.pollination.FatherSelector;
import io.github.slugblue.wildroots.genetics.population.VillageTemplate;
import io.github.slugblue.wildroots.genetics.quality.Booster;
import io.github.slugblue.wildroots.genetics.quality.GradedInput;
import io.github.slugblue.wildroots.genetics.quality.ProcessingResult;
import io.github.slugblue.wildroots.genetics.validation.Problem;
import java.util.List;
import java.util.Optional;

/**
 * Facade port between the genetics engine and the Minecraft layer
 * (docs/05-architecture.md §4.1, ADR-0001). The mod layer turns JSON into
 * records, computes the cell environment and RNG keys, stores the results;
 * the engine gets plain values only.
 *
 * <p>Totality (SEC-30): the facade validates every species the first time it
 * sees it (and again after {@code /reload} brings a new definition object).
 * An unknown or invalid species, or a genome in another layout, gives an
 * empty {@code Optional} / {@code Inert} — never an exception.
 */
public interface GeneticsService {

    /** Layout of a valid species, cached per species (§5.1). */
    Optional<GenomeLayout> layout(SpeciesDef species);

    /** Lazy wild genome (worldgen §3.3); key {@code Hash64(worldSeed, patchOrigin, plantPos)}. */
    Optional<Genome> wildPlant(SpeciesDef s, PatchPassport p, long plantKey);

    /** Village landrace genome (GDD §9.5); key {@code Hash64(worldSeed, villagePos, landraceId, harvestCount)}. */
    Optional<Genome> villagePlant(SpeciesDef s, VillageTemplate t, long key);

    /** Seed realised at planting (GDD §3.8); the species comes from the definitions snapshot. */
    Optional<Genome> offspring(LotRecord lot, long rngKey);

    /** Same with the species given explicitly. */
    Optional<Genome> offspring(SpeciesDef s, LotRecord lot, long rngKey);

    /** Copy without meiosis (GDD §5.5). */
    Genome clone(Genome g);

    /** ε at planting (GDD §3.1); key {@code Hash64(secret, lotId, remainingCount, packedPos, "noise")}. */
    Optional<NoiseVector> rollNoise(SpeciesDef s, long rngKey);

    /** ε of a plant whose species layout changed (§5.3): carried by trait id, new traits rolled. */
    Optional<NoiseVector> migrateNoise(NoiseVector stored, GenomeLayout oldLayout, SpeciesDef s, long rngKey);

    Optional<Phenotype> phenotype(Genome g, SpeciesDef s, Environment e, NoiseVector n);

    Optional<Classification> classify(Genome g, SpeciesDef s, List<CultivarDef> profiles,
                                      Optional<CultivarId> parentStatus);

    /** Status of a clone: the source's status always (GDD §6.2). */
    Classification classifyClone(Classification source);

    /** Fathers of a lot with the counts for {@code p_out} and seed set (GDD §4.1); key {@code Hash64(secret, plantUid, cycleIndex)}. */
    Optional<FatherSelector.Selection> chooseFathers(SpeciesDef s, Genome mother, List<Candidate> nearby,
                                                     List<Donor> donors, long rngKey);

    /** {@code p_out} of the lot (GDD §4.1–4.4). 0 for an invalid species. */
    double pOut(SpeciesDef s, FatherSelector.Selection selection, boolean beeVisited, Brush brush);

    /** Expected seeds of the mother (GDD §4.1, §4.2). 0 for an invalid species. */
    double expectedSeeds(SpeciesDef s, Phenotype mother, FatherSelector.Selection selection, boolean pollinator,
                         Brush brush);

    /** Grade 1–4 of a harvest by the cultivar's quality axes (GDD §6.4). */
    int grade(Phenotype p, CultivarDef cultivar);

    /** Grade 1–4 of a wild plant's harvest by the species' (line's) quality axes (GDD §6.4). */
    int grade(Phenotype p, SpeciesDef species);

    /** Processed product grade (GDD §6.6). */
    ProcessingResult process(List<GradedInput> mains, List<Booster> boosters);

    GenomeRecord encode(Genome g);

    /** Decodes and, if the layout changed, migrates a stored genome (§5.2–5.3). Never throws. */
    DecodeResult decode(GenomeRecord r, SpeciesDef s);

    /** Definition validation (§4.4); problems with {@code ERROR} drop the definition. */
    List<Problem> validate(SpeciesDef s);

    List<Problem> validate(CultivarDef c, SpeciesDef s);
}
