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

    static List<Either<FormattedText, TooltipComponent>> elements(boolean panel) {
        List<Either<FormattedText, TooltipComponent>> e = new ArrayList<>();
        e.add(Either.left(Component.literal("Seeds")));
        if (panel) {
            e.add(Either.right(LensPanel.ofProduce(Component.literal("x"), List.of(), 2, false, true)));
        }
        e.add(Either.left(Component.literal("Generation: 1")));
        return e;
    }

    @Test
    void panelAloneWithoutShift() {
        List<Either<FormattedText, TooltipComponent>> e = elements(true);
        LensPanel.arrange(e, true, false);
        assertEquals(1, e.size());
        assertTrue(e.get(0).right().isPresent());
    }

    @Test
    void textAloneWithShiftOnAnItem() {
        List<Either<FormattedText, TooltipComponent>> e = elements(true);
        LensPanel.arrange(e, true, true);
        assertEquals(2, e.size());
        assertTrue(e.stream().allMatch(x -> x.left().isPresent()));
    }

    @Test
    void cursorPanelIgnoresShiftAndOtherTooltipsAreUntouched() {
        List<Either<FormattedText, TooltipComponent>> e = elements(true);
        LensPanel.arrange(e, false, true);
        assertEquals(1, e.size());
        List<Either<FormattedText, TooltipComponent>> plain = elements(false);
        LensPanel.arrange(plain, true, false);
        assertEquals(2, plain.size());
    }
}
