package io.github.slugblue.wildroots.data;

import static io.github.slugblue.wildroots.data.codec.PathCodecs.BOOL;
import static io.github.slugblue.wildroots.data.codec.PathCodecs.ID;
import static io.github.slugblue.wildroots.data.codec.PathCodecs.INT;
import static io.github.slugblue.wildroots.data.codec.PathCodecs.NUM;
import static io.github.slugblue.wildroots.data.codec.PathCodecs.STR;
import static io.github.slugblue.wildroots.data.codec.PathCodecs.list;
import static io.github.slugblue.wildroots.data.codec.PathCodecs.map;
import static io.github.slugblue.wildroots.data.codec.PathCodecs.object;
import static io.github.slugblue.wildroots.data.codec.PathCodecs.oneOf;

import com.mojang.serialization.Codec;
import io.github.slugblue.wildroots.data.codec.PathCodecs;
import io.github.slugblue.wildroots.genetics.api.AlleleFreq;
import io.github.slugblue.wildroots.genetics.api.Bees;
import io.github.slugblue.wildroots.genetics.api.Budget;
import io.github.slugblue.wildroots.genetics.api.Defaults;
import io.github.slugblue.wildroots.genetics.api.Dominance;
import io.github.slugblue.wildroots.genetics.api.LoadDef;
import io.github.slugblue.wildroots.genetics.api.MutationRates;
import io.github.slugblue.wildroots.genetics.api.Pollination;
import io.github.slugblue.wildroots.genetics.api.QualTrait;
import io.github.slugblue.wildroots.genetics.api.QuantTrait;
import io.github.slugblue.wildroots.genetics.api.SpeciesDef;
import io.github.slugblue.wildroots.genetics.api.Timing;
import io.github.slugblue.wildroots.genetics.api.Tradeoff;
import io.github.slugblue.wildroots.genetics.api.WildFrequencies;
import io.github.slugblue.wildroots.genetics.model.TraitId;
import io.github.slugblue.wildroots.genetics.validation.DataLimits;
import io.github.slugblue.wildroots.genetics.validation.Problem;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.TreeMap;
import java.util.regex.Pattern;
import net.minecraft.resources.ResourceLocation;

/**
 * Species JSON {@code data/<ns>/crop_species/<id>.json} (GDD §3.9;
 * docs/05-architecture.md §8.1–8.2; ADR-0005; WR-125).
 *
 * <p>Two steps. The {@link #CODEC} (DFU, {@link PathCodecs}) checks the
 * structure and the JSON types and reports errors with their path; absent
 * optional values stay empty. {@link #toDefinition} then applies the
 * defaults of {@link Defaults} with a {@code WARN} for each one (GDD §14)
 * and checks the fields the engine does not know (archetype, colour, display
 * traits). The meaning of the engine fields is checked afterwards by
 * {@code DefinitionValidator} (§4.4).
 *
 * <p>The defaults and warnings mirror the test mapping {@code DefJson} of
 * {@code :genetics}; {@code SpeciesSchemaParityTest} runs the engine's
 * fixtures through both.
 */
public final class SpeciesSchema {

    private SpeciesSchema() {
    }

    // ------------------------------------------------------------------ raw form

    public record Quant(Optional<Integer> loci, Optional<Double> sigmaEnv, Optional<Boolean> vigorAffected) {
    }

    public record TradeoffRaw(String trait, String against, Double c, Double threshold) {
    }

    public record BudgetRaw(Optional<Double> limit, Optional<Map<String, Double>> weights) {
    }

    public record Wild(Optional<List<Double>> background, Optional<List<Double>> elite,
                       Optional<List<Double>> richElite, Optional<Map<String, Integer>> eliteLoci,
                       Optional<Map<String, Integer>> richExtraLoci) {
    }

    public record Qual(String dominant, Optional<Double> wildFreq, Optional<Boolean> toxic,
                       Optional<Integer> alleles, Optional<Boolean> selfIncompat, Optional<Integer> patchAlleles) {
    }

    public record Load(Optional<Integer> loci, Optional<Double> freq, Optional<Double> step) {
    }

    public record Mutation(Optional<Double> quantitative, Optional<Double> qualitative) {
    }

    public record BeesRaw(Double outcross, Optional<Integer> carryRange) {
    }

    public record Pol(String mode, Optional<Boolean> selfCompatible, Optional<Integer> radius, Double outcross,
                      Optional<BeesRaw> bees, Optional<Double> seedSetNoPollinator) {
    }

    public record TimingRaw(Double toHarvestMin, Double seedStageMin) {
    }

    public record Raw(String id, Optional<List<Double>> temperatureWindow, Map<String, Quant> quantitative,
                      Optional<List<TradeoffRaw>> tradeoffs, Optional<BudgetRaw> budget, Optional<Wild> wild,
                      Optional<Double> wildF, Optional<Map<String, Double>> starterFreq,
                      Map<String, Qual> qualitative, Optional<Load> load, Optional<Mutation> mutation,
                      Pol pollination, TimingRaw timing, Optional<Map<String, Double>> qualityAxes,
                      Optional<Map<String, String>> displayTraits, Optional<String> lifeCycle,
                      Optional<ResourceLocation> block, Optional<String> archetype, Optional<Integer> color,
                      Optional<String> rareAllele, Optional<String> propagation) {
    }

    // ------------------------------------------------------------------ codec

    /** Three allele frequencies 0/1/2. */
    static final Codec<List<Double>> FREQ = PathCodecs.map(list(NUM), l -> l).flatXmap(
            l -> l.size() == 3 ? com.mojang.serialization.DataResult.success(l)
                    : com.mojang.serialization.DataResult.error(() -> PathCodecs.at("", "three frequencies expected")),
            com.mojang.serialization.DataResult::success);

    static final Codec<Quant> QUANT = object(f -> new Quant(f.opt("loci", INT), f.opt("sigma_env", NUM),
            f.opt("vigor_affected", BOOL)));

    static final Codec<TradeoffRaw> TRADEOFF = object(f -> new TradeoffRaw(f.req("trait", STR),
            f.req("against", STR), f.req("c", NUM), f.req("threshold", NUM)));

    static final Codec<BudgetRaw> BUDGET = object(f -> new BudgetRaw(f.opt("limit", NUM),
            f.opt("weights", map(NUM))));

    static final Codec<Wild> WILD = object(f -> new Wild(f.opt("background", FREQ), f.opt("elite", FREQ),
            f.opt("rich_elite", FREQ), f.opt("elite_loci", map(INT)), f.opt("rich_extra_loci", map(INT))));

    static final Codec<Qual> QUAL = object(f -> new Qual(f.req("dominant", oneOf("wild", "domestic")),
            f.opt("wild_freq", NUM), f.opt("toxic", BOOL), f.opt("alleles", INT), f.opt("self_incompat", BOOL),
            f.opt("patch_alleles", INT)));

    static final Codec<Load> LOAD = object(f -> new Load(f.opt("loci", INT), f.opt("freq", NUM), f.opt("step", NUM)));

    static final Codec<Mutation> MUTATION = object(f -> new Mutation(f.opt("quantitative", NUM),
            f.opt("qualitative", NUM)));

    static final Codec<BeesRaw> BEES = object(f -> new BeesRaw(f.req("outcross", NUM), f.opt("carry_range", INT)));

    static final Codec<Pol> POLLINATION = object(f -> new Pol(f.req("mode", STR), f.opt("self_compatible", BOOL),
            f.opt("radius", INT), f.req("outcross", NUM), f.opt("bees", BEES),
            f.opt("seed_set_no_pollinator", NUM)));

    static final Codec<TimingRaw> TIMING = object(f -> new TimingRaw(f.req("to_harvest_min", NUM),
            f.req("seed_stage_min", NUM)));

    /** Structure and types of a species file. */
    public static final Codec<Raw> CODEC = object(f -> new Raw(
            f.req("id", STR),
            f.opt("temperature_window", list(NUM)),
            f.req("quantitative", map(QUANT)),
            f.opt("tradeoffs", list(TRADEOFF)),
            f.opt("budget", BUDGET),
            f.opt("wild_allele_freq", WILD),
            f.opt("wild_F", NUM),
            f.opt("starter_freq", map(NUM)),
            f.req("qualitative", map(QUAL)),
            f.opt("load", LOAD),
            f.opt("mutation", MUTATION),
            f.req("pollination", POLLINATION),
            f.req("timing", TIMING),
            f.opt("quality_axes", map(NUM)),
            f.opt("display_traits", map(STR)),
            f.opt("life_cycle", STR),
            f.opt("block", ID),
            f.opt("archetype", oneOf(SpeciesInfo.ARCHETYPES.toArray(String[]::new))),
            f.opt("color", INT),
            f.opt("rare_allele", STR),
            f.opt("propagation", STR)));

    // ------------------------------------------------------------------ defaults and mod checks

    /** Engine definition and mod data of one file, with the WARN/ERROR problems of this step. */
    public record Mapped(SpeciesDef def, SpeciesInfo info, List<Problem> problems) {
    }

    private static final Pattern DISPLAY_ID = Pattern.compile("[a-z0-9_]{1,64}");

    /** Applies defaults (with WARN) and checks the mod-only fields (ERROR). */
    public static Mapped toDefinition(Raw raw) {
        List<Problem> p = new ArrayList<>();
        List<Double> window = raw.temperatureWindow().orElse(List.of());
        double tMin = window.size() > 0 ? window.get(0) : Double.NaN;
        double tMax = window.size() > 1 ? window.get(1) : Double.NaN;

        List<QuantTrait> quant = new ArrayList<>();
        raw.quantitative().forEach((id, q) -> {
            String path = "quantitative." + id;
            QuantTrait def = Defaults.quantTrait(TraitId.of(id));
            quant.add(new QuantTrait(def.id(),
                    Defaults.or(q.loci().orElse(null), def.loci(), path + ".loci", p),
                    Defaults.or(q.sigmaEnv().orElse(null), def.sigmaEnv(), path + ".sigma_env", p),
                    Defaults.or(q.vigorAffected().orElse(null), def.vigorAffected(), path + ".vigor_affected", p)));
        });

        List<Tradeoff> trade = new ArrayList<>();
        if (raw.tradeoffs().isEmpty()) {
            p.add(Problem.warn("tradeoffs", "missing: no pleiotropy (GDD §3.10)"));
        }
        raw.tradeoffs().orElse(List.of()).forEach(t -> trade.add(new Tradeoff(TraitId.of(t.trait()),
                TraitId.of(t.against()), t.c(), t.threshold())));

        Budget budget;
        if (raw.budget().isEmpty()) {
            p.add(Problem.warn("budget", "missing: no resource budget (GDD §3.10)"));
            budget = Budget.NONE;
        } else {
            BudgetRaw b = raw.budget().get();
            double limit = Defaults.or(b.limit().orElse(null), Defaults.BUDGET_LIMIT, "budget.limit", p);
            Map<TraitId, Double> w = new TreeMap<>();
            if (b.weights().isEmpty()) {
                p.add(Problem.warn("budget.weights", "missing, default weights applied"));
                w.putAll(Defaults.BUDGET_WEIGHTS);
            } else {
                b.weights().get().forEach((k, v) -> w.put(TraitId.of(k), v));
            }
            budget = new Budget(limit, w);
        }

        Wild wild = raw.wild().orElse(new Wild(Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(),
                Optional.empty()));
        AlleleFreq bg = freq(wild.background(), Defaults.BACKGROUND_FREQ, "wild_allele_freq.background", p);
        AlleleFreq el = freq(wild.elite(), Defaults.ELITE_FREQ, "wild_allele_freq.elite", p);
        AlleleFreq rich = freq(wild.richElite(), Defaults.RICH_ELITE_FREQ, "wild_allele_freq.rich_elite", p);
        Map<TraitId, Integer> eliteLoci = traitMap(wild.eliteLoci().orElse(Map.of()));
        Map<TraitId, Integer> richExtra = wild.richExtraLoci().isEmpty() ? Defaults.RICH_EXTRA_LOCI
                : traitMap(wild.richExtraLoci().get());
        double wildF = Defaults.or(raw.wildF().orElse(null), 0.0, "wild_F", p);

        List<QualTrait> qual = new ArrayList<>();
        raw.qualitative().forEach((id, q) -> {
            String path = "qualitative." + id;
            boolean s = q.selfIncompat().orElse(false);
            double wildFreq = q.wildFreq().isPresent() ? q.wildFreq().get()
                    : s ? 0.0 : Defaults.or((Double) null, 0.0, path + ".wild_freq", p);
            qual.add(new QualTrait(TraitId.of(id), "domestic".equals(q.dominant()) ? Dominance.DOMESTIC : Dominance.WILD,
                    wildFreq, q.toxic().orElse(false), q.alleles().orElse(2), s,
                    q.patchAlleles().orElse(s ? Defaults.S_ALLELES_PER_PATCH : 2)));
        });
        Map<TraitId, Double> starter = traitMap(raw.starterFreq().orElse(Map.of()));

        LoadDef load;
        if (raw.load().isEmpty()) {
            p.add(Problem.warn("load", "missing, default " + Defaults.LOAD + " applied"));
            load = Defaults.LOAD;
        } else {
            Load l = raw.load().get();
            load = new LoadDef(Defaults.or(l.loci().orElse(null), Defaults.LOAD.loci(), "load.loci", p),
                    Defaults.or(l.freq().orElse(null), Defaults.LOAD.freq(), "load.freq", p),
                    Defaults.or(l.step().orElse(null), Defaults.LOAD.step(), "load.step", p));
        }
        Mutation mu = raw.mutation().orElse(new Mutation(Optional.empty(), Optional.empty()));
        MutationRates mut = new MutationRates(
                Defaults.or(mu.quantitative().orElse(null), Defaults.MUTATION.quantitative(), "mutation.quantitative", p),
                Defaults.or(mu.qualitative().orElse(null), Defaults.MUTATION.qualitative(), "mutation.qualitative", p));

        Pol po = raw.pollination();
        Optional<Bees> bees = po.bees().map(b -> new Bees(b.outcross(),
                Defaults.or(b.carryRange().orElse(null), 0, "pollination.bees.carry_range", p)));
        Pollination pol = new Pollination(po.mode(),
                Defaults.or(po.selfCompatible().orElse(null), true, "pollination.self_compatible", p),
                Defaults.or(po.radius().orElse(null), 0, "pollination.radius", p),
                po.outcross(), bees,
                Defaults.or(po.seedSetNoPollinator().orElse(null), Defaults.SEED_SET_NO_POLLINATOR,
                        "pollination.seed_set_no_pollinator", p));
        Timing timing = new Timing(raw.timing().toHarvestMin(), raw.timing().seedStageMin());
        Map<TraitId, Double> axes = traitMap(raw.qualityAxes().orElse(Map.of()));

        SpeciesDef def = new SpeciesDef(raw.id(), tMin, tMax, quant, trade, budget,
                new WildFrequencies(bg, el, eliteLoci, rich, richExtra), wildF, qual, starter, load, mut, pol,
                timing, axes);
        return new Mapped(def, info(raw, p), p);
    }

    private static SpeciesInfo info(Raw raw, List<Problem> p) {
        ResourceLocation id = ResourceLocation.tryParse(raw.id());
        if (id == null) {
            id = io.github.slugblue.wildroots.core.Ids.mod("invalid");
            p.add(Problem.error("id", "invalid id " + raw.id()));
        }
        Map<String, String> display = new java.util.LinkedHashMap<>(raw.displayTraits().orElse(Map.of()));
        if (display.size() > DataLimits.MAX_DISPLAY_TRAITS) {
            p.add(Problem.error("display_traits", display.size() + " entries > " + DataLimits.MAX_DISPLAY_TRAITS));
        }
        display.entrySet().removeIf(e -> {
            String trait = e.getKey();
            if (!raw.quantitative().containsKey(trait) && !raw.qualitative().containsKey(trait)) {
                p.add(Problem.warn("display_traits." + trait, "undeclared trait " + trait + ", ignored"));
                return true;
            }
            if (!DISPLAY_ID.matcher(e.getValue()).matches()) {
                p.add(Problem.error("display_traits." + trait, "display name must match " + DISPLAY_ID.pattern()));
            }
            return false;
        });
        OptionalInt color = OptionalInt.empty();
        if (raw.color().isPresent()) {
            int c = raw.color().get();
            if (c < 0 || c > DataLimits.MAX_COLOR) {
                p.add(Problem.error("color", c + " outside 0–" + DataLimits.MAX_COLOR));
            } else {
                color = OptionalInt.of(c);
            }
        }
        raw.rareAllele().ifPresent(a -> {
            if (!raw.qualitative().containsKey(a)) {
                p.add(Problem.error("rare_allele", "undeclared qualitative trait " + a));
            }
        });
        return new SpeciesInfo(id, raw.archetype().orElse("field"), raw.block(), color,
                raw.lifeCycle().orElse("annual"), display, raw.rareAllele(), raw.propagation().orElse("seed"));
    }

    private static AlleleFreq freq(Optional<List<Double>> v, AlleleFreq def, String path, List<Problem> p) {
        if (v.isEmpty()) {
            return Defaults.or(null, def, path, p);
        }
        List<Double> l = v.get();
        return new AlleleFreq(l.get(0), l.get(1), l.get(2));
    }

    private static <V> Map<TraitId, V> traitMap(Map<String, V> in) {
        Map<TraitId, V> out = new TreeMap<>();
        in.forEach((k, v) -> out.put(TraitId.of(k), v));
        return out;
    }
}
