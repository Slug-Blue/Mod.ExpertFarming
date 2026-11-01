package io.github.slugblue.wildroots.genetics.api;

import io.github.slugblue.wildroots.genetics.codec.DecodeResult;
import io.github.slugblue.wildroots.genetics.codec.GenomeRecord;
import io.github.slugblue.wildroots.genetics.model.Genome;
import io.github.slugblue.wildroots.genetics.model.GenomeLayout;
import io.github.slugblue.wildroots.genetics.phenotype.NoiseVector;
import io.github.slugblue.wildroots.genetics.pollination.Candidate;
import io.github.slugblue.wildroots.genetics.pollination.Donor;
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
 * the engine gets plain values only. Every definition passed in must have
 * passed {@link #validate(SpeciesDef)} (SEC-30).
 *
 * <p>Differences from the §4.1 sketch (WR-113): {@code decode} returns
 * {@link DecodeResult} ({@code Valid | Inert}, §5.2); {@code offspring} has an
 * overload with the species (mutation rates); the clone status and the
 * validation are part of the port.
 */
public interface GeneticsService {

    /** Layout of a species, cached per species (§5.1). */
    GenomeLayout layout(SpeciesDef species);

    /** Lazy wild genome (worldgen §3.3); key {@code Hash64(worldSeed, patchOrigin, plantPos)}. */
    Genome wildPlant(SpeciesDef s, PatchPassport p, long plantKey);

    /** Village landrace genome (GDD §9.5); key {@code Hash64(worldSeed, villagePos, landraceId, harvestCount)}. */
    Genome villagePlant(SpeciesDef s, VillageTemplate t, long key);

    /** Seed realised at planting: mother meiosis × chosen father meiosis + mutations (GDD §3.8). */
    Genome offspring(LotRecord lot, long rngKey);

    /** Same with the species given explicitly. */
    Genome offspring(SpeciesDef s, LotRecord lot, long rngKey);

    /** Copy without meiosis (GDD §5.5). */
    Genome clone(Genome g);

    /** ε at planting (GDD §3.1); key {@code Hash64(secret, lotId, remainingCount, packedPos, "noise")}. */
    NoiseVector rollNoise(SpeciesDef s, long rngKey);

    Phenotype phenotype(Genome g, SpeciesDef s, Environment e, NoiseVector n);

    Classification classify(Genome g, SpeciesDef s, List<CultivarDef> profiles, Optional<CultivarId> parentStatus);

    /** Status of a clone: the source's status always (GDD §6.2). */
    Classification classifyClone(Classification source);

    /** Fathers of a lot (GDD §4.1); key {@code Hash64(secret, plantUid, cycleIndex)}. */
    List<FatherPick> chooseFathers(SpeciesDef s, Genome mother, List<Candidate> nearby, List<Donor> donors,
                                   long rngKey);

    /** Grade 1–4 of a harvest (GDD §6.4). */
    int grade(Phenotype p, CultivarDef cultivar);

    /** Processed product grade (GDD §6.6). */
    ProcessingResult process(List<GradedInput> mains, List<Booster> boosters);

    GenomeRecord encode(Genome g);

    /** Decodes and, if the layout changed, migrates a stored genome (§5.2–5.3). Never throws. */
    DecodeResult decode(GenomeRecord r, SpeciesDef s);

    /** Definition validation (§4.4); problems with {@code ERROR} drop the definition. */
    List<Problem> validate(SpeciesDef s);

    List<Problem> validate(CultivarDef c, SpeciesDef s);
}
