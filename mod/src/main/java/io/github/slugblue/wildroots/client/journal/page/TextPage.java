package io.github.slugblue.wildroots.client.journal.page;

import com.google.gson.JsonObject;
import io.github.slugblue.wildroots.client.journal.GuideFormatException;
import io.github.slugblue.wildroots.client.journal.GuideJson;
import io.github.slugblue.wildroots.client.journal.GuideLimits;
import java.util.Optional;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/** {@code text}: a lang key of text, an optional heading key (journal v2 §3.1); a long text runs over pages. */
@OnlyIn(Dist.CLIENT)
public record TextPage(Optional<String> title, String text) implements PageData {

    public static final String TYPE = "text";

    static TextPage parse(JsonObject o, PageParser.Context ctx) throws GuideFormatException {
        return new TextPage(GuideJson.optString(o, "title", GuideLimits.LANG_KEY),
                GuideJson.string(o, "text", GuideLimits.LANG_KEY));
    }
}
