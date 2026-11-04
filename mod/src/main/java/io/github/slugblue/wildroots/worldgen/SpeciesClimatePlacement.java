package io.github.slugblue.wildroots.worldgen;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.slugblue.wildroots.core.registry.ModPlacementModifiers;
import io.github.slugblue.wildroots.core.safety.EntryPoint;
import io.github.slugblue.wildroots.core.safety.Guarded;
import io.github.slugblue.wildroots.data.Definitions;
import io.github.slugblue.wildroots.genetics.api.SpeciesDef;
import io.github.slugblue.wildroots.plant.ClimateService;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.placement.PlacementContext;
import net.minecraft.world.level.levelgen.placement.PlacementFilter;
import net.minecraft.world.level.levelgen.placement.PlacementModifierType;

/**
 * {@code wildroots:species_climate} (worldgen §4.2 row 6, §2.2; S11): the
 * temperature of the point, with the vanilla height adjustment, lies in the
 * species window ± {@code tolerance}. The temperature is
 * {@code ClimateService.temperature(context.getLevel(), pos)}, the same
 * function growth uses. Above Y = 80 vanilla adds noise of ±0.01, and frozen
 * biomes take noise into the base: there the window edges get a slack of
 * {@value ClimateWindow#EDGE_SLACK}. A species missing from the definitions (line off)
 * places nothing.
 */
public final class SpeciesClimatePlacement extends PlacementFilter {

    public static final Codec<SpeciesClimatePlacement> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.STRING.fieldOf("species").forGetter(SpeciesClimatePlacement::species),
            Codec.doubleRange(0, 2).optionalFieldOf("tolerance", 0.0).forGetter(SpeciesClimatePlacement::tolerance)
    ).apply(i, SpeciesClimatePlacement::new));

    private final String species;
    private final double tolerance;

    public SpeciesClimatePlacement(String species, double tolerance) {
        this.species = species;
        this.tolerance = tolerance;
    }

    public String species() {
        return species;
    }

    public double tolerance() {
        return tolerance;
    }

    /** Whether the temperature at {@code pos} carries vanilla noise (above Y 80, or a frozen biome). */
    public static boolean noisy(Holder<Biome> biome, BlockPos pos) {
        return pos.getY() > ClimateWindow.NOISE_ABOVE_Y
                || biome.value().getModifiedClimateSettings().temperatureModifier() == Biome.TemperatureModifier.FROZEN;
    }

    @Override
    @EntryPoint
    protected boolean shouldPlace(PlacementContext context, RandomSource random, BlockPos pos) {
        return Guarded.call("species_climate", pos, () -> fitsAt(context, pos), false);
    }

    private boolean fitsAt(PlacementContext context, BlockPos pos) {
        Optional<SpeciesDef> def = Definitions.current().species(species).map(e -> e.def());
        if (def.isEmpty()) {
            return false;
        }
        Holder<Biome> biome = context.getLevel().getBiome(pos);
        double t = ClimateService.temperature(context.getLevel(), pos);
        return ClimateWindow.fits(t, def.get().tempMin(), def.get().tempMax(), tolerance, noisy(biome, pos));
    }

    @Override
    public PlacementModifierType<?> type() {
        return ModPlacementModifiers.SPECIES_CLIMATE.get();
    }
}
