package io.github.slugblue.wildroots.data;

import static io.github.slugblue.wildroots.data.codec.PathCodecs.ID;
import static io.github.slugblue.wildroots.data.codec.PathCodecs.INT;
import static io.github.slugblue.wildroots.data.codec.PathCodecs.NUM;
import static io.github.slugblue.wildroots.data.codec.PathCodecs.STR;
import static io.github.slugblue.wildroots.data.codec.PathCodecs.list;
import static io.github.slugblue.wildroots.data.codec.PathCodecs.map;
import static io.github.slugblue.wildroots.data.codec.PathCodecs.object;
import static io.github.slugblue.wildroots.data.codec.PathCodecs.oneOf;

import com.mojang.serialization.Codec;
import io.github.slugblue.wildroots.core.Ids;
import io.github.slugblue.wildroots.genetics.api.CultivarDef;
import io.github.slugblue.wildroots.genetics.api.CultivarId;
import io.github.slugblue.wildroots.genetics.api.Defaults;
import io.github.slugblue.wildroots.genetics.api.QualReq;
import io.github.slugblue.wildroots.genetics.api.Range;
import io.github.slugblue.wildroots.genetics.model.TraitId;
import io.github.slugblue.wildroots.genetics.validation.DataLimits;
import io.github.slugblue.wildroots.genetics.validation.Problem;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.regex.Pattern;
import net.minecraft.resources.ResourceLocation;

/**
 * Cultivar and landrace JSON {@code data/<ns>/cultivars/<id>.json} (GDD §6.3;
 * economy §2.2; docs/05-architecture.md §8.1–8.2; WR-125). Same two steps as
 * {@link SpeciesSchema}: the codec checks structure and types with paths,
 * {@link #toDefinition} applies defaults with a {@code WARN} and checks the
 * item data the engine does not see ({@code food_reference},
 * {@code food_mult}, keys).
 */
public final class CultivarSchema {

    private CultivarSchema() {
    }

    public record RangeRaw(Optional<Double> min, Optional<Double> max) {
    }

    public record Requires(Optional<Map<String, RangeRaw>> quantitative, Optional<Map<String, String>> qualitative,
                           Optional<List<Map<String, String>>> anyOf) {
    }

    public record Food(Integer nutrition, Double saturation) {
    }

    public record Produce(Optional<ResourceLocation> item, Optional<Double> foodMult, Optional<Food> foodReference,
                          Optional<Map<String, Double>> qualityAxes, Optional<ResourceLocation> motherItem) {
    }

    public record Raw(String id, String species, Optional<Integer> tier, Optional<Integer> priority,
                      Optional<Requires> requires, Optional<Double> hysteresis, Optional<Produce> produce,
                      Optional<String> parent, Optional<String> historicalNote) {
    }

    static final Codec<String> REQ = oneOf("wild", "domestic");

    static final Codec<RangeRaw> RANGE = object(f -> new RangeRaw(f.opt("min", NUM), f.opt("max", NUM)));

    static final Codec<Requires> REQUIRES = object(f -> new Requires(f.opt("quantitative", map(RANGE)),
            f.opt("qualitative", map(REQ)), f.opt("any_of", list(map(REQ)))));

    static final Codec<Food> FOOD = object(f -> new Food(f.req("nutrition", INT), f.req("saturation", NUM)));

    static final Codec<Produce> PRODUCE = object(f -> new Produce(f.opt("item", ID), f.opt("food_mult", NUM),
            f.opt("food_reference", FOOD), f.opt("quality_axes", map(NUM)), f.opt("mother_item", ID)));

    /** Structure and types of a cultivar file. */
    public static final Codec<Raw> CODEC = object(f -> new Raw(f.req("id", STR), f.req("species", STR),
            f.opt("tier", INT), f.opt("priority", INT), f.opt("requires", REQUIRES), f.opt("hysteresis", NUM),
            f.opt("produce", PRODUCE), f.opt("parent", STR), f.opt("historical_note", STR)));

    /** Engine definition and mod data of one file, with the WARN/ERROR problems of this step. */
    public record Mapped(CultivarDef def, CultivarInfo info, List<Problem> problems) {
    }

    /** Largest {@code food_mult} (economy §2.2 uses up to 1.5). */
    public static final double MAX_FOOD_MULT = 16;
    private static final Pattern KEY = Pattern.compile("[a-z0-9_.]{1," + DataLimits.MAX_TRANSLATION_KEY + "}");

    /** Applies defaults (with WARN) and checks the mod-only fields (ERROR). */
    public static Mapped toDefinition(Raw raw) {
        List<Problem> p = new ArrayList<>();
        Requires req = raw.requires().orElse(new Requires(Optional.empty(), Optional.empty(), Optional.empty()));
        Map<TraitId, Range> quant = new TreeMap<>();
        req.quantitative().orElse(Map.of()).forEach((k, r) ->
                quant.put(TraitId.of(k), new Range(r.min().orElse(0.0), r.max().orElse(1.0))));
        SortedMap<TraitId, QualReq> qual = reqs(req.qualitative().orElse(Map.of()));
        List<SortedMap<TraitId, QualReq>> anyOf = req.anyOf().orElse(List.of()).stream().map(CultivarSchema::reqs)
                .toList();
        Produce produce = raw.produce().orElse(new Produce(Optional.empty(), Optional.empty(), Optional.empty(),
                Optional.empty(), Optional.empty()));
        Map<TraitId, Double> axes = new TreeMap<>();
        produce.qualityAxes().orElse(Map.of()).forEach((k, v) -> axes.put(TraitId.of(k), v));
        Optional<CultivarId> parent = raw.parent().map(CultivarId::of);
        CultivarDef def = new CultivarDef(CultivarId.of(raw.id()), raw.species(),
                Defaults.or(raw.tier().orElse(null), 0, "tier", p),
                raw.priority().orElse(0),
                new TreeMap<>(quant), new TreeMap<>(qual), anyOf,
                Defaults.or(raw.hysteresis().orElse(null), Defaults.HYSTERESIS, "hysteresis", p),
                new TreeMap<>(axes), parent);

        ResourceLocation id = ResourceLocation.tryParse(raw.id());
        if (id == null) {
            id = Ids.mod("invalid");
            p.add(Problem.error("id", "invalid id " + raw.id()));
        }
        Optional<CultivarInfo.FoodReference> food = Optional.empty();
        if (produce.foodReference().isPresent()) {
            Food fr = produce.foodReference().get();
            boolean ok = DataLimits.range(fr.nutrition(), 0, DataLimits.MAX_FOOD_NUTRITION,
                    "produce.food_reference.nutrition", p);
            ok &= DataLimits.range(fr.saturation(), 0, DataLimits.MAX_FOOD_SATURATION,
                    "produce.food_reference.saturation", p);
            if (ok) {
                food = Optional.of(new CultivarInfo.FoodReference(fr.nutrition(), fr.saturation()));
            }
        }
        double mult = produce.foodMult().orElse(1.0);
        DataLimits.range(mult, 0, MAX_FOOD_MULT, "produce.food_mult", p);
        String help = raw.historicalNote().orElse(new CultivarInfo(id, Optional.empty(), Optional.empty(), 1, Optional.empty(), "")
                .translationKey() + ".note");
        if (!KEY.matcher(help).matches()) {
            p.add(Problem.error("historical_note", "translation key must match " + KEY.pattern()));
        }
        CultivarInfo info = new CultivarInfo(id, produce.item(), food, mult, produce.motherItem(), help);
        return new Mapped(def, info, p);
    }

    private static SortedMap<TraitId, QualReq> reqs(Map<String, String> in) {
        SortedMap<TraitId, QualReq> out = new TreeMap<>();
        in.forEach((k, v) -> out.put(TraitId.of(k), "domestic".equals(v) ? QualReq.DOMESTIC : QualReq.WILD));
        return out;
    }
}
