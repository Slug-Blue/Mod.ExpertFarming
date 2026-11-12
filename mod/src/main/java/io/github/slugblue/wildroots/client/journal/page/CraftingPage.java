package io.github.slugblue.wildroots.client.journal.page;

import com.google.gson.JsonObject;
import io.github.slugblue.wildroots.client.journal.GuideFormatException;
import io.github.slugblue.wildroots.client.journal.GuideJson;
import io.github.slugblue.wildroots.client.journal.GuideLimits;
import java.util.Optional;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * {@code crafting}: a recipe by id, drawn live from the client's recipe
 * manager (journal v2 §3.2), with an optional caption key. Whether the
 * recipe exists is known only in a world, so it is checked when the page
 * is laid out, not here.
 */
@OnlyIn(Dist.CLIENT)
public record CraftingPage(ResourceLocation recipe, Optional<String> text) implements PageData {

    public static final String TYPE = "crafting";

    static CraftingPage parse(JsonObject o, PageParser.Context ctx) throws GuideFormatException {
        return new CraftingPage(GuideJson.location(o, "recipe"), GuideJson.optString(o, "text", GuideLimits.LANG_KEY));
    }
}
