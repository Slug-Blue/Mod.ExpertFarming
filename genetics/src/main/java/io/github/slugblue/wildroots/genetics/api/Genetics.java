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
import io.github.slugblue.wildroots.genetics.pollination.Brush;
import io.github.slugblue.wildroots.genetics.pollination.Candidate;
import io.github.slugblue.wildroots.genetics.pollination.Donor;
import io.github.slugblue.wildroots.genetics.pollination.FatherSelector;
import io.github.slugblue.wildroots.genetics.pollination.Outcrossing;
import io.github.slugblue.wildroots.genetics.pollination.SeedSet;
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
 * (for {@link #offspring(LotRecord, long)}) and the layout dictionary used to
 * migrate stored genomes. A species is validated when it first fills the
 * cache; invalid ones are remembered as such until a new definition object
 * replaces them. Thread-safe: the cache is guarded by the instance lock.
 */
public final class Genetics implements GeneticsService {

    private final Function<String, SpeciesDef> species;
    private final LayoutDictionary dictionary;
    private final Map<String, Entry> compiled = new HashMap<>();

    /** Cache entry: the definition object it was built from and the compiled form, if valid. */
    private record Entry(SpeciesDef def, Optional<CompiledSpecies> compiled) {
    }

    /**
     * @param species    species by id (the current definitions snapshot); may return {@code null}
     * @param dictionary layout dictionary (jar history + world data), filled by the mod layer
     */
    public Genetics(Function<String, SpeciesDef> species, LayoutDictionary dictionary) {
        this.species = species;
        this.dictionary = dictionary;
    }

    /** Engine over a fixed set of species; layouts of the valid ones go into a fresh dictionary. */
    public static Genetics of(List<SpeciesDef> defs) {
        Map<String, SpeciesDef> byId = new HashMap<>();
        LayoutDictionary d = new LayoutDictionary();
        for (SpeciesDef s : defs) {
            byId.put(s.id(), s);
            if (!DefinitionValidator.hasErrors(DefinitionValidator.validate(s))) {
                d.register(s.id(), GenomeLayout.of(s));
            }
        }
        return new Genetics(byId::get, d);
    }

    /** Compiled form of a valid species, empty for null or invalid definitions. */
    synchronized Optional<CompiledSpecies> compiled(SpeciesDef s) {
        if (s == null || s.id() == null) {
            return Optional.empty();
        }
        Entry e = compiled.get(s.id());
        if (e == null || e.def() != s) {
            Optional<CompiledSpecies> c = Optional.empty();
            try {
                if (!DefinitionValidator.hasErrors(DefinitionValidator.validate(s))) {
                    c = Optional.of(new CompiledSpecies(s));
                }
            } catch (RuntimeException ex) {
                c = Optional.empty();
            }
            e = new Entry(s, c);
            compiled.put(s.id(), e);
        }
        return e.compiled();
    }

    /** Compiled species if {@code g} belongs to it in its current layout. */
    private Optional<CompiledSpecies> forGenome(SpeciesDef s, Genome g) {
        return compiled(s).filter(c -> g != null && g.species().equals(s.id()) && g.layout().equals(c.layout()));
    }

    @Override
    public Optional<GenomeLayout> layout(SpeciesDef s) {
        return compiled(s).map(CompiledSpecies::layout);
    }

    @Override
    public Optional<Genome> wildPlant(SpeciesDef s, PatchPassport p, long plantKey) {
        return compiled(s).map(c -> WildPopulation.wildPlant(s, c.layout(), p, plantKey));
    }

    @Override
    public Optional<Genome> villagePlant(SpeciesDef s, VillageTemplate t, long key) {
        return compiled(s).map(c -> t.plant(c, key));
    }

    @Override
    public Optional<Genome> offspring(LotRecord lot, long rngKey) {
        return offspring(species.apply(lot.species()), lot, rngKey);
    }

    @Override
    public Optional<Genome> offspring(SpeciesDef s, LotRecord lot, long rngKey) {
        return forGenome(s, lot.mother()).map(c -> Offspring.of(lot, s.mutation(), rngKey));
    }

    @Override
    public Genome clone(Genome g) {
        return Cloning.clone(g);
    }

    @Override
    public Optional<NoiseVector> rollNoise(SpeciesDef s, long rngKey) {
        return compiled(s).map(c -> PhenotypeCalculator.rollNoise(c, rngKey));
    }

    @Override
    public Optional<NoiseVector> migrateNoise(NoiseVector stored, GenomeLayout oldLayout, SpeciesDef s, long rngKey) {
        return compiled(s).map(c -> stored.migrate(oldLayout, c, rngKey));
    }

    @Override
    public Optional<Phenotype> phenotype(Genome g, SpeciesDef s, Environment e, NoiseVector n) {
        return forGenome(s, g).map(c -> PhenotypeCalculator.compute(c, g, e, n));
    }

    @Override
    public Optional<Classification> classify(Genome g, SpeciesDef s, List<CultivarDef> profiles,
                                             Optional<CultivarId> parentStatus) {
        return forGenome(s, g).map(c -> CultivarClassifier.classify(c, g, profiles, parentStatus));
    }

    @Override
    public Classification classifyClone(Classification source) {
        return CultivarClassifier.forClone(source);
    }

    @Override
    public Optional<FatherSelector.Selection> chooseFathers(SpeciesDef s, Genome mother, List<Candidate> nearby,
                                                            List<Donor> donors, long rngKey) {
        return forGenome(s, mother).map(c -> FatherSelector.choose(s, mother, nearby, donors, rngKey));
    }

    @Override
    public double pOut(SpeciesDef s, FatherSelector.Selection sel, boolean beeVisited, Brush brush) {
        return compiled(s).map(c -> Outcrossing.pOut(s, sel.compatibleNeighbours(), sel.compatibleDonors(),
                beeVisited, brush)).orElse(0.0);
    }

    @Override
    public double expectedSeeds(SpeciesDef s, Phenotype mother, FatherSelector.Selection sel, boolean pollinator,
                                Brush brush) {
        return compiled(s).map(c -> {
            boolean shatters = c.def().qual(Traits.SHATTERING) != null && !mother.domestic(Traits.SHATTERING);
            return SeedSet.expected(s, mother.pOr(Traits.YIELD, 0), pollinator, sel.compatiblePollen(), brush,
                    shatters);
        }).orElse(0.0);
    }

    @Override
    public int grade(Phenotype p, CultivarDef cultivar) {
        return Grade.of(QualityIndex.of(p, cultivar.qualityAxes()), p.bitter());
    }

    @Override
    public int grade(Phenotype p, SpeciesDef s) {
        return Grade.of(QualityIndex.of(p, s.qualityAxes()), p.bitter());
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
        Optional<CompiledSpecies> c = compiled(s);
        if (c.isEmpty()) {
            return new DecodeResult.Inert(s == null ? "unknown species" : "invalid species " + s.id());
        }
        return GenomeMigrator.migrate(r, s, c.get().layout(), dictionary).toDecodeResult();
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
