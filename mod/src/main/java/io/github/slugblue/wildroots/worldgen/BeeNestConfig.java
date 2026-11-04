package io.github.slugblue.wildroots.worldgen;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.Holder;
import net.minecraft.util.valueproviders.IntProvider;
import net.minecraft.util.valueproviders.UniformInt;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;

/**
 * Block {@code bee_nest} of a wild patch (worldgen §7, §8.2).
 *
 * @param chance          nest chance of an ordinary patch (carrot 0.6)
 * @param richChance      nest chance of a rich patch (1.0); the starter patch always gets one
 * @param minDistance     ring of the search, from the patch centre (6)
 * @param maxDistance     ring of the search (16)
 * @param bees            bees stored in the nest (2–3)
 * @param fallbacks       place cascade, in order: {@code tree}, {@code cliff}, {@code lone_tree}
 *                        ({@code driftwood} and {@code snag} belong to later lines)
 * @param loneTreeOnlyFor patch kinds allowed a lone tree: {@code starter}, {@code rich}
 * @param loneTreeFeature configured feature of the lone tree ({@code wildroots:lone_birch_bees}, data only)
 */
public record BeeNestConfig(float chance, float richChance, int minDistance, int maxDistance, IntProvider bees,
                            List<String> fallbacks, List<String> loneTreeOnlyFor,
                            Optional<Holder<ConfiguredFeature<?, ?>>> loneTreeFeature) {

    /** Largest search distance: the nest stays in the 3×3 write region (worldgen §7.2). */
    public static final int MAX_DISTANCE = 16;
    /** Lone trees stand closer, so that their crown stays in the region too (worldgen §7.2). */
    public static final int LONE_TREE_MAX_DISTANCE = 12;

    public static final Codec<BeeNestConfig> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.floatRange(0, 1).fieldOf("chance").forGetter(BeeNestConfig::chance),
            Codec.floatRange(0, 1).optionalFieldOf("rich_chance", 1.0f).forGetter(BeeNestConfig::richChance),
            Codec.intRange(0, MAX_DISTANCE).optionalFieldOf("min_distance", 6).forGetter(BeeNestConfig::minDistance),
            Codec.intRange(1, MAX_DISTANCE).optionalFieldOf("max_distance", MAX_DISTANCE)
                    .forGetter(BeeNestConfig::maxDistance),
            IntProvider.codec(0, 3).optionalFieldOf("bees", UniformInt.of(2, 3)).forGetter(BeeNestConfig::bees),
            Codec.STRING.listOf().fieldOf("fallbacks").forGetter(BeeNestConfig::fallbacks),
            Codec.STRING.listOf().optionalFieldOf("lone_tree_only_for", List.of("starter", "rich"))
                    .forGetter(BeeNestConfig::loneTreeOnlyFor),
            ConfiguredFeature.CODEC.optionalFieldOf("lone_tree_feature").forGetter(BeeNestConfig::loneTreeFeature)
    ).apply(i, BeeNestConfig::new));
}
