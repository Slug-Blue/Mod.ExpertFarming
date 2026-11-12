package io.github.slugblue.wildroots.client.journal.page;

import com.google.gson.JsonObject;
import io.github.slugblue.wildroots.client.journal.GuideFormatException;
import io.github.slugblue.wildroots.client.journal.GuideJson;
import io.github.slugblue.wildroots.client.journal.GuideLimits;
import java.util.List;
import java.util.Optional;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/** {@code links}: a row of links to other entries (journal v2 §2.4); closed ones are not shown. */
@OnlyIn(Dist.CLIENT)
public record LinksPage(Optional<String> title, List<String> links) implements PageData {

    public static final String TYPE = "links";

    static LinksPage parse(JsonObject o, PageParser.Context ctx) throws GuideFormatException {
        List<String> links = GuideJson.list(o, "links", GuideLimits.ID);
        if (links.isEmpty()) {
            throw new GuideFormatException("\"links\" is empty");
        }
        return new LinksPage(GuideJson.optString(o, "title", GuideLimits.LANG_KEY), links);
    }
}
