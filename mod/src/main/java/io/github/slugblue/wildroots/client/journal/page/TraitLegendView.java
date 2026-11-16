package io.github.slugblue.wildroots.client.journal.page;

import io.github.slugblue.wildroots.client.ClientDefinitions;
import io.github.slugblue.wildroots.client.journal.GuidePage;
import io.github.slugblue.wildroots.client.journal.JournalColors;
import io.github.slugblue.wildroots.client.ui.TraitBar;
import io.github.slugblue.wildroots.data.sync.SyncedDefinitions;
import io.github.slugblue.wildroots.lens.LensScale;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * {@code trait_legend} (journal v2 §3.4; WR-181d, owner playtest 3): how to
 * read the lens with the same bar as the lens panel ({@link TraitBar}).
 * The content flows over half pages by {@link TraitLegendLayout}, which
 * measures every string with the real font, so nothing is cut or leaves the
 * page: the intro and the five steps of a scale (a click on the scale line
 * turns through the 8 scales of narrative §9.2), how to use the lens, the
 * produce grade, the flag marks, and an example of the species' traits named
 * as the species names them ({@code displayTraits}).
 */
@OnlyIn(Dist.CLIENT)
public final class TraitLegendView implements PageView {

    /** The 8 scales in the order of narrative §9.2. */
    public static final List<String> SCALES = List.of("size", "yield", "growth", "flavor", "sweetness", "tenderness",
            "tolerance", "expression");
    /** The traits of the example, in the order of the lens panel. */
    static final List<String> EXAMPLE_TRAITS = List.of("fruit_size", "yield", "growth_speed", "flavor",
            "cold_tolerance", "heat_tolerance", "drought_tolerance");
    static final TraitBar.Flag[] FLAGS = {TraitBar.Flag.BITTER, TraitBar.Flag.POLLINATION_B, TraitBar.Flag.AILING,
        TraitBar.Flag.LIKE_CULTIVAR};
    static final String[] FLAG_KEYS = {"bitter", "pollination", "ailing", "looks_like"};

    static String tr(String key) {
        return Component.translatable(key).getString();
    }

    /** The translated strings of a legend for a species, in the game's language. */
    static TraitLegendLayout.Strings strings(TraitLegendPage data) {
        Map<String, String> display = ClientDefinitions.get().species().stream()
                .filter(s -> s.id().equals(data.species())).findFirst()
                .map(SyncedDefinitions.Species::displayTraits).orElse(Map.of());
        return strings(data.species().getPath(), data.example(), display, TraitLegendView::tr);
    }

    /**
     * The strings of a legend with any translator (a key to its text): the game's, or a lang file in
     * JUnit, so the test checks exactly what the page draws (review m4).
     */
    public static TraitLegendLayout.Strings strings(String speciesPath, List<Integer> example,
                                                    Map<String, String> display,
                                                    java.util.function.UnaryOperator<String> tr) {
        List<String> names = new ArrayList<>();
        List<Integer> steps = new ArrayList<>();
        for (int i = 0; i < EXAMPLE_TRAITS.size() && i < example.size(); i++) {
            names.add(tr.apply(LensScale.nameKey(EXAMPLE_TRAITS.get(i), speciesPath, display)));
            steps.add(example.get(i));
        }
        String[][] words = new String[SCALES.size()][5];
        String[] scaleLines = new String[SCALES.size()];
        for (int s = 0; s < SCALES.size(); s++) {
            for (int step = 1; step <= 5; step++) {
                words[s][step - 1] = tr.apply(LensScale.wordKey(SCALES.get(s), step));
            }
            scaleLines[s] = tr.apply("wildroots.journal.guide.traits.scale").replace("%1$s", "%s").replace("%s",
                    tr.apply("wildroots.journal.guide.traits.scale." + SCALES.get(s)));
        }
        List<String> flags = new ArrayList<>();
        for (String k : FLAG_KEYS) {
            flags.add(tr.apply("wildroots.journal.guide.traits.flag." + k));
        }
        return new TraitLegendLayout.Strings(tr.apply("wildroots.journal.guide.traits.intro"),
                tr.apply("wildroots.journal.guide.traits.grade"), tr.apply("wildroots.journal.guide.traits.how_to"),
                tr.apply("wildroots.species." + speciesPath), names, steps, words, scaleLines,
                tr.apply("wildroots.journal.guide.traits.flags"), flags, tr.apply("wildroots.journal.ui.example"));
    }

    static List<LaidOutPage> layout(TraitLegendPage data, GuidePage page, PageContext ctx) {
        TraitLegendLayout.Strings s = strings(data);
        TextFit.Measure m = TextFit.of(ctx.font());
        List<LaidOutPage> out = new ArrayList<>();
        for (TraitLegendLayout.Page p : TraitLegendLayout.layout(s, m)) {
            out.add(LaidOutPage.half(rect -> new TraitLegendView(rect, p, s, m)));
        }
        return out;
    }

    private final PageRect rect;
    private final Font font;
    private final TraitLegendLayout.Page page;
    private final TextFit.Measure measure;
    private final String[][] words;
    private final String[] scaleLines;
    private final List<AbstractWidget> widgets = new ArrayList<>();
    /** The styled lines of every Text item, made once per page (review m5), not per frame. */
    private final java.util.Map<TraitLegendLayout.Text, List<net.minecraft.util.FormattedCharSequence>> styled =
            new java.util.IdentityHashMap<>();
    private int scale;
    /** Wrapped words of the five steps for the shown scale (beside a bar: one line; stacked: up to 2). */
    private final List<List<String>> stepLabels = new ArrayList<>();
    private ScaleButton scaleButton;

    TraitLegendView(PageRect rect, TraitLegendLayout.Page page, TraitLegendLayout.Strings strings,
                    TextFit.Measure measure) {
        this.rect = rect;
        this.font = Minecraft.getInstance().font;
        this.page = page;
        this.measure = measure;
        this.words = strings.words();
        this.scaleLines = strings.scaleLines();
        for (Object it : page.items()) {
            if (it instanceof TraitLegendLayout.Text t) {
                List<net.minecraft.util.FormattedCharSequence> lines = new ArrayList<>();
                for (String l : t.lines()) {
                    Component c = Component.literal(l);
                    switch (t.kind()) {
                        case BOLD -> c = ((net.minecraft.network.chat.MutableComponent) c).withStyle(ChatFormatting.BOLD);
                        case FADED -> c = ((net.minecraft.network.chat.MutableComponent) c).withStyle(ChatFormatting.ITALIC);
                        default -> { }
                    }
                    lines.add(c.getVisualOrderText());
                }
                styled.put(t, List.copyOf(lines));
            }
            if (it instanceof TraitLegendLayout.ScaleLine sl) {
                scaleButton = new ScaleButton(rect.x(), rect.y() + sl.y(), TraitLegendLayout.WIDTH, sl.h());
                widgets.add(scaleButton);
            }
        }
        updateWords();
    }

    @Override
    public PageRect rect() {
        return rect;
    }

    @Override
    public List<AbstractWidget> widgets() {
        return widgets;
    }

    void nextScale() {
        scale = (scale + 1) % SCALES.size();
        updateWords();
        if (scaleButton != null) {
            scaleButton.update();
        }
    }

    /** The words of the shown scale, fitted once per change of scale (not per frame). */
    private void updateWords() {
        stepLabels.clear();
        for (int step = 0; step < 5; step++) {
            stepLabels.add(TextFit.wrapMax(words[scale][step], TraitLegendLayout.WIDTH, 2, false, measure));
        }
    }

    /** A plain ink line (the words beside bars, the flag texts): no component, no style. */
    private void ink(GuiGraphics g, String text, int x, int y) {
        g.drawString(font, text, x, y, JournalColors.INK, false);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        int x0 = rect.x();
        int y0 = rect.y();
        for (Object it : page.items()) {
            if (it instanceof TraitLegendLayout.Text t) {
                int color = t.kind() == TraitLegendLayout.Kind.FADED ? JournalColors.INK_FADED : JournalColors.INK;
                List<net.minecraft.util.FormattedCharSequence> lines = styled.get(t);
                for (int i = 0; i < lines.size(); i++) {
                    g.drawString(font, lines.get(i), x0, y0 + t.y() + i * PageRect.LINE_HEIGHT, color, false);
                }
            } else if (it instanceof TraitLegendLayout.Bar b) {
                bar(g, b, x0, y0);
            } else if (it instanceof TraitLegendLayout.Flag f) {
                TraitBar.drawFlag(g, FLAGS[f.flag()], x0, y0 + f.y());
                for (int i = 0; i < f.lines().size(); i++) {
                    ink(g, f.lines().get(i), x0 + TraitLegendLayout.FLAG_TEXT_X,
                            y0 + f.y() + 1 + i * PageRect.LINE_HEIGHT);
                }
            }
        }
    }

    private void bar(GuiGraphics g, TraitLegendLayout.Bar b, int x0, int y0) {
        List<String> label = b.stepIndex() >= 0 ? stepLabels.get(b.stepIndex()) : b.label();
        if (b.stacked()) {
            for (int i = 0; i < label.size(); i++) {
                ink(g, label.get(i), x0, y0 + b.y() + i * PageRect.LINE_HEIGHT);
            }
            TraitBar.drawOnPaper(g, x0 + 1, y0 + b.barY(), b.step(), TraitBar.SEGMENTS, Component.empty());
        } else {
            TraitBar.drawOnPaper(g, x0 + 1, y0 + b.y(), b.step(), TraitBar.SEGMENTS,
                    Component.literal(label.isEmpty() ? "" : label.get(0)));
        }
    }

    /** "(words of the scale: size)": a click or Enter turns to the next scale; wraps to at most two lines. */
    final class ScaleButton extends AbstractButton {

        private List<String> shown = List.of();

        ScaleButton(int x, int y, int w, int h) {
            super(x, y, w, h, Component.empty());
            update();
        }

        void update() {
            Component m = Component.literal(scaleLines[scale]);
            setMessage(m);
            setTooltip(Tooltip.create(m));
            shown = TextFit.wrapMax(scaleLines[scale], width, 2, false, measure);
        }

        @Override
        public void onPress() {
            nextScale();
        }

        @Override
        protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
            for (int i = 0; i < shown.size(); i++) {
                int y = getY() + i * PageRect.LINE_HEIGHT;
                g.drawString(font, shown.get(i), getX(), y, JournalColors.LINK, false);
                if (isHoveredOrFocused()) {
                    g.hLine(getX(), getX() + font.width(shown.get(i)) - 1, y + 8, JournalColors.LINK);
                }
            }
        }

        @Override
        protected void updateWidgetNarration(NarrationElementOutput out) {
            defaultButtonNarrationText(out);
        }
    }
}
