package io.github.slugblue.wildroots.client.journal.page;

import com.google.gson.JsonObject;
import io.github.slugblue.wildroots.client.journal.GuideFormatException;
import io.github.slugblue.wildroots.client.journal.GuideJson;
import io.github.slugblue.wildroots.client.journal.GuideLimits;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * {@code scene}: a 3D scene over the whole spread (journal v2 §3.13),
 * named by its id; the scene file itself ({@code journal/scenes/<id>.json},
 * §5.8) belongs to the scene engine ({@code client.journal.scene}).
 */
@OnlyIn(Dist.CLIENT)
public record ScenePage(String scene) implements PageData {

    public static final String TYPE = "scene";

    static ScenePage parse(JsonObject o, PageParser.Context ctx) throws GuideFormatException {
        return new ScenePage(GuideJson.string(o, "scene", GuideLimits.ID));
    }
}
