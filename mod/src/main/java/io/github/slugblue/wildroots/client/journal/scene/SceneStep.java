package io.github.slugblue.wildroots.client.journal.scene;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * One step of a scene (journal v2 §3.13, §5.8): its caption key, length
 * in ticks, opening rule, optional camera move, actions (sorted by
 * {@code at}, stable) and the icons of its 2D frame.
 *
 * @param unlock shown only when one of these ids is open (§2.3; empty = always)
 * @param unless shown only when none of these ids is open: the variant of a
 *               step that another step with {@code unlock} replaces (§3.6,
 *               the brush)
 */
@OnlyIn(Dist.CLIENT)
public record SceneStep(String caption, int duration, List<String> unlock, List<String> unless,
                        Optional<Move> camera, List<SceneAction> actions, List<FrameIcon> frame) {

    public SceneStep {
        unlock = List.copyOf(unlock);
        unless = List.copyOf(unless);
        actions = actions.stream().sorted(Comparator.comparingInt(SceneAction::at)).toList();
        frame = List.copyOf(frame);
    }

    /** A camera move of the step: to {@code target} over {@code ease} ticks. */
    public record Move(SceneCamera target, int ease) {
    }

    /** One icon of the 2D frame: an item or a block-atlas sprite. */
    public sealed interface FrameIcon {
    }

    public record FrameItem(ResourceLocation item) implements FrameIcon {
    }

    public record FrameSprite(ResourceLocation sprite) implements FrameIcon {
    }
}
