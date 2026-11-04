package io.github.slugblue.wildroots.worldgen;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Optional;
import net.minecraft.core.Holder;
import net.minecraft.util.valueproviders.IntProvider;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;

/**
 * Block {@code rich} of a wild patch (worldgen §5, §8.2). The genetics of a
 * rich patch (the extra elite loci on {@code yield} and {@code fruit_size},
 * frequencies {@code rich_elite}) belong to the species JSON and the engine
 * (WR-108); the patch only sets the {@code rich} flag of the passport.
 *
 * @param chance       share of rich patches (0.08)
 * @param minFromSpawn closer to the world spawn the patch is ordinary (256, §5.2)
 * @param plants       plant count of a rich patch (14–20); tries +50 %
 * @param landmark     spatial signature at the edge (boulder, §5.3)
 */
public record RichConfig(float chance, int minFromSpawn, IntProvider plants,
                         Optional<Holder<ConfiguredFeature<?, ?>>> landmark) {

    public static final Codec<RichConfig> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.floatRange(0, 1).fieldOf("chance").forGetter(RichConfig::chance),
            Codec.intRange(0, 30_000_000).optionalFieldOf("min_distance_from_spawn", 256)
                    .forGetter(RichConfig::minFromSpawn),
            IntProvider.codec(1, WildPatchConfig.MAX_PLANTS).fieldOf("plants").forGetter(RichConfig::plants),
            ConfiguredFeature.CODEC.optionalFieldOf("landmark").forGetter(RichConfig::landmark)
    ).apply(i, RichConfig::new));
}
