package io.github.slugblue.wildroots.client.journal.page;

import com.google.gson.JsonObject;
import io.github.slugblue.wildroots.client.journal.GuideFormatException;
import io.github.slugblue.wildroots.client.journal.GuideJson;
import java.util.List;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * {@code trait_legend}: the lens scale over a spread (journal v2 §3.4):
 * a species whose trait names the example uses, and fixed example steps
 * 1–5 in the order of the species' displayed traits.
 */
@OnlyIn(Dist.CLIENT)
public record TraitLegendPage(ResourceLocation species, List<Integer> example) implements PageData {

    public static final String TYPE = "trait_legend";
    static final ResourceLocation DEFAULT_SPECIES = io.github.slugblue.wildroots.core.Ids.mod("wild_carrot");

    static TraitLegendPage parse(JsonObject o, PageParser.Context ctx) throws GuideFormatException {
        return new TraitLegendPage(GuideJson.optLocation(o, "species").orElse(DEFAULT_SPECIES),
                GuideJson.intList(o, "example", 1, 5));
    }
}
