package io.github.slugblue.wildroots.genetics.testutil;

import io.github.slugblue.wildroots.genetics.api.AlleleFreq;
import io.github.slugblue.wildroots.genetics.api.Bees;
import io.github.slugblue.wildroots.genetics.api.Budget;
import io.github.slugblue.wildroots.genetics.api.CultivarDef;
import io.github.slugblue.wildroots.genetics.api.CultivarId;
import io.github.slugblue.wildroots.genetics.api.Defaults;
import io.github.slugblue.wildroots.genetics.api.Dominance;
import io.github.slugblue.wildroots.genetics.api.LoadDef;
import io.github.slugblue.wildroots.genetics.api.MutationRates;
import io.github.slugblue.wildroots.genetics.api.Pollination;
import io.github.slugblue.wildroots.genetics.api.QualReq;
import io.github.slugblue.wildroots.genetics.api.QualTrait;
import io.github.slugblue.wildroots.genetics.api.QuantTrait;
import io.github.slugblue.wildroots.genetics.api.Range;
import io.github.slugblue.wildroots.genetics.api.SpeciesDef;
import io.github.slugblue.wildroots.genetics.api.Timing;
import io.github.slugblue.wildroots.genetics.api.Tradeoff;
import io.github.slugblue.wildroots.genetics.api.WildFrequencies;
import io.github.slugblue.wildroots.genetics.model.TraitId;
import io.github.slugblue.wildroots.genetics.validation.Problem;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

/**
 * Test-only mapping of species and cultivar JSON (GDD §3.9, §6.3) to engine
 * records, standing in for the Codec layer of {@code :mod} (WR-125). Missing
 * values come from {@link Defaults#or} with a {@code WARN}; wrong JSON types
 * give an {@code ERROR} and a {@code NaN}/empty value that the validator also
 * rejects. Semantics are checked by {@code DefinitionValidator}, not here.
 */
public final class DefJson {

    private final List<Problem> problems;

    private DefJson(List<Problem> problems) {
        this.problems = problems;
    }

    public static SpeciesDef species(String resource) {
        List<Problem> p = new ArrayList<>();
        SpeciesDef s = species(MiniJson.resource(resource), p);
        if (p.stream().anyMatch(Problem::isError)) {
            throw new IllegalStateException(resource + ": " + p);
        }
        return s;
    }

    public static CultivarDef cultivar(String resource) {
        List<Problem> p = new ArrayList<>();
        CultivarDef c = cultivar(MiniJson.resource(resource), p);
        if (p.stream().anyMatch(Problem::isError)) {
            throw new IllegalStateException(resource + ": " + p);
        }
        return c;
    }

    @SuppressWarnings("unchecked")
    public static SpeciesDef species(Object json, List<Problem> problems) {
        DefJson d = new DefJson(problems);
        Map<String, Object> m = d.obj(json, "");
        String id = d.str(m.get("id"), "id", "");
        List<Object> window = d.list(m.get("temperature_window"), "temperature_window");
        double tMin = window.size() > 0 ? d.num(window.get(0), "temperature_window[0]") : Double.NaN;
        double tMax = window.size() > 1 ? d.num(window.get(1), "temperature_window[1]") : Double.NaN;

        List<QuantTrait> quant = new ArrayList<>();
        for (Map.Entry<String, Object> e : d.obj(m.get("quantitative"), "quantitative").entrySet()) {
            String path = "quantitative." + e.getKey();
            Map<String, Object> q = d.obj(e.getValue(), path);
            QuantTrait def = Defaults.quantTrait(TraitId.of(e.getKey()));
            quant.add(new QuantTrait(def.id(),
                    d.intOr(q.get("loci"), def.loci(), path + ".loci", true),
                    d.numOr(q.get("sigma_env"), def.sigmaEnv(), path + ".sigma_env"),
                    d.boolOr(q.get("vigor_affected"), def.vigorAffected(), path + ".vigor_affected")));
        }

        List<Tradeoff> trade = new ArrayList<>();
        if (!m.containsKey("tradeoffs")) {
            problems.add(Problem.warn("tradeoffs", "missing: no pleiotropy (GDD §3.10)"));
        }
        List<Object> tl = d.list(m.get("tradeoffs"), "tradeoffs");
        for (int i = 0; i < tl.size(); i++) {
            String path = "tradeoffs[" + i + "]";
            Map<String, Object> t = d.obj(tl.get(i), path);
            trade.add(new Tradeoff(TraitId.of(d.str(t.get("trait"), path + ".trait", "")),
                    TraitId.of(d.str(t.get("against"), path + ".against", "")),
                    d.num(t.get("c"), path + ".c"), d.num(t.get("threshold"), path + ".threshold")));
        }

        Budget budget;
        if (m.get("budget") == null) {
            problems.add(Problem.warn("budget", "missing: no resource budget (GDD §3.10)"));
            budget = Budget.NONE;
        } else {
            Map<String, Object> b = d.obj(m.get("budget"), "budget");
            double limit = d.numOr(b.get("limit"), Defaults.BUDGET_LIMIT, "budget.limit");
            Map<TraitId, Double> w = new TreeMap<>();
            if (b.get("weights") == null) {
                problems.add(Problem.warn("budget.weights", "missing, default weights applied"));
                w.putAll(Defaults.BUDGET_WEIGHTS);
            } else {
                for (Map.Entry<String, Object> e : d.obj(b.get("weights"), "budget.weights").entrySet()) {
                    w.put(TraitId.of(e.getKey()), d.num(e.getValue(), "budget.weights." + e.getKey()));
                }
            }
            budget = new Budget(limit, w);
        }

        Map<String, Object> wf = m.get("wild_allele_freq") == null ? Map.of()
                : d.obj(m.get("wild_allele_freq"), "wild_allele_freq");
        AlleleFreq bg = d.freq(wf.get("background"), Defaults.BACKGROUND_FREQ, "wild_allele_freq.background");
        AlleleFreq el = d.freq(wf.get("elite"), Defaults.ELITE_FREQ, "wild_allele_freq.elite");
        AlleleFreq rich = d.freq(wf.get("rich_elite"), Defaults.RICH_ELITE_FREQ, "wild_allele_freq.rich_elite");
        Map<TraitId, Integer> eliteLoci = d.intMap(wf.get("elite_loci"), "wild_allele_freq.elite_loci");
        Map<TraitId, Integer> richExtra = wf.get("rich_extra_loci") == null
                ? Defaults.RICH_EXTRA_LOCI : d.intMap(wf.get("rich_extra_loci"), "wild_allele_freq.rich_extra_loci");
        double wildF = d.numOr(m.get("wild_F"), 0.0, "wild_F");

        List<QualTrait> qual = new ArrayList<>();
        for (Map.Entry<String, Object> e : d.obj(m.get("qualitative"), "qualitative").entrySet()) {
            String path = "qualitative." + e.getKey();
            Map<String, Object> q = d.obj(e.getValue(), path);
            String dom = d.str(q.get("dominant"), path + ".dominant", "wild");
            boolean s = d.boolOr(q.get("self_incompat"), false, path + ".self_incompat", false);
            int alleles = d.intOr(q.get("alleles"), 2, path + ".alleles", false);
            qual.add(new QualTrait(TraitId.of(e.getKey()),
                    "domestic".equals(dom) ? Dominance.DOMESTIC : Dominance.WILD,
                    d.numOr(q.get("wild_freq"), 0.0, path + ".wild_freq", !s),
                    d.boolOr(q.get("toxic"), false, path + ".toxic", false), alleles, s,
                    d.intOr(q.get("patch_alleles"), s ? Defaults.S_ALLELES_PER_PATCH : 2,
                            path + ".patch_alleles", false)));
        }
        Map<TraitId, Double> starter = d.numMap(m.get("starter_freq"), "starter_freq");

        LoadDef load;
        if (m.get("load") == null) {
            problems.add(Problem.warn("load", "missing, default " + Defaults.LOAD + " applied"));
            load = Defaults.LOAD;
        } else {
            Map<String, Object> l = d.obj(m.get("load"), "load");
            load = new LoadDef(d.intOr(l.get("loci"), Defaults.LOAD.loci(), "load.loci", true),
                    d.numOr(l.get("freq"), Defaults.LOAD.freq(), "load.freq"),
                    d.numOr(l.get("step"), Defaults.LOAD.step(), "load.step"));
        }
        Map<String, Object> mu = m.get("mutation") == null ? Map.of() : d.obj(m.get("mutation"), "mutation");
        MutationRates mut = new MutationRates(
                d.numOr(mu.get("quantitative"), Defaults.MUTATION.quantitative(), "mutation.quantitative"),
                d.numOr(mu.get("qualitative"), Defaults.MUTATION.qualitative(), "mutation.qualitative"));

        Map<String, Object> po = d.obj(m.get("pollination"), "pollination");
        Optional<Bees> bees = Optional.empty();
        if (po.get("bees") != null) {
            Map<String, Object> b = d.obj(po.get("bees"), "pollination.bees");
            bees = Optional.of(new Bees(d.num(b.get("outcross"), "pollination.bees.outcross"),
                    d.intOr(b.get("carry_range"), 0, "pollination.bees.carry_range", true)));
        }
        Pollination pol = new Pollination(d.str(po.get("mode"), "pollination.mode", "insect"),
                d.boolOr(po.get("self_compatible"), true, "pollination.self_compatible"),
                d.intOr(po.get("radius"), 0, "pollination.radius", true),
                d.num(po.get("outcross"), "pollination.outcross"), bees,
                d.numOr(po.get("seed_set_no_pollinator"), Defaults.SEED_SET_NO_POLLINATOR,
                        "pollination.seed_set_no_pollinator"));
        Map<String, Object> ti = d.obj(m.get("timing"), "timing");
        Timing timing = new Timing(d.num(ti.get("to_harvest_min"), "timing.to_harvest_min"),
                d.num(ti.get("seed_stage_min"), "timing.seed_stage_min"));
        Map<TraitId, Double> axes = d.numMap(m.get("quality_axes"), "quality_axes");

        return new SpeciesDef(id, tMin, tMax, quant, trade, budget,
                new WildFrequencies(bg, el, eliteLoci, rich, richExtra), wildF, qual, starter, load, mut, pol,
                timing, axes);
    }

    @SuppressWarnings("unchecked")
    public static CultivarDef cultivar(Object json, List<Problem> problems) {
        DefJson d = new DefJson(problems);
        Map<String, Object> m = d.obj(json, "");
        Map<String, Object> req = m.get("requires") == null ? Map.of() : d.obj(m.get("requires"), "requires");
        Map<TraitId, Range> quant = new TreeMap<>();
        Map<String, Object> rq = req.get("quantitative") == null ? Map.of()
                : d.obj(req.get("quantitative"), "requires.quantitative");
        for (Map.Entry<String, Object> e : rq.entrySet()) {
            String path = "requires.quantitative." + e.getKey();
            Map<String, Object> r = d.obj(e.getValue(), path);
            quant.put(TraitId.of(e.getKey()), new Range(
                    r.get("min") == null ? 0.0 : d.num(r.get("min"), path + ".min"),
                    r.get("max") == null ? 1.0 : d.num(r.get("max"), path + ".max")));
        }
        Map<TraitId, QualReq> qual = d.reqMap(req.get("qualitative"), "requires.qualitative");
        List<Map<TraitId, QualReq>> anyOf = new ArrayList<>();
        List<Object> ao = d.list(req.get("any_of"), "requires.any_of");
        for (int i = 0; i < ao.size(); i++) {
            anyOf.add(d.reqMap(ao.get(i), "requires.any_of[" + i + "]"));
        }
        Map<String, Object> produce = m.get("produce") == null ? Map.of() : d.obj(m.get("produce"), "produce");
        Map<TraitId, Double> axes = d.numMap(produce.get("quality_axes"), "produce.quality_axes");
        Optional<CultivarId> parent = m.get("parent") == null ? Optional.empty()
                : Optional.of(CultivarId.of(d.str(m.get("parent"), "parent", "")));
        return new CultivarDef(CultivarId.of(d.str(m.get("id"), "id", "")),
                d.str(m.get("species"), "species", ""),
                d.intOr(m.get("tier"), 0, "tier", true),
                d.intOr(m.get("priority"), 0, "priority", false),
                new TreeMap<>(quant), new TreeMap<>(qual),
                anyOf.stream().map(TreeMap::new).map(t -> (java.util.SortedMap<TraitId, QualReq>) t).toList(),
                d.numOr(m.get("hysteresis"), Defaults.HYSTERESIS, "hysteresis"),
                new TreeMap<>(axes), parent);
    }

    // ---------------------------------------------------------------- helpers

    @SuppressWarnings("unchecked")
    private Map<String, Object> obj(Object v, String path) {
        if (v instanceof Map<?, ?> m) {
            return (Map<String, Object>) m;
        }
        if (v != null) {
            problems.add(Problem.error(path, "object expected"));
        } else {
            problems.add(Problem.error(path, "missing"));
        }
        return new LinkedHashMap<>();
    }

    @SuppressWarnings("unchecked")
    private List<Object> list(Object v, String path) {
        if (v instanceof List<?> l) {
            return (List<Object>) l;
        }
        if (v != null) {
            problems.add(Problem.error(path, "array expected"));
        }
        return List.of();
    }

    private double num(Object v, String path) {
        if (v instanceof Double d) {
            return d;
        }
        problems.add(Problem.error(path, v == null ? "missing" : "number expected"));
        return Double.NaN;
    }

    private double numOr(Object v, double def, String path) {
        return numOr(v, def, path, true);
    }

    private double numOr(Object v, double def, String path, boolean warn) {
        if (v == null) {
            if (warn) {
                return Defaults.or((Double) null, def, path, problems);
            }
            return def;
        }
        return num(v, path);
    }

    /** An int field: missing → default (WARN if {@code warn}); non-finite or fractional → ERROR. */
    private int intOr(Object v, int def, String path, boolean warn) {
        if (v == null) {
            if (warn) {
                problems.add(Problem.warn(path, "missing, default " + def + " applied"));
            }
            return def;
        }
        double x = num(v, path);
        return Double.isNaN(x) && !(v instanceof Double) ? def
                : io.github.slugblue.wildroots.genetics.validation.DataLimits.toInt(x, def, path, problems);
    }

    private boolean boolOr(Object v, boolean def, String path) {
        return boolOr(v, def, path, true);
    }

    private boolean boolOr(Object v, boolean def, String path, boolean warn) {
        if (v == null) {
            return warn ? Defaults.or((Boolean) null, def, path, problems) : def;
        }
        if (v instanceof Boolean b) {
            return b;
        }
        problems.add(Problem.error(path, "boolean expected"));
        return def;
    }

    private String str(Object v, String path, String def) {
        if (v instanceof String s) {
            return s;
        }
        problems.add(Problem.error(path, v == null ? "missing" : "string expected"));
        return def;
    }

    private AlleleFreq freq(Object v, AlleleFreq def, String path) {
        if (v == null) {
            return Defaults.or(null, def, path, problems);
        }
        List<Object> l = list(v, path);
        if (l.size() != 3) {
            problems.add(Problem.error(path, "three frequencies expected"));
            return new AlleleFreq(Double.NaN, Double.NaN, Double.NaN);
        }
        return new AlleleFreq(num(l.get(0), path + "[0]"), num(l.get(1), path + "[1]"), num(l.get(2), path + "[2]"));
    }

    private Map<TraitId, Integer> intMap(Object v, String path) {
        Map<TraitId, Integer> out = new TreeMap<>();
        if (v == null) {
            return out;
        }
        for (Map.Entry<String, Object> e : obj(v, path).entrySet()) {
            out.put(TraitId.of(e.getKey()), intOr(e.getValue(), Integer.MIN_VALUE, path + "." + e.getKey(), true));
        }
        return out;
    }

    private Map<TraitId, Double> numMap(Object v, String path) {
        Map<TraitId, Double> out = new TreeMap<>();
        if (v == null) {
            return out;
        }
        for (Map.Entry<String, Object> e : obj(v, path).entrySet()) {
            out.put(TraitId.of(e.getKey()), num(e.getValue(), path + "." + e.getKey()));
        }
        return out;
    }

    private Map<TraitId, QualReq> reqMap(Object v, String path) {
        Map<TraitId, QualReq> out = new TreeMap<>();
        if (v == null) {
            return out;
        }
        for (Map.Entry<String, Object> e : obj(v, path).entrySet()) {
            String s = str(e.getValue(), path + "." + e.getKey(), "wild");
            if (!s.equals("wild") && !s.equals("domestic")) {
                problems.add(Problem.error(path + "." + e.getKey(), "'wild' or 'domestic' expected"));
            }
            out.put(TraitId.of(e.getKey()), s.equals("domestic") ? QualReq.DOMESTIC : QualReq.WILD);
        }
        return out;
    }
}
