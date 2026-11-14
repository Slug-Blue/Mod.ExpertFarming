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
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * {@code trait_legend} over a spread (journal v2 §3.4; WR-181d): how to
 * read the lens with the same bar as the lens panel ({@link TraitBar}).
 * Left: the five steps with the words of one scale (a click on the scale
 * line turns through the 8 scales of narrative §9.2), the produce grade,
 * how to use the lens. Right: an example of the species' traits, named
 * as the species names them ({@code displayTraits}), the flag marks, and
 * "an example, not your plant".
 */
@OnlyIn(Dist.CLIENT)
public final class TraitLegendView implements PageView {

    /** The 8 scales in the order of narrative §9.2. */
    public static final List<String> SCALES = List.of("size", "yield", "growth", "flavor", "sweetness", "tenderness",
            "tolerance", "expression");
    /** The traits of the example, in the order of the lens panel. */
    static final List<String> EXAMPLE_TRAITS = List.of("fruit_size", "yield", "growth_speed", "flavor",
            "cold_tolerance", "heat_tolerance", "drought_tolerance");
    static final int COL = 144;
    static final int NAME_W = 46;

    static List<LaidOutPage> layout(TraitLegendPage data, GuidePage page, PageContext ctx) {
        return List.of(LaidOutPage.spread(rect -> new TraitLegendView(rect, data, ctx)));
    }

    private final PageRect rect;
    private final Font font;
    private final int leftX;
    private final int rightX;
    private final int top;
    private int scale;
    private final List<FormattedCharSequence> intro;
    private final List<FormattedCharSequence> grade;
    private final List<FormattedCharSequence> howTo;
    private final ScaleButton scaleButton;
    private final FormattedCharSequence speciesName;
    private final List<Row> rows = new ArrayList<>();
    private final FormattedCharSequence flagsTitle;
    private final List<FormattedCharSequence> example;
    private final List<AbstractWidget> widgets = new ArrayList<>();
    private final int stepsY;
    private final int gradeY;
    /** Measured once, and again when the scale changes: the five words, the flag labels. */
    private final List<Component> words = new ArrayList<>();
    private final List<String> flagLabels = new ArrayList<>();
    static final TraitBar.Flag[] FLAGS = {TraitBar.Flag.BITTER, TraitBar.Flag.POLLINATION_B, TraitBar.Flag.AILING,
        TraitBar.Flag.LIKE_CULTIVAR};
    static final String[] FLAG_KEYS = {"bitter", "pollination", "ailing", "looks_like"};

    /** One example row: name (cut), steps, scale. */
    record Row(FormattedCharSequence name, int step, String scale) {
    }

    TraitLegendView(PageRect rect, TraitLegendPage data, PageContext ctx) {
        this.rect = rect;
        this.font = ctx.font();
        this.leftX = rect.x() + 4;
        this.rightX = rect.x() + COL;
        this.top = rect.y() + 2;
        int w = PageRect.HALF_WIDTH;
        this.intro = font.split(Component.translatable("wildroots.journal.guide.traits.intro"), w);
        this.stepsY = top + intro.size() * PageRect.LINE_HEIGHT + 3;
        int scaleY = stepsY + 5 * TraitBar.ROW_HEIGHT + 1;
        this.scaleButton = new ScaleButton(leftX, scaleY, w);
        widgets.add(scaleButton);
        this.gradeY = scaleY + PageRect.LINE_HEIGHT + 4;
        this.grade = font.split(Component.translatable("wildroots.journal.guide.traits.grade"), w);
        this.howTo = font.split(Component.translatable("wildroots.journal.guide.traits.how_to"), w);

        String speciesPath = data.species().getPath();
        Map<String, String> display = ClientDefinitions.get().species().stream()
                .filter(s -> s.id().equals(data.species())).findFirst()
                .map(SyncedDefinitions.Species::displayTraits).orElse(Map.of());
        this.speciesName = TextLayout.heading(Component.translatable("wildroots.species." + speciesPath))
                .getVisualOrderText();
        for (int i = 0; i < EXAMPLE_TRAITS.size() && i < data.example().size(); i++) {
            String trait = EXAMPLE_TRAITS.get(i);
            Component name = Component.translatable(LensScale.nameKey(trait, speciesPath, display));
            String cut = font.plainSubstrByWidth(name.getString(), NAME_W);
            rows.add(new Row(Component.literal(cut).getVisualOrderText(), data.example().get(i),
                    LensScale.scale(trait, display)));
        }
        this.flagsTitle = TextLayout.heading(Component.translatable("wildroots.journal.guide.traits.flags"))
                .getVisualOrderText();
        for (String k : FLAG_KEYS) {
            flagLabels.add(font.plainSubstrByWidth(Component.translatable("wildroots.journal.guide.traits.flag." + k)
                    .getString(), 46));
        }
        updateWords();
        this.example = font.split(Component.translatable("wildroots.journal.ui.example")
                .withStyle(s -> s.withItalic(true)), w);
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
        scaleButton.update();
        updateWords();
    }

    private void updateWords() {
        words.clear();
        for (int step = 1; step <= 5; step++) {
            words.add(Component.translatable(LensScale.wordKey(SCALES.get(scale), step)));
        }
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        int y = top;
        for (FormattedCharSequence l : intro) {
            g.drawString(font, l, leftX, y, JournalColors.INK, false);
            y += PageRect.LINE_HEIGHT;
        }
        for (int step = 1; step <= 5; step++) {
            TraitBar.drawOnPaper(g, leftX + 1, stepsY + (step - 1) * TraitBar.ROW_HEIGHT, step, TraitBar.SEGMENTS,
                    words.get(step - 1));
        }
        y = gradeY;
        for (FormattedCharSequence l : grade) {
            g.drawString(font, l, leftX, y, JournalColors.INK, false);
            y += PageRect.LINE_HEIGHT;
        }
        y += 3;
        g.hLine(leftX, leftX + PageRect.HALF_WIDTH - 1, y - 2, JournalColors.INK_FADED);
        for (FormattedCharSequence l : howTo) {
            if (y + PageRect.LINE_HEIGHT > rect.bottom()) {
                break;
            }
            g.drawString(font, l, leftX, y, JournalColors.INK, false);
            y += PageRect.LINE_HEIGHT;
        }

        y = top;
        g.drawString(font, speciesName, rightX, y, JournalColors.INK, false);
        y += PageRect.LINE_HEIGHT + 3;
        for (Row r : rows) {
            g.drawString(font, r.name(), rightX, y, JournalColors.INK, false);
            TraitBar.drawOnPaper(g, rightX + NAME_W + 2, y - 1, r.step(), TraitBar.SEGMENTS, Component.empty());
            y += TraitBar.ROW_HEIGHT;
        }
        y += 2;
        g.hLine(rightX, rightX + PageRect.HALF_WIDTH - 1, y, JournalColors.INK_FADED);
        y += 3;
        g.drawString(font, flagsTitle, rightX, y, JournalColors.INK, false);
        y += PageRect.LINE_HEIGHT + 1;
        for (int i = 0; i < FLAGS.length; i++) {
            int fx = rightX + (i % 2) * 58;
            int fy = y + (i / 2) * 11;
            TraitBar.drawFlag(g, FLAGS[i], fx, fy);
            g.drawString(font, flagLabels.get(i), fx + 11, fy + 1, JournalColors.INK, false);
        }
        y += 24;
        for (FormattedCharSequence l : example) {
            if (y + PageRect.LINE_HEIGHT > rect.bottom()) {
                break;
            }
            g.drawString(font, l, rightX, y, JournalColors.INK_FADED, false);
            y += PageRect.LINE_HEIGHT;
        }
    }

    /** "(words of the scale: size)": a click or Enter turns to the next scale. */
    final class ScaleButton extends AbstractButton {

        private FormattedCharSequence shown = FormattedCharSequence.EMPTY;
        private int shownWidth;

        ScaleButton(int x, int y, int w) {
            super(x, y, w, PageRect.LINE_HEIGHT, Component.empty());
            update();
        }

        void update() {
            Component m = Component.translatable("wildroots.journal.guide.traits.scale",
                    Component.translatable("wildroots.journal.guide.traits.scale." + SCALES.get(scale)));
            setMessage(m);
            setTooltip(Tooltip.create(m));
            String cut = font.plainSubstrByWidth(m.getString(), width);
            shown = Component.literal(cut).getVisualOrderText();
            shownWidth = font.width(cut);
        }

        @Override
        public void onPress() {
            nextScale();
        }

        @Override
        protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
            g.drawString(font, shown, getX(), getY(), JournalColors.LINK, false);
            if (isHoveredOrFocused()) {
                g.hLine(getX(), getX() + shownWidth - 1, getY() + 8, JournalColors.LINK);
            }
        }

        @Override
        protected void updateWidgetNarration(NarrationElementOutput out) {
            defaultButtonNarrationText(out);
        }
    }
}
