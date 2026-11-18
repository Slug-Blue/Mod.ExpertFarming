package io.github.slugblue.wildroots.worldgen;

import io.github.slugblue.wildroots.core.Ids;
import io.github.slugblue.wildroots.genetics.api.SpeciesDef;
import io.github.slugblue.wildroots.genetics.model.TraitId;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.biome.Biome;

/** Natural colour sources only; never rewrites an inherited or already stored genome. */
public final class WildColourHabitats {
    public static final TagKey<Biome> PURPLE = TagKey.create(Registries.BIOME, Ids.mod("wild_colour/wild_carrot_purple"));
    public static final TagKey<Biome> YELLOW = TagKey.create(Registries.BIOME, Ids.mod("wild_colour/wild_carrot_yellow"));
    private static final TraitId P = TraitId.of("color_p");
    private static final TraitId Y1 = TraitId.of("color_y1");

    private WildColourHabitats() { }

    /** Allowed habitats keep the species' rare frequency; elsewhere even hidden carriers are absent. */
    public static Map<TraitId, Double> frequencies(SpeciesDef def, Holder<Biome> biome) {
        if (!def.id().equals("wildroots:wild_carrot")) {
            return Map.of();
        }
        Map<TraitId, Double> out = new LinkedHashMap<>();
        if (!biome.is(PURPLE)) {
            out.put(P, 0.0);
        }
        if (!biome.is(YELLOW)) {
            out.put(Y1, 0.0);
        }
        return Map.copyOf(out);
    }
}
