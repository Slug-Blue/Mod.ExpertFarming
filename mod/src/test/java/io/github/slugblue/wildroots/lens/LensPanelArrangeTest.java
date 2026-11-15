package io.github.slugblue.wildroots.lens;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mojang.datafixers.util.Either;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import org.junit.jupiter.api.Test;

/** Playtest 2, item 4: the styled panel owns the tooltip; Shift on an item shows the text lines instead. */
class LensPanelArrangeTest {

    /** Name, the panel, our two lines, another mod's line, the F3+H id line. */
    static List<Either<FormattedText, TooltipComponent>> elements(boolean panel) {
        List<Either<FormattedText, TooltipComponent>> e = new ArrayList<>();
        e.add(Either.left(Component.translatable("item.wildroots.seed_lot")));
        if (panel) {
            e.add(Either.right(LensPanel.ofProduce(Component.literal("x"), List.of(), 2, false, true)));
        }
        e.add(Either.left(Component.translatable("wildroots.tooltip.lot.generation", "1")));
        e.add(Either.left(Component.translatable("wildroots.tooltip.lot.from_wild")));
        e.add(Either.left(Component.literal("Other Mod")));
        e.add(Either.left(Component.literal("wildroots:seed_lot")));
        return e;
    }

    static List<String> text(List<Either<FormattedText, TooltipComponent>> e) {
        return e.stream().map(x -> x.map(t -> t instanceof Component c && c.getContents()
                instanceof net.minecraft.network.chat.contents.TranslatableContents k ? k.getKey() : t.getString(),
                c -> "PANEL")).toList();
    }

    @Test
    void withoutShiftThePanelReplacesOnlyOurLines() {
        List<Either<FormattedText, TooltipComponent>> e = elements(true);
        LensPanel.arrange(e, true, false);
        assertEquals(List.of("item.wildroots.seed_lot", "PANEL", "Other Mod", "wildroots:seed_lot"), text(e));
    }

    @Test
    void textAloneWithShiftOnAnItem() {
        List<Either<FormattedText, TooltipComponent>> e = elements(true);
        LensPanel.arrange(e, true, true);
        assertEquals(5, e.size());
        assertTrue(e.stream().allMatch(x -> x.left().isPresent()));
    }

    @Test
    void cursorPanelIgnoresShiftAndOtherTooltipsAreUntouched() {
        List<Either<FormattedText, TooltipComponent>> e = elements(true);
        LensPanel.arrange(e, false, true);
        assertEquals(List.of("PANEL"), text(e));
        List<Either<FormattedText, TooltipComponent>> plain = elements(false);
        LensPanel.arrange(plain, true, false);
        assertEquals(5, plain.size());
    }
}
