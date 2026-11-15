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
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.item.ItemStack;

/**
 * The lens panel: a {@link LensSheet} in the journal's paper style, used
 * in two places.
 * <ul>
 *   <li>D2 (owner, 2026-11-11; {@code 00-vision.md} item 33): while the
 *   hand lens is carried on the mouse cursor in a container screen and
 *   hovers over a seed lot, a mother root or produce;</li>
 *   <li>playtest 2, item 4: as the tooltip of an inspected seed lot, an
 *   inspected mother root and produce, instead of the long list of lines
 *   (Shift shows the old lines).</li>
 * </ul>
 *
 * <p>Display only, from data already on the stack: the compact tier-1
 * {@link LensRow} the server wrote into the {@code disp} of an inspected
 * lot or mother root (with its offspring band), the lot's own {@code disp}
 * lines (name, generation, origin), or the grade and bitterness of produce.
 * Nothing here reads a genome or computes genetics; a lot or mother root
 * without the row shows only the hint to click the lens on it.
 *
 * @param sheet what is drawn
 */
public record LensPanel(LensSheet sheet) implements TooltipComponent {

    public static final String GRADE = "wildroots.panel.lens.grade";
    public static final String GRADE_WORD = "wildroots.panel.grade.";
    public static final String CLICK = "wildroots.panel.lens.click";
    /** Under a planted root's bars: only the planting noise moves it (item 6). */
    public static final String ROOT_VARY = "wildroots.panel.lens.root_vary";
    /** The tooltip's hint that Shift shows the text lines. */
    public static final String SHIFT = "wildroots.panel.lens.shift";
    /** Steps of a tier-1 axis ({@link LensScale}). */
    public static final int STEPS = 5;
    /** Grades of produce (GDD §6.4). */
    public static final int GRADES = 4;

    /**
     * One bar of a row as keys (JUnit): a label, {@code step} of
     * {@code max} segments filled and the word of the step.
     */
    public record Bar(String labelKey, int step, int max, String wordKey) {

        public Bar {
            max = Math.max(1, max);
            step = Math.max(0, Math.min(max, step));
        }
    }

    public LensPanel {
        if (sheet == null) {
            throw new IllegalArgumentException("no sheet");
        }
    }

    /** The bars that are drawn. */
    public List<LensSheet.Bar> bars() {
        return sheet.bars();
    }

    // ------------------------------------------------------------------ pure parts (JUnit)

    /** The bars of a tier-1 row of {@code species} as keys: one per trait token, in layout order. */
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

    /** Whether a {@code disp} line of a lot goes into the panel's header (not the name, not pollination, not the row). */
    static boolean headerLine(DispCodec.Line l) {
        return !l.key().startsWith("wildroots.tooltip.lot.pollination.") && !l.key().equals(LensRow.KEY);
    }

    /**
     * The panel of a lot root (pure): its name as the title, generation,
     * line name and origin under it, then the mother's bars with the
     * offspring band, the flags and "seeds vary"; an uninspected lot gets
     * the hint to click the lens instead. Empty for no lot.
     */
    public static Optional<LensPanel> ofLot(Tag root, boolean shiftHint) {
        LotNbt.Result r = LotNbt.read(root);
        String species = r instanceof LotNbt.Lot l ? l.species() : r instanceof LotNbt.Lazy z ? z.species() : null;
        if (species == null) {
            return Optional.empty();
        }
        List<DispCodec.Line> disp = LotNbt.disp(root);
        Component title = disp.isEmpty() ? Component.translatable("item.wildroots.seed_lot")
                : DispCodec.toComponent(disp.get(0));
        List<Component> header = new ArrayList<>();
        for (int i = 1; i < disp.size(); i++) {
            if (headerLine(disp.get(i))) {
                header.add(DispCodec.toComponent(disp.get(i)));
            }
        }
        boolean inspected = r instanceof LotNbt.Lot l && (l.flags() & LotFlags.INSPECTED) != 0;
        Optional<LensRow> row = inspected ? row(disp) : Optional.empty();
        if (row.isEmpty()) {
            return Optional.of(new LensPanel(new LensSheet(title, header, LensSheet.Icon.NONE, Optional.empty(),
                    List.of(), List.of(), List.of(Component.translatable(CLICK)))));
        }
        boolean seedling = r instanceof LotNbt.Lot l && l.seedling();
        LensSheet s = LensSheet.lot(title, header, row.get(), species, seedling);
        return Optional.of(new LensPanel(shiftHint ? withNote(s, Component.translatable(SHIFT)) : s));
    }

    /**
     * The panel of a mother root (pure): name, grade and line name, its own
     * bars with the planting-noise band; the click hint if not inspected.
     */
    public static Optional<LensPanel> ofMother(Tag root, boolean shiftHint) {
        Optional<MotherNbt.Mother> m = MotherNbt.read(root);
        if (m.isEmpty()) {
            return Optional.empty();
        }
        String species = m.get().species();
        Component title = Component.translatable(m.get().cultivar().map(c -> nameKey("cultivar", c))
                .orElse(nameKey("species", species)));
        List<Component> header = new ArrayList<>();
        header.add(Component.translatable("wildroots.grade." + m.get().grade()));
        m.get().lineName().ifPresent(n -> header.add(Component.translatable("wildroots.tooltip.lot.line_name",
                Component.literal(n))));
        Optional<LensRow> row = row(MotherNbt.disp(root));
        if (row.isEmpty()) {
            return Optional.of(new LensPanel(new LensSheet(title, header, LensSheet.Icon.NONE, Optional.empty(),
                    List.of(), List.of(), List.of(Component.translatable(CLICK)))));
        }
        List<Component> notes = new ArrayList<>();
        if (!row.get().bands().isEmpty()) {
            notes.add(Component.translatable(ROOT_VARY));
        }
        if (row.get().more()) {
            notes.add(Component.translatable(LensRow.MORE_KEY));
        }
        if (shiftHint) {
            notes.add(Component.translatable(SHIFT));
        }
        return Optional.of(new LensPanel(new LensSheet(title, header, LensSheet.Icon.NONE, Optional.empty(),
                LensSheet.bars(row.get(), species), LensSheet.flags(row.get()), notes)));
    }

    /** The panel of produce (pure): its name, colour, the grade bar and bitterness; no band, it has no genome. */
    public static LensPanel ofProduce(Component title, List<Component> header, int grade, boolean bitter,
                                      boolean shiftHint) {
        Bar g = gradeBar(grade);
        List<LensSheet.Flag> flags = bitter ? List.of(new LensSheet.Flag(LensSheet.Icon.BITTER,
                Component.translatable("wildroots.grade.bitter"))) : List.of();
        List<Component> notes = shiftHint ? List.of(Component.translatable(SHIFT)) : List.of();
        return new LensPanel(new LensSheet(title, header, LensSheet.Icon.NONE, Optional.empty(),
                List.of(new LensSheet.Bar(Component.translatable(LensReading.TRAIT, Component.translatable(g.labelKey()),
                        Component.translatable(g.wordKey())), g.step(), g.max(), 0, 0)), flags, notes));
    }

    static LensSheet withNote(LensSheet s, Component note) {
        List<Component> notes = new ArrayList<>(s.notes());
        notes.add(note);
        return new LensSheet(s.title(), s.header(), s.headIcon(), s.barsTitle(), s.bars(), s.flags(), notes);
    }

    static String nameKey(String kind, String id) {
        return io.github.slugblue.wildroots.journal.JournalTriggers.nameKey(kind, id);
    }

    /**
     * The tooltip elements once the panel takes them over (item 4; pure,
     * JUnit). On an item without Shift the panel replaces only our own long
     * lines (translation keys {@code wildroots.*}): the item name (first
     * line), other mods' lines and the F3+H advanced lines stay (review 1,
     * m6). With Shift on an item, the text lines without the panel. The
     * cursor panel (no item) is the panel alone. A tooltip without a panel is
     * left as it is.
     */
    public static void arrange(List<com.mojang.datafixers.util.Either<net.minecraft.network.chat.FormattedText,
            TooltipComponent>> elements, boolean itemTooltip, boolean shift) {
        boolean panel = elements.stream().anyMatch(e -> e.right().filter(LensPanel.class::isInstance).isPresent());
        if (!panel) {
            return;
        }
        if (itemTooltip && shift) {
            elements.removeIf(e -> e.right().filter(LensPanel.class::isInstance).isPresent());
        } else if (itemTooltip) {
            for (int i = elements.size() - 1; i > 0; i--) {
                if (elements.get(i).left().filter(LensPanel::ours).isPresent()) {
                    elements.remove(i);
                }
            }
        } else {
            elements.removeIf(e -> e.left().isPresent());
        }
    }

    /** A line our {@code appendHoverText} wrote: a translatable {@code wildroots.*} key. */
    static boolean ours(net.minecraft.network.chat.FormattedText t) {
        return t instanceof Component c
                && c.getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents k
                && k.getKey().startsWith("wildroots.");
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

    /** The cursor panel (D2) of {@code stack}: a seed lot, a mother root or produce; empty for anything else. */
    public static Optional<LensPanel> of(ItemStack stack) {
        return of(stack, false);
    }

    /**
     * The panel of {@code stack}; {@code shiftHint} adds "Shift: text" (the
     * item tooltip, item 4).
     */
    public static Optional<LensPanel> of(ItemStack stack, boolean shiftHint) {
        CompoundTag tag = stack.getTag();
        if (stack.getItem() instanceof SeedLotItem) {
            return ofLot(LotNbt.root(tag), shiftHint);
        }
        if (stack.getItem() instanceof MotherItem) {
            return ofMother(tag == null ? null : tag.get(MotherNbt.KEY), shiftHint);
        }
        if (stack.getItem() instanceof ProduceItem p) {
            List<Component> header = new ArrayList<>();
            if (p.rootColours()) {
                net.minecraft.resources.ResourceLocation id = net.minecraftforge.registries.ForgeRegistries.ITEMS
                        .getKey(p);
                if (id != null) {
                    header.add(Component.translatable("wildroots.cultivar." + id.getPath() + ".variant."
                            + (ProduceStacks.colour(tag) == ProduceStacks.PURPLE ? "purple" : "yellow")));
                }
            }
            // on an item tooltip the name line stays above the panel (m6): no second title
            return Optional.of(ofProduce(shiftHint ? Component.empty() : stack.getHoverName(), header,
                    ProduceStacks.grade(tag),
                    ProduceStacks.bitter(tag), shiftHint));
        }
        return Optional.empty();
    }

    /**
     * The tooltip image of an item (item 4): the styled panel for an
     * inspected lot or mother root and for produce; none for anything else,
     * which keeps its short text tooltip.
     */
    public static Optional<TooltipComponent> tooltipImage(ItemStack stack) {
        // one entry per thread, keyed by the stack and its tag: hovering one stack parses it once
        Object[] last = LAST_IMAGE.get();
        if (last[0] == stack && last[1] == stack.getTag() && last[2] == stack.getItem()) {
            @SuppressWarnings("unchecked")
            Optional<TooltipComponent> cached = (Optional<TooltipComponent>) last[3];
            return cached;
        }
        Optional<TooltipComponent> image = stack.getItem() instanceof ProduceItem || inspected(stack)
                ? of(stack, true).map(p -> p) : Optional.empty();
        last[0] = stack;
        last[1] = stack.getTag();
        last[2] = stack.getItem();
        last[3] = image;
        return image;
    }

    private static final ThreadLocal<Object[]> LAST_IMAGE = ThreadLocal.withInitial(() -> new Object[4]);
}
