package io.github.slugblue.wildroots.worldgen;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.HolderSet;
import net.minecraft.core.RegistryCodecs;
import net.minecraft.core.registries.Registries;
import net.minecraft.util.valueproviders.IntProvider;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.levelgen.feature.configurations.FeatureConfiguration;

/**
 * Configuration of {@code wildroots:wild_patch} (worldgen §3, §8.2): one
 * configured feature per species, data only.
 *
 * @param species     species id ({@code wildroots:wild_carrot})
 * @param categories  patch categories in priority order; the first whose
 *                    biome tag {@code wildroots:patch_category/<id>} holds
 *                    the origin biome is the category (worldgen §2.1)
 * @param plants      target plant count (at most {@value #MAX_PLANTS})
 * @param minPlants   fewer placed plants than this: no patch at all (§3.2 item 4)
 * @param tries       placement attempts
 * @param xzSpread    horizontal spread, triangular (≤ {@value #MAX_SPREAD}, worldgen §12.1)
 * @param ySpread     vertical spread
 * @param soil        blocks a plant may stand on
 * @param replaceable blocks a plant may replace besides air
 * @param stages      shares of mature / flowering / seeding plants
 * @param beeNest     bee nest step (worldgen §7), absent for wind-pollinated species
 * @param rich        rich patches (worldgen §5), absent: never rich
 */
public record WildPatchConfig(String species, List<String> categories, IntProvider plants, int minPlants, int tries,
                              int xzSpread, int ySpread, HolderSet<Block> soil, HolderSet<Block> replaceable,
                              Stages stages, Optional<BeeNestConfig> beeNest,
                              Optional<RichConfig> rich) implements FeatureConfiguration {

    /** Plants per patch, hard limit (worldgen §12.1). */
    public static final int MAX_PLANTS = 24;
    /** Spread limit: the patch and its nest stay within the 3×3 write region (worldgen §7.2, §12.1). */
    public static final int MAX_SPREAD = 6;

    /** Shares of the stages (worldgen §3.2 item 5); the mature share is what is left. */
    public record Stages(float mature, float flowering, float seeding) {

        public static final Codec<Stages> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.floatRange(0, 1).fieldOf("mature").forGetter(Stages::mature),
                Codec.floatRange(0, 1).fieldOf("flowering").forGetter(Stages::flowering),
                Codec.floatRange(0, 1).fieldOf("seeding").forGetter(Stages::seeding)
        ).apply(i, Stages::new));
    }

    public static final Codec<WildPatchConfig> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.STRING.fieldOf("species").forGetter(WildPatchConfig::species),
            Codec.STRING.listOf().fieldOf("categories").forGetter(WildPatchConfig::categories),
            IntProvider.codec(1, MAX_PLANTS).fieldOf("plants").forGetter(WildPatchConfig::plants),
            Codec.intRange(1, MAX_PLANTS).optionalFieldOf("min_plants", 5).forGetter(WildPatchConfig::minPlants),
            Codec.intRange(1, 256).fieldOf("tries").forGetter(WildPatchConfig::tries),
            Codec.intRange(0, MAX_SPREAD).fieldOf("xz_spread").forGetter(WildPatchConfig::xzSpread),
            Codec.intRange(0, MAX_SPREAD).fieldOf("y_spread").forGetter(WildPatchConfig::ySpread),
            RegistryCodecs.homogeneousList(Registries.BLOCK).fieldOf("soil").forGetter(WildPatchConfig::soil),
            RegistryCodecs.homogeneousList(Registries.BLOCK).fieldOf("replaceable")
                    .forGetter(WildPatchConfig::replaceable),
            Stages.CODEC.fieldOf("stages").forGetter(WildPatchConfig::stages),
            BeeNestConfig.CODEC.optionalFieldOf("bee_nest").forGetter(WildPatchConfig::beeNest),
            RichConfig.CODEC.optionalFieldOf("rich").forGetter(WildPatchConfig::rich)
    ).apply(i, WildPatchConfig::new));

    /** The same configuration with another nest block (tests, starter patch). */
    public WildPatchConfig withBeeNest(Optional<BeeNestConfig> nest) {
        return new WildPatchConfig(species, categories, plants, minPlants, tries, xzSpread, ySpread, soil, replaceable,
                stages, nest, rich);
    }

    /** The same configuration with another rich block (tests). */
    public WildPatchConfig withRich(Optional<RichConfig> r) {
        return new WildPatchConfig(species, categories, plants, minPlants, tries, xzSpread, ySpread, soil, replaceable,
                stages, beeNest, r);
    }
}
