package io.github.slugblue.wildroots.data;

import java.util.Optional;
import net.minecraft.resources.ResourceLocation;

/**
 * Mod-side data of a cultivar or landrace (GDD §6.3, economy §2.2): the
 * harvest item, the food reference of grade III, the food multiplier, the
 * mother item and the help key. Part of it goes to clients in
 * {@code DefinitionsSyncPacket} (§8.3).
 *
 * @param id            cultivar id
 * @param produceItem   harvest item
 * @param foodReference nutrition and saturation of grade III (vanilla numbers)
 * @param foodMult      number-of-items multiplier
 * @param motherItem    mother root / clone item
 * @param helpKey       translation key of the historical note
 */
public record CultivarInfo(ResourceLocation id, Optional<ResourceLocation> produceItem,
                           Optional<FoodReference> foodReference, double foodMult,
                           Optional<ResourceLocation> motherItem, String helpKey) {

    /** {@code food_reference} of {@code produce} (economy §2.2). */
    public record FoodReference(int nutrition, double saturation) {
    }

    /** Translation key of the cultivar name (narrative §2). */
    public String translationKey() {
        return "wildroots.cultivar." + (id.getNamespace().equals("wildroots") ? id.getPath()
                : id.getNamespace() + "." + id.getPath());
    }
}
