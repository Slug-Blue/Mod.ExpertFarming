package io.github.slugblue.wildroots.client.journal.page;

import com.google.gson.JsonObject;
import io.github.slugblue.wildroots.client.journal.GuideFormatException;
import io.github.slugblue.wildroots.client.journal.GuideJson;
import io.github.slugblue.wildroots.client.journal.GuideLimits;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * {@code spotlight}: one item large, its name, an optional "you have it"
 * badge and up to 8 lines of text (journal v2 §3.1). {@code variant} is
 * only one of {@link #VARIANTS}, the display stacks the client knows.
 */
@OnlyIn(Dist.CLIENT)
public record SpotlightPage(ResourceLocation item, Optional<String> variant, Optional<String> title,
                            Optional<String> text, boolean ownedBadge) implements PageData {

    public static final String TYPE = "spotlight";
    /** Display variants built on the client (journal v2 §3.1); the stacks never leave the client. */
    public static final Set<String> VARIANTS = Set.of("colour=yellow", "colour=purple", "colour=red");
    static final Pattern VARIANT = Pattern.compile("[a-z_]{1,32}=[a-z0-9_]{1,32}");

    static SpotlightPage parse(JsonObject o, PageParser.Context ctx) throws GuideFormatException {
        ResourceLocation item = GuideJson.location(o, "item");
        if (!ctx.itemExists(item)) {
            throw new GuideFormatException("unknown item " + item);
        }
        Optional<String> variant = GuideJson.optString(o, "variant", VARIANT);
        if (variant.isPresent() && !VARIANTS.contains(variant.get())) {
            throw new GuideFormatException("unknown variant " + variant.get());
        }
        return new SpotlightPage(item, variant, GuideJson.optString(o, "title", GuideLimits.LANG_KEY),
                GuideJson.optString(o, "text", GuideLimits.LANG_KEY), GuideJson.bool(o, "owned_badge", false));
    }
}
