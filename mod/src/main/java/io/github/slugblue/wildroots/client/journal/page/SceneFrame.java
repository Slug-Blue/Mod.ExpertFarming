package io.github.slugblue.wildroots.client.journal.page;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.github.slugblue.wildroots.client.journal.GuideFormatException;
import io.github.slugblue.wildroots.client.journal.GuideJson;
import io.github.slugblue.wildroots.client.journal.GuideLimits;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * The 2D fallback frame of a scene (journal v2 §3.13; WR-181d): for each
 * step its caption key, its {@code unlock} and its {@code frame} (up to
 * {@value #MAX_ICONS} icons: an item or a block-atlas sprite). Built from
 * the same scene JSON as the 3D scene, reading only these fields, so it
 * works without the scene engine and when the 3D scene is refused.
 *
 * <p>{@code frame_layout} (optional, top level of the scene file):
 * {@code row} — the current step's icons in a row with arrows (the growth
 * stages, §3.3); {@code ring} — one icon per step around a ring with the
 * current one highlighted (the biennial cycle, §3.6). Default {@code row}.
 */
@OnlyIn(Dist.CLIENT)
public record SceneFrame(String id, Layout layout, List<Step> steps) {

    public static final int MAX_STEPS = 16;
    public static final int MAX_ICONS = 8;

    public enum Layout {
        ROW, RING
    }

    /** An icon: an item, or a sprite of the block atlas ({@code wildroots:block/wild_carrot_stage0}). */
    public record Icon(Optional<ResourceLocation> item, Optional<ResourceLocation> sprite) {
    }

    /** {@code unless}: the step is left out when one of these is open (the step a variant replaces). */
    public record Step(String caption, List<String> unlock, List<String> unless, List<Icon> icons) {

        public Step {
            unlock = List.copyOf(unlock);
            unless = List.copyOf(unless);
            icons = List.copyOf(icons);
        }
    }

    public SceneFrame {
        steps = List.copyOf(steps);
    }

    /** Reads the fallback fields of a scene file; anything else in it is the engine's and is ignored here. */
    public static SceneFrame parse(JsonObject o) throws GuideFormatException {
        String id = GuideJson.string(o, "id", GuideLimits.ID);
        String layout = GuideJson.optString(o, "frame_layout", GuideLimits.ID).orElse("row");
        Layout l = switch (layout) {
            case "row" -> Layout.ROW;
            case "ring" -> Layout.RING;
            default -> throw new GuideFormatException("unknown frame_layout " + layout);
        };
        if (!(o.get("steps") instanceof JsonArray arr) || arr.isEmpty()) {
            throw new GuideFormatException("\"steps\" is not a list of steps");
        }
        if (arr.size() > MAX_STEPS) {
            throw new GuideFormatException("more than " + MAX_STEPS + " steps");
        }
        List<Step> steps = new ArrayList<>();
        for (JsonElement e : arr) {
            if (!(e instanceof JsonObject s)) {
                throw new GuideFormatException("a step is not an object");
            }
            List<Icon> icons = new ArrayList<>();
            if (s.get("frame") instanceof JsonArray f) {
                if (f.size() > MAX_ICONS) {
                    throw new GuideFormatException("a frame has more than " + MAX_ICONS + " icons");
                }
                for (JsonElement ie : f) {
                    if (!(ie instanceof JsonObject io)) {
                        throw new GuideFormatException("a frame icon is not an object");
                    }
                    Optional<ResourceLocation> item = GuideJson.optLocation(io, "item");
                    Optional<ResourceLocation> sprite = GuideJson.optLocation(io, "sprite");
                    if (item.isPresent() == sprite.isPresent()) {
                        throw new GuideFormatException("a frame icon needs exactly one of \"item\" and \"sprite\"");
                    }
                    icons.add(new Icon(item, sprite));
                }
            } else if (s.has("frame")) {
                throw new GuideFormatException("\"frame\" is not a list");
            }
            steps.add(new Step(GuideJson.string(s, "caption", GuideLimits.LANG_KEY),
                    GuideJson.list(s, "unlock", GuideLimits.UNLOCK), GuideJson.list(s, "unless", GuideLimits.UNLOCK),
                    icons));
        }
        return new SceneFrame(id, l, steps);
    }
}
