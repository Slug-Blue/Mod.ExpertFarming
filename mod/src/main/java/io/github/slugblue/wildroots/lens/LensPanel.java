package io.github.slugblue.wildroots.lens;

import io.github.slugblue.wildroots.core.safety.DispCodec;
import io.github.slugblue.wildroots.item.LotFlags;
import io.github.slugblue.wildroots.item.LotNbt;
import io.github.slugblue.wildroots.item.MotherItem;
import io.github.slugblue.wildroots.item.MotherNbt;
import io.github.slugblue.wildroots.item.ProduceItem;
import io.github.slugblue.wildroots.item.ProduceStacks;
import io.github.slugblue.wildroots.item.SeedLotItem;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.item.ItemStack;

/**
 * The lens panel (owner decision D2, 2026-11-11; {@code 00-vision.md} item
 * 33): while the hand lens is carried on the mouse cursor in a container
 * screen and hovers over a seed lot, a mother root or a produce item, the
 * client draws this panel with filled bars, one per tier-1 axis.
 *
 * <p>Display only, from data already on the stack: the compact tier-1
 * {@link LensRow} the server wrote into the {@code disp} of an inspected
 * lot or mother root, or the grade and bitterness of produce (it stores
 * nothing else). Nothing here reads a genome or computes genetics; a lot or
 * mother root without the row shows only the hint to click the lens on it
 * (the server inspects it then, {@code HandLensItem#overrideStackedOnOther}).
 *
 * @param lines the text lines: a header first, then the notes (bitterness,
 *              pollination, signs of inbreeding, "looks like", "and more")
 * @param bars  the bars, drawn right after the header
 */
public record LensPanel(List<Component> lines, List<Bar> bars) implements TooltipComponent {

    public static final String MOTHER = "wildroots.panel.lens.mother";
    public static final String ROOT = "wildroots.panel.lens.root";
    public static final String PRODUCE = "wildroots.panel.lens.produce";
    public static final String GRADE = "wildroots.panel.lens.grade";
    public static final String GRADE_WORD = "wildroots.panel.grade.";
    public static final String CLICK = "wildroots.panel.lens.click";
    /** Steps of a tier-1 axis ({@link LensScale}). */
    public static final int STEPS = 5;
    /** Grades of produce (GDD §6.4). */
    public static final int GRADES = 4;

    /**
     * One bar: a label, {@code step} of {@code max} segments filled and the
     * word of the step (the same word the tooltip shows).
     */
    public record Bar(String labelKey, int step, int max, String wordKey) {

        public Bar {
            max = Math.max(1, max);
            step = Math.max(0, Math.min(max, step));
        }

        public Component label() {
            return Component.translatable(labelKey);
        }

        public Component word() {
            return Component.translatable(wordKey);
        }
    }

    public LensPanel {
        lines = List.copyOf(lines);
        bars = List.copyOf(bars);
    }

    // ------------------------------------------------------------------ pure parts (JUnit)

    /** The bars of a tier-1 row of {@code species}: one per trait token, in layout order. */
    public static List<Bar> bars(LensRow row, String species) {
        List<Bar> out = new ArrayList<>();
        for (LensRow.Token t : LensRow.tokens(row.traits())) {
            out.add(new Bar(LensRow.traitNameKey(t, species), t.step(), STEPS, LensScale.wordKey(t.scale(), t.step())));
        }
        return out;
    }

    /** The grade bar of produce: grade 1–4 of 4. */
    public static Bar gradeBar(int grade) {
        int g = Math.max(1, Math.min(GRADES, grade));
        return new Bar(GRADE, g, GRADES, GRADE_WORD + g);
    }

    /** The first tier-1 row among {@code disp} lines, if any. */
    public static Optional<LensRow> row(List<DispCodec.Line> disp) {
        for (DispCodec.Line l : disp) {
            Optional<LensRow> r = LensRow.parse(l);
            if (r.isPresent()) {
                return r;
            }
        }
        return Optional.empty();
    }

    /** A panel of a lot or mother root: its tier-1 row as bars and notes, or the hint to click. */
    static LensPanel ofRow(String header, Optional<LensRow> row, String species) {
        List<Component> lines = new ArrayList<>();
        lines.add(Component.translatable(header));
        if (row.isEmpty()) {
            lines.add(Component.translatable(CLICK));
            return new LensPanel(lines, List.of());
        }
        lines.addAll(row.get().notes());
        return new LensPanel(lines, bars(row.get(), species));
    }

    /** A panel of produce: grade bar and bitterness. */
    static LensPanel ofProduce(int grade, boolean bitter) {
        List<Component> lines = new ArrayList<>();
        lines.add(Component.translatable(PRODUCE));
        if (bitter) {
            lines.add(Component.translatable("wildroots.grade.bitter"));
        }
        return new LensPanel(lines, List.of(gradeBar(grade)));
    }

    // ------------------------------------------------------------------ from a stack (display only)

    /** Whether the hand lens can inspect this stack by a click (a seed lot or a mother root). */
    public static boolean inspectable(ItemStack stack) {
        return stack.getItem() instanceof SeedLotItem || stack.getItem() instanceof MotherItem;
    }

    /** Whether the stack already carries its tier-1 row (inspected lot, inspected mother root). */
    public static boolean inspected(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        if (stack.getItem() instanceof SeedLotItem) {
            return LotNbt.read(LotNbt.root(tag)) instanceof LotNbt.Lot l && (l.flags() & LotFlags.INSPECTED) != 0
                    && row(LotNbt.disp(LotNbt.root(tag))).isPresent();
        }
        if (stack.getItem() instanceof MotherItem) {
            return tag != null && row(MotherNbt.disp(tag.get(MotherNbt.KEY))).isPresent();
        }
        return false;
    }

    /** The panel of {@code stack}: a seed lot, a mother root or produce; empty for anything else. */
    public static Optional<LensPanel> of(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        if (stack.getItem() instanceof SeedLotItem) {
            LotNbt.Result r = LotNbt.read(LotNbt.root(tag));
            String species = r instanceof LotNbt.Lot l ? l.species() : r instanceof LotNbt.Lazy z ? z.species() : null;
            if (species == null) {
                return Optional.empty();
            }
            boolean inspected = r instanceof LotNbt.Lot l && (l.flags() & LotFlags.INSPECTED) != 0;
            return Optional.of(ofRow(MOTHER, inspected ? row(LotNbt.disp(LotNbt.root(tag))) : Optional.empty(),
                    species));
        }
        if (stack.getItem() instanceof MotherItem) {
            return MotherItem.read(stack).map(m -> ofRow(ROOT, row(MotherNbt.disp(tag.get(MotherNbt.KEY))),
                    m.species()));
        }
        if (stack.getItem() instanceof ProduceItem) {
            return Optional.of(ofProduce(ProduceStacks.grade(tag), ProduceStacks.bitter(tag)));
        }
        return Optional.empty();
    }
}
