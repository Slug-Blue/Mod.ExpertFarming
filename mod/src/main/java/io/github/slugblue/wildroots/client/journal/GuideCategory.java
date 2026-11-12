package io.github.slugblue.wildroots.client.journal;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * A guide category (journal v2 §2.2, §5.2): a tab of the book and a row of
 * the contents. Its name is the lang key {@link #titleKey()}.
 */
@OnlyIn(Dist.CLIENT)
public record GuideCategory(String id, GuideIcon icon, int order) {

    /** The only group of the JSON categories; the "My records" side is built by code. */
    public static final String GROUP_GUIDE = "guide";

    public String titleKey() {
        return "wildroots.journal.category." + id;
    }
}
