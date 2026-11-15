package io.github.slugblue.wildroots.item;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.slugblue.wildroots.core.safety.DispCodec;
import io.github.slugblue.wildroots.core.safety.NbtLimits;
import io.github.slugblue.wildroots.lens.LensPanel;
import io.github.slugblue.wildroots.lens.LensRow;
import io.github.slugblue.wildroots.lens.LensSheet;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import org.junit.jupiter.api.Test;

/**
 * Playtest 2, item 4: the styled panel is built from what the stack
 * carries. An inspected lot shows its name, generation and origin, the
 * mother's bars titled "Mother plant" with the offspring band, the flags
 * and "seeds vary"; an inspected root shows its bars with the noise band;
 * produce shows its grade bar and no band; anything uninspected keeps only
 * the hint.
 */
class LensPanelModelTest {

    static final LensRow ROW = new LensRow("s4*y3g2f3Wc3h2d2", 'w', 'B', true, Optional.of("eastern_carrot"), false)
            .withBands(new int[][] {{3, 5}, {2, 3}, {2, 2}, {3, 3}, {2, 4}, {1, 2}, {2, 2}});

    static String key(Component c) {
        return c.getContents() instanceof TranslatableContents t ? t.getKey() : c.getString();
    }

    static CompoundTag inspectedLot() {
        CompoundTag root = LotNbtTest.typical();
        LotNbt.Lot lot = (LotNbt.Lot) LotNbt.read(root);
        LotNbt.Lot marked = new LotNbt.Lot(lot.lotId(), lot.species(), lot.cultivar(), lot.mother(), lot.fathers(),
                lot.pOut(), lot.generation(), lot.lineName(), lot.flags() | LotFlags.INSPECTED, lot.rare(),
                lot.seedlingNoise());
        List<DispCodec.Line> d = new ArrayList<>(LotStacks.disp(marked));
        d.add(ROW.line());
        CompoundTag out = LotNbt.write(marked, d);
        assertTrue(NbtLimits.lot(out).ok(), "inspected lot with a band over its limit");
        return out;
    }

    @Test
    void inspectedLotPanel() {
        LensSheet s = LensPanel.ofLot(inspectedLot(), true).orElseThrow().sheet();
        assertEquals("wildroots.cultivar.eastern_carrot", key(s.title()));
        List<String> header = s.header().stream().map(LensPanelModelTest::key).toList();
        assertEquals(List.of("wildroots.tooltip.lot.generation", "wildroots.tooltip.lot.line_name",
                "wildroots.tooltip.lot.from_wild"), header, "header lines");
        assertEquals(LensSheet.Icon.POLLINATION_B, s.headIcon());
        assertEquals(LensSheet.MOTHER, key(s.barsTitle().orElseThrow()));
        assertEquals(7, s.bars().size());
        assertTrue(s.bars().stream().allMatch(LensSheet.Bar::hasBand), "bars without the band");
        assertEquals(3, s.bars().get(0).bandLo());
        assertEquals(5, s.bars().get(0).bandHi());
        assertEquals(4, s.bars().get(0).step());
        List<LensSheet.Icon> icons = s.flags().stream().map(LensSheet.Flag::icon).toList();
        assertEquals(List.of(LensSheet.Icon.BITTER, LensSheet.Icon.POLLINATION_B, LensSheet.Icon.AILING,
                LensSheet.Icon.LIKE_CULTIVAR), icons);
        List<String> notes = s.notes().stream().map(LensPanelModelTest::key).toList();
        assertEquals(List.of(LensSheet.VARY, LensPanel.SHIFT), notes);
        // the cursor panel has no Shift hint
        assertFalse(LensPanel.ofLot(inspectedLot(), false).orElseThrow().sheet().notes().stream()
                .anyMatch(c -> key(c).equals(LensPanel.SHIFT)));
    }

    /** Review 1, m4: a realised seedling lot keeps its genome and noise: no band, no "Mother plant", no "seeds vary". */
    @Test
    void seedlingLotHasNoBandAndNoVaryNote() {
        LotStacks.Parent parent = new LotStacks.Parent(LotNbtTest.SP, Optional.empty(), LotNbtTest.genome(5),
                Optional.empty(), 2, 0);
        CompoundTag root = LotStacks.seedlingTag(parent, new byte[] {1, 2, 3, 4, 5, 6, 7}, 0x77L);
        LotNbt.Lot lot = (LotNbt.Lot) LotNbt.read(root);
        assertTrue(lot.seedling());
        LotNbt.Lot marked = new LotNbt.Lot(lot.lotId(), lot.species(), lot.cultivar(), lot.mother(), lot.fathers(),
                lot.pOut(), lot.generation(), lot.lineName(), lot.flags() | LotFlags.INSPECTED, lot.rare(),
                lot.seedlingNoise());
        List<DispCodec.Line> d = new ArrayList<>(LotStacks.disp(marked));
        d.add(ROW.line()); // even a forged band on a seedling row is not drawn
        LensSheet s = LensPanel.ofLot(LotNbt.write(marked, d), false).orElseThrow().sheet();
        assertEquals(7, s.bars().size());
        assertTrue(s.bars().stream().noneMatch(LensSheet.Bar::hasBand), "seedling with a band");
        assertTrue(s.barsTitle().isEmpty(), "seedling bars titled Mother plant");
        assertTrue(s.notes().stream().noneMatch(c -> key(c).equals(LensSheet.VARY)), "seedling says seeds vary");
    }

    @Test
    void uninspectedLotShowsOnlyTheHint() {
        LensSheet s = LensPanel.ofLot(LotNbtTest.typical(), true).orElseThrow().sheet();
        assertTrue(s.bars().isEmpty() && s.flags().isEmpty(), "uninspected lot has bars");
        assertEquals(List.of(LensPanel.CLICK), s.notes().stream().map(LensPanelModelTest::key).toList());
        // a row in disp without the inspected flag does not count
        CompoundTag forged = LotNbtTest.typical();
        LotNbt.Lot lot = (LotNbt.Lot) LotNbt.read(forged);
        List<DispCodec.Line> d = new ArrayList<>(LotStacks.disp(lot));
        d.add(ROW.line());
        assertTrue(LensPanel.ofLot(LotNbt.write(lot, d), true).orElseThrow().sheet().bars().isEmpty());
        assertTrue(LensPanel.ofLot(new CompoundTag(), true).isEmpty(), "panel of no lot");
        assertTrue(LensPanel.ofLot(null, true).isEmpty(), "panel of null");
    }

    @Test
    void motherRootPanelHasTheNoiseBand() {
        CompoundTag base = ItemRootBoundaryTest.fullMother();
        assertTrue(LensPanel.ofMother(base, true).orElseThrow().sheet().bars().isEmpty(), "uninspected root bars");
        LensRow rootRow = new LensRow("s4*y3g2f3Wc3h2d2", 'w', '-', false, Optional.empty(), false)
                .withBands(new int[][] {{4, 4}, {3, 3}, {2, 2}, {3, 3}, {3, 3}, {2, 2}, {2, 2}});
        CompoundTag root = MotherNbt.withRow(base, rootRow.degradations().stream().map(LensRow::line).toList())
                .orElseThrow();
        assertEquals(Optional.of(rootRow), LensPanel.row(MotherNbt.disp(root)), "the banded row did not fit 512 B");
        LensSheet s = LensPanel.ofMother(root, true).orElseThrow().sheet();
        assertEquals("wildroots.cultivar.eastern_carrot", key(s.title()));
        assertEquals(7, s.bars().size());
        assertTrue(s.bars().stream().allMatch(LensSheet.Bar::hasBand));
        assertTrue(s.barsTitle().isEmpty(), "a root's bars are its own");
        assertEquals(List.of(LensPanel.ROOT_VARY, LensPanel.SHIFT), s.notes().stream().map(LensPanelModelTest::key)
                .toList());
    }

    @Test
    void producePanelHasTheGradeBarAndNoBand() {
        LensSheet s = LensPanel.ofProduce(Component.literal("Carrot"), List.of(), 3, true, true).sheet();
        assertEquals(1, s.bars().size());
        LensSheet.Bar g = s.bars().get(0);
        assertEquals(3, g.step());
        assertEquals(LensPanel.GRADES, g.max());
        assertFalse(g.hasBand(), "produce has no genome, no band");
        assertEquals(List.of(LensSheet.Icon.BITTER), s.flags().stream().map(LensSheet.Flag::icon).toList());
        assertTrue(LensPanel.ofProduce(Component.literal("Carrot"), List.of(), 9, false, false).sheet().flags()
                .isEmpty());
        assertEquals(4, LensPanel.ofProduce(Component.literal("Carrot"), List.of(), 9, false, false).sheet().bars()
                .get(0).step());
    }
}
