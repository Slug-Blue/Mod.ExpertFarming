package io.github.slugblue.wildroots.client.journal;

import java.util.Optional;
import java.util.regex.Pattern;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * The icon of a category or entry (journal v2 §5.2): an item id, or
 * {@code gui:<sprite>}, a 16 × 16 cell of {@code textures/gui/journal_icons.png}.
 */
@OnlyIn(Dist.CLIENT)
public record GuideIcon(Optional<ResourceLocation> item, Optional<String> sprite) {

    public static final GuideIcon NONE = new GuideIcon(Optional.empty(), Optional.empty());
    static final String GUI = "gui:";
    static final Pattern SPRITE = Pattern.compile("[a-z0-9_]{1,32}");

    public static GuideIcon item(ResourceLocation id) {
        return new GuideIcon(Optional.of(id), Optional.empty());
    }

    public static GuideIcon sprite(String name) {
        return new GuideIcon(Optional.empty(), Optional.of(name));
    }

    /** Parses an icon string; an unknown item is an error. */
    static GuideIcon parse(String s, GuideLoader.Registries reg) throws GuideFormatException {
        if (s.startsWith(GUI)) {
            String name = s.substring(GUI.length());
            if (!SPRITE.matcher(name).matches()) {
                throw new GuideFormatException("bad icon sprite " + s);
            }
            return sprite(name);
        }
        ResourceLocation rl = ResourceLocation.tryParse(s);
        if (rl == null || !GuideJson.LOCATION.matcher(s).matches()) {
            throw new GuideFormatException("bad icon " + s);
        }
        if (!reg.itemExists(rl)) {
            throw new GuideFormatException("unknown item " + rl);
        }
        return item(rl);
    }
}
