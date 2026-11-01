package io.github.slugblue.wildroots.genetics.validation;

import static io.github.slugblue.wildroots.genetics.validation.DataLimits.range;

import io.github.slugblue.wildroots.genetics.api.AlleleFreq;
import io.github.slugblue.wildroots.genetics.api.Bees;
import io.github.slugblue.wildroots.genetics.api.CultivarDef;
import io.github.slugblue.wildroots.genetics.api.QualReq;
import io.github.slugblue.wildroots.genetics.api.QualTrait;
import io.github.slugblue.wildroots.genetics.api.QuantTrait;
import io.github.slugblue.wildroots.genetics.api.Range;
import io.github.slugblue.wildroots.genetics.api.SpeciesDef;
import io.github.slugblue.wildroots.genetics.api.Tradeoff;
import io.github.slugblue.wildroots.genetics.model.GenomeLayout;
import io.github.slugblue.wildroots.genetics.model.TraitId;
import io.github.slugblue.wildroots.genetics.population.VillageTemplate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.regex.Pattern;

/**
 * Validation of definitions before the engine sees them
 * (docs/05-architecture.md §4.4, SEC-28…SEC-30). Returns problems with the
 * JSON path; never throws. {@code ERROR} drops the record, {@code WARN} keeps
 * it (default or normalisation applied).
 */
public final class DefinitionValidator {

    private static final Pattern ID = Pattern.compile("[a-z0-9_.-]+:[a-z0-9_./-]+");
    private static final Pattern TRAIT = Pattern.compile("[a-z0-9_]{1,64}");

    private DefinitionValidator() {
    }

    public static boolean hasErrors(List<Problem> problems) {
        for (Problem p : problems) {
            if (p.isError()) {
                return true;
            }
        }
        return false;
    }

    // ------------------------------------------------------------------ species

    public static List<Problem> validate(SpeciesDef s) {
        List<Problem> out = new ArrayList<>();
        if (s == null) {
            out.add(Problem.error("", "no definition"));
            return out;
        }
        id(s.id(), "id", out);
        if (range(s.tempMin(), -DataLimits.MAX_ABS_TEMPERATURE, DataLimits.MAX_ABS_TEMPERATURE,
                "temperature_window[0]", out)
                & range(s.tempMax(), -DataLimits.MAX_ABS_TEMPERATURE, DataLimits.MAX_ABS_TEMPERATURE,
                "temperature_window[1]", out)
                && s.tempMin() > s.tempMax()) {
            out.add(Problem.error("temperature_window", "min > max"));
        }

        Map<TraitId, QuantTrait> quant = new LinkedHashMap<>();
        if (s.quantitative().size() > DataLimits.MAX_QUANT_TRAITS) {
            out.add(Problem.error("quantitative", s.quantitative().size() + " traits > " + DataLimits.MAX_QUANT_TRAITS));
        }
        if (s.quantitative().isEmpty()) {
            out.add(Problem.error("quantitative", "no quantitative traits"));
        }
        for (QuantTrait q : s.quantitative()) {
            String p = "quantitative." + q.id();
            traitName(q.id(), p, out);
            if (quant.put(q.id(), q) != null) {
                out.add(Problem.error(p, "duplicate trait"));
            }
            intRange(q.loci(), DataLimits.MIN_LOCI, DataLimits.MAX_LOCI, p + ".loci", out);
            range(q.sigmaEnv(), 0, DataLimits.MAX_SIGMA_ENV, p + ".sigma_env", out);
        }

        Map<TraitId, QualTrait> qual = new LinkedHashMap<>();
        if (s.qualitative().size() > DataLimits.MAX_QUAL_TRAITS) {
            out.add(Problem.error("qualitative", s.qualitative().size() + " traits > " + DataLimits.MAX_QUAL_TRAITS));
        }
        int sLoci = 0;
        for (QualTrait q : s.qualitative()) {
            String p = "qualitative." + q.id();
            traitName(q.id(), p, out);
            if (quant.containsKey(q.id()) || qual.put(q.id(), q) != null) {
                out.add(Problem.error(p, "duplicate trait"));
            }
            range(q.wildFreq(), 0, 1, p + ".wild_freq", out);
            if (q.selfIncompat()) {
                sLoci++;
                intRange(q.alleles(), DataLimits.MIN_S_ALLELES, DataLimits.MAX_S_ALLELES, p + ".alleles", out);
                intRange(q.patchAlleles(), 1, Math.max(1, q.alleles()), p + ".patch_alleles", out);
            } else if (q.alleles() != 2) {
                out.add(Problem.error(p + ".alleles", "only the S-locus may have " + q.alleles() + " alleles"));
            }
        }
        if (sLoci > 1) {
            out.add(Problem.error("qualitative", "more than one S-locus"));
        }

        intRange(s.load().loci(), 0, DataLimits.MAX_LOAD_LOCI, "load.loci", out);
        range(s.load().freq(), 0, 1, "load.freq", out);
        if (range(s.load().step(), 0, 1, "load.step", out) && s.load().step() <= 0) {
            out.add(Problem.error("load.step", "must be > 0"));
        }

        boolean sizesOk = !hasErrors(out);
        long bits = GenomeLayout.bitsOf(s);
        if (bits > DataLimits.MAX_LAYOUT_BITS) {
            out.add(Problem.error("layout", bits + " bits > " + DataLimits.MAX_LAYOUT_BITS));
        } else if (sizesOk) {
            {
                try {
                    String canon = GenomeLayout.of(s).canonical();
                    if (canon.length() > DataLimits.MAX_CANONICAL_LENGTH) {
                        out.add(Problem.error("layout", "canonical string of " + canon.length() + " chars > "
                                + DataLimits.MAX_CANONICAL_LENGTH));
                    }
                } catch (RuntimeException e) {
                    out.add(Problem.error("layout", "cannot build: " + e.getMessage()));
                }
            }
        }

        if (s.tradeoffs().size() > DataLimits.MAX_TRADEOFFS) {
            out.add(Problem.error("tradeoffs", s.tradeoffs().size() + " pairs > " + DataLimits.MAX_TRADEOFFS));
        }
        for (int i = 0; i < s.tradeoffs().size(); i++) {
            Tradeoff t = s.tradeoffs().get(i);
            String p = "tradeoffs[" + i + "]";
            declared(t.trait(), quant, p + ".trait", out);
            declared(t.against(), quant, p + ".against", out);
            range(t.c(), 0, DataLimits.MAX_TRADEOFF_C, p + ".c", out);
            range(t.threshold(), 0, 1, p + ".threshold", out);
        }

        if (!s.budget().isNone()) {
            if (range(s.budget().limit(), 0, DataLimits.MAX_BUDGET_LIMIT, "budget.limit", out)
                    && s.budget().limit() <= 0) {
                out.add(Problem.error("budget.limit", "must be > 0"));
            }
            for (Map.Entry<TraitId, Double> e : s.budget().weights().entrySet()) {
                String p = "budget.weights." + e.getKey();
                declared(e.getKey(), quant, p, out);
                range(e.getValue(), 0, DataLimits.MAX_BUDGET_WEIGHT, p, out);
            }
        }

        freq(s.wild().background(), "wild_allele_freq.background", out);
        freq(s.wild().elite(), "wild_allele_freq.elite", out);
        freq(s.wild().richElite(), "wild_allele_freq.rich_elite", out);
        for (Map.Entry<TraitId, Integer> e : s.wild().eliteLoci().entrySet()) {
            String p = "wild_allele_freq.elite_loci." + e.getKey();
            QuantTrait q = quant.get(e.getKey());
            if (q == null) {
                out.add(Problem.error(p, "undeclared trait " + e.getKey()));
            } else if (e.getValue() < 0 || e.getValue() > q.loci()) {
                out.add(Problem.error(p, e.getValue() + " elite loci, trait has " + q.loci()));
            }
        }
        for (Map.Entry<TraitId, Integer> e : s.wild().richExtraLoci().entrySet()) {
            String p = "wild_allele_freq.rich_extra_loci." + e.getKey();
            QuantTrait q = quant.get(e.getKey());
            if (q == null) {
                // rich defaults name yield and fruit_size; a species without them just has no extra loci
                if (!e.getKey().value().equals("yield") && !e.getKey().value().equals("fruit_size")) {
                    out.add(Problem.error(p, "undeclared trait " + e.getKey()));
                }
            } else if (e.getValue() < 0
                    || e.getValue() + s.wild().eliteLoci().getOrDefault(e.getKey(), 0) > q.loci()) {
                out.add(Problem.error(p, "elite + extra loci exceed " + q.loci()));
            }
        }
        range(s.wildF(), 0, 1, "wild_F", out);
        for (Map.Entry<TraitId, Double> e : s.starterFreq().entrySet()) {
            String p = "starter_freq." + e.getKey();
            QualTrait q = qual.get(e.getKey());
            if (q == null || q.alleles() != 2) {
                out.add(Problem.error(p, "not a biallelic qualitative trait"));
            }
            range(e.getValue(), 0, 1, p, out);
        }

        range(s.mutation().quantitative(), 0, DataLimits.MAX_MUTATION, "mutation.quantitative", out);
        range(s.mutation().qualitative(), 0, DataLimits.MAX_MUTATION, "mutation.qualitative", out);

        intRange(s.pollination().radius(), 0, DataLimits.MAX_RADIUS, "pollination.radius", out);
        range(s.pollination().outcross(), 0, 1, "pollination.outcross", out);
        range(s.pollination().seedSetNoPollinator(), 0, 1, "pollination.seed_set_no_pollinator", out);
        if (s.pollination().bees().isPresent()) {
            Bees b = s.pollination().bees().get();
            range(b.outcross(), 0, 1, "pollination.bees.outcross", out);
            intRange(b.carryRange(), 0, DataLimits.MAX_CARRY_RANGE, "pollination.bees.carry_range", out);
        }
        if (!s.pollination().selfCompatible() && sLoci == 0) {
            out.add(Problem.error("pollination.self_compatible", "self-incompatible species without an S-locus"));
        }
        range(s.timing().toHarvestMin(), DataLimits.MIN_STAGE_MIN, DataLimits.MAX_STAGE_MIN,
                "timing.to_harvest_min", out);
        if (s.timing().seedStageMin() != 0) {
            range(s.timing().seedStageMin(), DataLimits.MIN_STAGE_MIN, DataLimits.MAX_STAGE_MIN,
                    "timing.seed_stage_min", out);
        }
        axes(s.qualityAxes(), quant.keySet(), "quality_axes", out);
        return out;
    }

    // ----------------------------------------------------------------- cultivar

    public static List<Problem> validate(CultivarDef c, SpeciesDef species) {
        List<Problem> out = new ArrayList<>();
        if (c == null) {
            out.add(Problem.error("", "no definition"));
            return out;
        }
        id(c.id().value(), "id", out);
        if (species == null || !species.id().equals(c.species())) {
            out.add(Problem.error("species", "unknown species " + c.species()));
            return out;
        }
        intRange(c.tier(), 1, 2, "tier", out);
        intRange(c.priority(), DataLimits.MIN_PRIORITY, DataLimits.MAX_PRIORITY, "priority", out);
        range(c.hysteresis(), 0, DataLimits.MAX_HYSTERESIS, "hysteresis", out);
        int reqs = c.quantitative().size() + c.qualitative().size();
        for (SortedMap<TraitId, QualReq> g : c.anyOf()) {
            reqs += g.size();
        }
        if (reqs > DataLimits.MAX_REQUIREMENTS) {
            out.add(Problem.error("requires", reqs + " requirements > " + DataLimits.MAX_REQUIREMENTS));
        }
        Set<TraitId> quantIds = new HashSet<>();
        species.quantitative().forEach(q -> quantIds.add(q.id()));
        for (Map.Entry<TraitId, Range> e : c.quantitative().entrySet()) {
            String p = "requires.quantitative." + e.getKey();
            if (!quantIds.contains(e.getKey())) {
                out.add(Problem.error(p, "undeclared trait " + e.getKey()));
            }
            boolean ok = range(e.getValue().min(), 0, 1, p + ".min", out)
                    & range(e.getValue().max(), 0, 1, p + ".max", out);
            if (ok && e.getValue().min() > e.getValue().max()) {
                out.add(Problem.error(p, "min > max"));
            }
        }
        qualReqs(c.qualitative(), species, "requires.qualitative", out);
        for (int i = 0; i < c.anyOf().size(); i++) {
            if (c.anyOf().get(i).isEmpty()) {
                out.add(Problem.error("requires.any_of[" + i + "]", "empty group"));
            }
            qualReqs(c.anyOf().get(i), species, "requires.any_of[" + i + "]", out);
        }
        axes(c.qualityAxes(), quantIds, "produce.quality_axes", out);
        return out;
    }

    // ---------------------------------------------------------- whole definition set

    /** Result of validating one load: problems (path = record key + JSON path) and the records that survive. */
    public record SetResult(List<Problem> problems, List<SpeciesDef> species, List<CultivarDef> cultivars,
                            List<VillageTemplate> templates) {

        public SetResult {
            problems = List.copyOf(problems);
            species = List.copyOf(species);
            cultivars = List.copyOf(cultivars);
            templates = List.copyOf(templates);
        }
    }

    /**
     * Validates a whole load (SEC-28 counts, SEC-29 references): at most 512
     * species, 4096 cultivars (64 per species) and 64 village templates —
     * extras by id order are ERROR; duplicate ids are ERROR; every record is
     * validated; references cultivar → species, cultivar → parent and
     * template → landrace go through {@link ReferenceGraph}, and a rejected
     * record takes its dependents with it. Paths are prefixed with
     * {@code species:<id>}, {@code cultivar:<id>}, {@code template:<landrace>}.
     */
    public static SetResult validateSet(List<SpeciesDef> species, List<CultivarDef> cultivars,
                                        List<VillageTemplate> templates) {
        List<Problem> out = new ArrayList<>();
        Map<String, SpeciesDef> sp = new TreeMap<>();
        for (SpeciesDef s : species) {
            if (s == null) {
                continue;
            }
            String key = "species:" + s.id();
            if (sp.containsKey(key)) {
                out.add(Problem.error(key, "duplicate species id"));
                continue;
            }
            sp.put(key, s);
        }
        limitCount(sp, DataLimits.MAX_SPECIES, "species", out);
        Map<String, CultivarDef> cv = new TreeMap<>();
        for (CultivarDef c : cultivars) {
            if (c == null) {
                continue;
            }
            String key = "cultivar:" + c.id().value();
            if (cv.containsKey(key)) {
                out.add(Problem.error(key, "duplicate cultivar id"));
                continue;
            }
            cv.put(key, c);
        }
        limitCount(cv, DataLimits.MAX_CULTIVARS, "cultivars", out);
        Map<String, Integer> perSpecies = new TreeMap<>();
        for (Map.Entry<String, CultivarDef> e : new ArrayList<>(cv.entrySet())) {
            int n = perSpecies.merge(e.getValue().species(), 1, Integer::sum);
            if (n > DataLimits.MAX_CULTIVARS_PER_SPECIES) {
                out.add(Problem.error(e.getKey(), "more than " + DataLimits.MAX_CULTIVARS_PER_SPECIES
                        + " cultivars of " + e.getValue().species()));
                cv.remove(e.getKey());
            }
        }
        Map<String, VillageTemplate> tp = new TreeMap<>();
        for (VillageTemplate t : templates) {
            if (t != null && tp.put("template:" + t.landrace().value(), t) != null) {
                out.add(Problem.error("template:" + t.landrace().value(), "duplicate template"));
            }
        }
        limitCount(tp, DataLimits.MAX_VILLAGE_TEMPLATES, "village templates", out);

        Map<String, List<String>> graph = new TreeMap<>();
        for (Map.Entry<String, SpeciesDef> e : sp.entrySet()) {
            List<Problem> p = validate(e.getValue());
            prefix(e.getKey(), p, out);
            graph.put(e.getKey(), hasErrors(p) ? List.of(e.getKey() + "#invalid") : List.of());
        }
        for (Map.Entry<String, CultivarDef> e : cv.entrySet()) {
            CultivarDef c = e.getValue();
            List<String> refs = new ArrayList<>();
            refs.add("species:" + c.species());
            c.parent().ifPresent(p -> refs.add("cultivar:" + p.value()));
            SpeciesDef s = sp.get("species:" + c.species());
            if (s != null) {
                List<Problem> p = validate(c, s);
                prefix(e.getKey(), p, out);
                if (hasErrors(p)) {
                    refs.add(e.getKey() + "#invalid");
                }
            }
            graph.put(e.getKey(), refs);
        }
        for (Map.Entry<String, VillageTemplate> e : tp.entrySet()) {
            graph.put(e.getKey(), List.of("cultivar:" + e.getValue().landrace().value()));
        }
        List<Problem> refProblems = ReferenceGraph.check(graph);
        java.util.Set<String> rejected = ReferenceGraph.rejected(refProblems);
        for (Problem p : refProblems) {
            if (!p.message().startsWith("dangling reference to ") || !p.message().endsWith("#invalid")) {
                out.add(p);
            }
        }
        List<SpeciesDef> okS = new ArrayList<>();
        sp.forEach((k, v) -> {
            if (!rejected.contains(k)) {
                okS.add(v);
            }
        });
        List<CultivarDef> okC = new ArrayList<>();
        cv.forEach((k, v) -> {
            if (!rejected.contains(k)) {
                okC.add(v);
            }
        });
        List<VillageTemplate> okT = new ArrayList<>();
        tp.forEach((k, v) -> {
            if (!rejected.contains(k)) {
                okT.add(v);
            }
        });
        return new SetResult(out, okS, okC, okT);
    }

    private static <T> void limitCount(Map<String, T> byKey, int max, String what, List<Problem> out) {
        if (byKey.size() <= max) {
            return;
        }
        List<String> keys = new ArrayList<>(byKey.keySet());
        for (String k : keys.subList(max, keys.size())) {
            out.add(Problem.error(k, "more than " + max + " " + what + " in one load"));
            byKey.remove(k);
        }
    }

    private static void prefix(String key, List<Problem> in, List<Problem> out) {
        for (Problem p : in) {
            out.add(new Problem(p.severity(), p.path().isEmpty() ? key : key + "/" + p.path(), p.message()));
        }
    }

    // ------------------------------------------------------------------ helpers

    static void id(String id, String path, List<Problem> out) {
        if (id == null || id.length() > DataLimits.MAX_ID_LENGTH || !ID.matcher(id).matches()) {
            out.add(Problem.error(path, "invalid id " + id));
        }
    }

    static void traitName(TraitId t, String path, List<Problem> out) {
        if (!TRAIT.matcher(t.value()).matches() || t.value().equals("load")) {
            out.add(Problem.error(path, "invalid trait id " + t));
        }
    }

    static void intRange(int v, int lo, int hi, String path, List<Problem> out) {
        if (v < lo || v > hi) {
            out.add(Problem.error(path, v + " outside " + lo + "–" + hi));
        }
    }

    static void declared(TraitId t, Map<TraitId, QuantTrait> quant, String path, List<Problem> out) {
        if (!quant.containsKey(t)) {
            out.add(Problem.error(path, "undeclared trait " + t));
        }
    }

    static void freq(AlleleFreq f, String path, List<Problem> out) {
        boolean ok = range(f.p0(), 0, 1, path + "[0]", out) & range(f.p1(), 0, 1, path + "[1]", out)
                & range(f.p2(), 0, 1, path + "[2]", out);
        if (ok && Math.abs(f.p0() + f.p1() + f.p2() - 1) > DataLimits.FREQ_SUM_TOLERANCE) {
            out.add(Problem.error(path, "frequencies sum to " + (f.p0() + f.p1() + f.p2()) + ", not 1"));
        }
    }

    static void axes(Map<TraitId, Double> axes, Set<TraitId> declared, String path, List<Problem> out) {
        if (axes.size() > DataLimits.MAX_QUALITY_AXES) {
            out.add(Problem.error(path, axes.size() + " axes > " + DataLimits.MAX_QUALITY_AXES));
        }
        double sum = 0;
        boolean ok = true;
        for (Map.Entry<TraitId, Double> e : axes.entrySet()) {
            String p = path + "." + e.getKey();
            if (!declared.contains(e.getKey())) {
                out.add(Problem.error(p, "undeclared trait " + e.getKey()));
            }
            ok &= range(e.getValue(), 0, DataLimits.MAX_BUDGET_WEIGHT, p, out);
            sum += e.getValue();
        }
        if (ok && !axes.isEmpty()) {
            if (!(sum > 0)) {
                out.add(Problem.error(path, "weights sum to 0"));
            } else if (Math.abs(sum - 1) > DataLimits.FREQ_SUM_TOLERANCE) {
                out.add(Problem.warn(path, "weights sum to " + sum + ", normalised"));
            }
        }
    }

    static void qualReqs(Map<TraitId, QualReq> req, SpeciesDef s, String path, List<Problem> out) {
        for (TraitId t : req.keySet()) {
            QualTrait q = s.qual(t);
            if (q == null || q.alleles() != 2) {
                out.add(Problem.error(path + "." + t, "not a biallelic qualitative trait of " + s.id()));
            }
        }
    }
}
