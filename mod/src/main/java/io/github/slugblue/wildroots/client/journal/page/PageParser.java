package io.github.slugblue.wildroots.client.journal.page;

import com.google.gson.JsonObject;
import io.github.slugblue.wildroots.client.journal.GuideFormatException;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/** Reads the fields of one page type; a wrong field throws and the page is skipped (journal v2 §5.2). */
@OnlyIn(Dist.CLIENT)
@FunctionalInterface
public interface PageParser {

    /** What a parser may ask about the game while loading: only registries, no world. */
    interface Context {

        /** Whether an item id is in the item registry. */
        boolean itemExists(ResourceLocation item);
    }

    PageData parse(JsonObject page, Context ctx) throws GuideFormatException;
}
