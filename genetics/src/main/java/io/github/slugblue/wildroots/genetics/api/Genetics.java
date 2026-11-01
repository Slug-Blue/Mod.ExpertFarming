package io.github.slugblue.wildroots.genetics.api;

import io.github.slugblue.wildroots.genetics.breeding.Cloning;
import io.github.slugblue.wildroots.genetics.breeding.Offspring;
import io.github.slugblue.wildroots.genetics.classify.CultivarClassifier;
import io.github.slugblue.wildroots.genetics.codec.DecodeResult;
import io.github.slugblue.wildroots.genetics.codec.GenomeCodec;
import io.github.slugblue.wildroots.genetics.codec.GenomeMigrator;
import io.github.slugblue.wildroots.genetics.codec.GenomeRecord;
import io.github.slugblue.wildroots.genetics.codec.LayoutDictionary;
import io.github.slugblue.wildroots.genetics.model.Genome;
import io.github.slugblue.wildroots.genetics.model.GenomeLayout;
import io.github.slugblue.wildroots.genetics.phenotype.CompiledSpecies;
import io.github.slugblue.wildroots.genetics.phenotype.NoiseVector;
import io.github.slugblue.wildroots.genetics.phenotype.PhenotypeCalculator;
import io.github.slugblue.wildroots.genetics.pollination.Candidate;
import io.github.slugblue.wildroots.genetics.pollination.Donor;
import io.github.slugblue.wildroots.genetics.pollination.FatherSelector;
import io.github.slugblue.wildroots.genetics.population.VillageTemplate;
import io.github.slugblue.wildroots.genetics.population.WildPopulation;
import io.github.slugblue.wildroots.genetics.quality.Booster;
import io.github.slugblue.wildroots.genetics.quality.Grade;
import io.github.slugblue.wildroots.genetics.quality.GradedInput;
import io.github.slugblue.wildroots.genetics.quality.ProcessingQuality;
import io.github.slugblue.wildroots.genetics.quality.ProcessingResult;
import io.github.slugblue.wildroots.genetics.quality.QualityIndex;
import io.github.slugblue.wildroots.genetics.validation.DefinitionValidator;
import io.github.slugblue.wildroots.genetics.validation.Problem;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

/**
 * Default implementation of {@link GeneticsService}. Holds a species lookup
 * (for {@link #offspring(LotRecord, long)} and lot fathers) and the layout
 * dictionary used to migrate stored genomes. Compiled species are cached by
 * id and rebuilt when a different definition object arrives (after
 * {@code /reload}). Thread-safe for the server thread plus readers: the
 * caches are guarded by the instance lock.
 */
public final class Genetics implements GeneticsService {

    private final Function<String, SpeciesDef> species;
    private final LayoutDictionary dictionary;
    private final Map<String, CompiledSpecies> compiled = new HashMap<>();

    /**
     * @param species    species by id (the current definitions snapshot); may return {@code null}
     * @param dictionary layout dictionary (jar history + world data), filled by the mod layer
     */
    public Genetics(Function<String, SpeciesDef> species, LayoutDictionary dictionary) {
        this.species = species;
        this.dictionary = dictionary;
    }

    /** Engine over a fixed set of species; their current layouts are registered in a fresh dictionary. */
    public static Genetics of(List<SpeciesDef> defs) {
        Map<String, SpeciesDef> byId = new HashMap<>();
        LayoutDictionary d = new LayoutDictionary();
        for (SpeciesDef s : defs) {
            byId.put(s.id(), s);
            d.register(s.id(), GenomeLayout.of(s));
        }
        return new Genetics(byId::get, d);
    }

    synchronized CompiledSpecies compiled(SpeciesDef s) {
        CompiledSpecies c = compiled.get(s.id());
        if (c == null || c.def() != s) {
            c = new CompiledSpecies(s);
            compiled.put(s.id(), c);
        }
        return c;
    }

    @Override
    public GenomeLayout layout(SpeciesDef s) {
        return compiled(s).layout();
    }

    @Override
    public Genome wildPlant(SpeciesDef s, PatchPassport p, long plantKey) {
        return WildPopulation.wildPlant(s, layout(s), p, plantKey);
    }

    @Override
    public Genome villagePlant(SpeciesDef s, VillageTemplate t, long key) {
        return t.plant(s, layout(s), key);
    }

    @Override
    public Genome offspring(LotRecord lot, long rngKey) {
        SpeciesDef s = species.apply(lot.species());
        if (s == null) {
            throw new IllegalArgumentException("unknown species " + lot.species() + ": the lot is inert");
        }
        return offspring(s, lot, rngKey);
    }

    @Override
    public Genome offspring(SpeciesDef s, LotRecord lot, long rngKey) {
        return Offspring.of(lot, s.mutation(), rngKey);
    }

    @Override
    public Genome clone(Genome g) {
        return Cloning.clone(g);
    }

    @Override
    public NoiseVector rollNoise(SpeciesDef s, long rngKey) {
        return PhenotypeCalculator.rollNoise(compiled(s), rngKey);
    }

    @Override
    public Phenotype phenotype(Genome g, SpeciesDef s, Environment e, NoiseVector n) {
        return PhenotypeCalculator.compute(compiled(s), g, e, n);
    }

    @Override
    public Classification classify(Genome g, SpeciesDef s, List<CultivarDef> profiles,
                                   Optional<CultivarId> parentStatus) {
        return CultivarClassifier.classify(compiled(s), g, profiles, parentStatus);
    }

    @Override
    public Classification classifyClone(Classification source) {
        return CultivarClassifier.forClone(source);
    }

    @Override
    public List<FatherPick> chooseFathers(SpeciesDef s, Genome mother, List<Candidate> nearby, List<Donor> donors,
                                          long rngKey) {
        return FatherSelector.choose(s, mother, nearby, donors, rngKey).fathers();
    }

    @Override
    public int grade(Phenotype p, CultivarDef cultivar) {
        return Grade.of(QualityIndex.of(p, cultivar.qualityAxes()), p.bitter());
    }

    @Override
    public ProcessingResult process(List<GradedInput> mains, List<Booster> boosters) {
        return ProcessingQuality.process(mains, boosters);
    }

    @Override
    public GenomeRecord encode(Genome g) {
        return GenomeCodec.encode(g);
    }

    @Override
    public DecodeResult decode(GenomeRecord r, SpeciesDef s) {
        if (s == null) {
            return new DecodeResult.Inert("unknown species");
        }
        return GenomeMigrator.migrate(r, s, layout(s), dictionary).toDecodeResult();
    }

    @Override
    public List<Problem> validate(SpeciesDef s) {
        return DefinitionValidator.validate(s);
    }

    @Override
    public List<Problem> validate(CultivarDef c, SpeciesDef s) {
        return DefinitionValidator.validate(c, s);
    }
}
