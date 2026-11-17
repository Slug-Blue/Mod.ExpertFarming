package io.github.slugblue.wildroots.item;

import io.github.slugblue.wildroots.data.CultivarInfo;
import io.github.slugblue.wildroots.data.Definitions;
import io.github.slugblue.wildroots.data.DefinitionsSnapshot;
import io.github.slugblue.wildroots.genetics.api.ColourProps;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.food.FoodProperties;

/**
 * Food of graded items (docs/05-architecture.md §12.3; GDD §6.4, §3.6):
 * {@code n = max(1, round(n_ref·m))}, {@code s = s_ref·m}, bitter ×0.5
 * saturation plus nausea for 5 s (poison for 4 s if the bitterness is
 * toxic). {@code FoodProperties} are cached per (reference, grade, bitter),
 * so one item never has more than 6 objects for its reference.
 */
public final class GradedFood {

    /** Nausea of a bitter harvest: 5 s. */
    public static final int NAUSEA_TICKS = 100;
    /** Poison of a toxic bitter harvest: 4 s. */
    public static final int POISON_TICKS = 80;

    private record Key(int nRef, double sRef, int grade, boolean bitter, boolean toxic, double colourMult) {
    }

    private static final Map<Key, FoodProperties> CACHE = new ConcurrentHashMap<>();

    private GradedFood() {
    }

    public static FoodProperties of(CultivarInfo.FoodReference ref, int grade, boolean bitter, boolean toxic) {
        return of(ref, grade, bitter, toxic, 1);
    }

    /** With the saturation multiplier of the root's colour ({@link #colourSaturation}); 1 = none. */
    public static FoodProperties of(CultivarInfo.FoodReference ref, int grade, boolean bitter, boolean toxic,
                                    double colourMult) {
        int g = ProduceStacks.normalise(grade, bitter);
        return CACHE.computeIfAbsent(new Key(ref.nutrition(), ref.saturation(), g, bitter, toxic, colourMult), k -> {
            FoodProperties.Builder b = new FoodProperties.Builder()
                    .nutrition(ProduceStacks.nutrition(k.nRef(), k.grade()))
                    .saturationMod(ProduceStacks.saturation(k.sRef(), k.grade(), k.bitter(), k.colourMult()));
            if (k.bitter()) {
                b.effect(() -> k.toxic() ? new MobEffectInstance(MobEffects.POISON, POISON_TICKS)
                        : new MobEffectInstance(MobEffects.CONFUSION, NAUSEA_TICKS), 1.0f);
            }
            return b.build();
        });
    }

    /** Cached objects (tests). */
    static int cached() {
        return CACHE.size();
    }

    /**
     * Properties of the root colour {@code colourName} (yellow, orange, purple, red) for the cultivar whose
     * harvest item is {@code item}, from the {@code colours.properties} of its species;
     * {@link ColourProps#ZERO} where the species (with its colour properties, which only the server has) is not
     * known. The tooltip words come from the sign of these numbers, never from the numbers (WR-704).
     */
    public static ColourProps colourProps(ResourceLocation item, String colourName) {
        DefinitionsSnapshot snap = Definitions.current();
        for (DefinitionsSnapshot.CultivarEntry c : snap.cultivars().values()) {
            if (c.info().produceItem().filter(item::equals).isPresent()) {
                return snap.species(c.def().species()).map(s -> {
                    var rules = s.def().colours();
                    return rules.props(rules.indexOf(colourName));
                }).orElse(ColourProps.ZERO);
            }
        }
        return ColourProps.ZERO;
    }

    /**
     * Saturation multiplier of the root colour {@code colourName} for the cultivar whose harvest item is
     * {@code item} ({@link #colourProps}); 1 where unknown.
     */
    public static double colourSaturation(ResourceLocation item, String colourName) {
        return colourProps(item, colourName).saturationMult();
    }

    /** {@code food_reference} of the cultivar whose harvest item is {@code item}, if loaded. */
    public static Optional<CultivarInfo.FoodReference> referenceOf(ResourceLocation item) {
        DefinitionsSnapshot snap = Definitions.current();
        for (DefinitionsSnapshot.CultivarEntry c : snap.cultivars().values()) {
            if (c.info().produceItem().filter(item::equals).isPresent()) {
                return c.info().foodReference();
            }
        }
        return Optional.empty();
    }

    /** Food multiplier of the cultivar whose harvest item is {@code item} ({@code food_mult}, economy §2.2), else 1. */
    public static double foodMultOf(ResourceLocation item) {
        for (DefinitionsSnapshot.CultivarEntry c : Definitions.current().cultivars().values()) {
            if (c.info().produceItem().filter(item::equals).isPresent()) {
                return c.info().foodMult();
            }
        }
        return 1.0;
    }
}
