package io.github.slugblue.wildroots.lens;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.slugblue.wildroots.client.ui.TraitBar;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import org.junit.jupiter.api.Test;

/**
 * Playtest 2, panel overflow (owner screenshots 13.png, 14.png): lines such
 * as "Засухоустойчивость: очень низкая" ran past the paper card. The card's
 * width came from a fixed cap that the unwrapped bar labels did not respect.
 * Now {@link LensSheetLayout} wraps every text inside the card. Here with a
 * fake font (7 px per character, wider than the real cyrillic glyphs) and
 * the real ru_ru and en_us strings of every trait, scale word, flag, note
 * and header line, at the widths a screen can give, 320 px wide included.
 */
class LensSheetLayoutTest {

    // ------------------------------------------------------------------ lang and fake font

    static Map<String, String> lang(String name) {
        Path dir = Path.of(System.getProperty("user.dir")).toAbsolutePath();
        for (Path q = dir; q != null; q = q.getParent()) {
            Path f = q.resolve("src/main/resources/assets/wildroots/lang/" + name + ".json");
            Path g = q.resolve("mod/src/main/resources/assets/wildroots/lang/" + name + ".json");
            for (Path c : new Path[] {f, g}) {
                if (Files.isRegularFile(c)) {
                    try {
                        JsonObject o = JsonParser.parseString(Files.readString(c, StandardCharsets.UTF_8))
                                .getAsJsonObject();
                        Map<String, String> m = new TreeMap<>();
                        o.entrySet().forEach(e -> m.put(e.getKey(), e.getValue().getAsString()));
                        return m;
                    } catch (IOException e) {
                        throw new IllegalStateException(e);
                    }
                }
            }
        }
        throw new IllegalStateException("no lang file " + name);
    }

    static final Pattern FORMAT = Pattern.compile("%(?:(\\d+)\\$)?s|%%");

    /** The text of a component with the lang strings of {@code lang} (a missing key shows as itself). */
    static String resolve(Map<String, String> lang, Component c) {
        StringBuilder out = new StringBuilder();
        if (c.getContents() instanceof TranslatableContents t) {
            String fmt = lang.getOrDefault(t.getKey(), t.getKey());
            Matcher m = FORMAT.matcher(fmt);
            int next = 0;
            int last = 0;
            while (m.find()) {
                out.append(fmt, last, m.start());
                if (m.group().equals("%%")) {
                    out.append('%');
                } else {
                    int i = m.group(1) != null ? Integer.parseInt(m.group(1)) - 1 : next++;
                    Object a = i < t.getArgs().length ? t.getArgs()[i] : "";
                    out.append(a instanceof Component ac ? resolve(lang, ac) : String.valueOf(a));
                }
                last = m.end();
            }
            out.append(fmt.substring(last));
        } else {
            out.append(c.getContents() instanceof net.minecraft.network.chat.contents.LiteralContents l ? l.text() : "");
        }
        c.getSiblings().forEach(s -> out.append(resolve(lang, s)));
        return out.toString();
    }

    /** A fake font: {@code px} per code point, word wrap at spaces, a word longer than the line is broken. */
    record Fake(Map<String, String> lang, int px) implements LensSheetLayout.Measure {

        int w(String s) {
            return s.codePointCount(0, s.length()) * px;
        }

        @Override
        public int width(Component c) {
            return w(resolve(lang, c));
        }

        @Override
        public List<Integer> wrap(Component c, int maxWidth) {
            int perLine = Math.max(1, maxWidth / px);
            List<Integer> out = new ArrayList<>();
            StringBuilder line = new StringBuilder();
            for (String word : resolve(lang, c).split(" ", -1)) {
                while (word.codePointCount(0, word.length()) > perLine) {
                    if (line.length() > 0) {
                        out.add(w(line.toString()));
                        line.setLength(0);
                    }
                    int cut = word.offsetByCodePoints(0, perLine);
                    out.add(w(word.substring(0, cut)));
                    word = word.substring(cut);
                }
                int need = line.length() == 0 ? word.length() : line.codePointCount(0, line.length()) + 1
                        + word.codePointCount(0, word.length());
                if (line.length() > 0 && need > perLine) {
                    out.add(w(line.toString()));
                    line.setLength(0);
                }
                if (line.length() > 0) {
                    line.append(' ');
                }
                line.append(word);
            }
            out.add(w(line.toString()));
            return out;
        }
    }

    // ------------------------------------------------------------------ sheets from the real keys

    static List<String> keys(Map<String, String> lang, String prefix) {
        return lang.keySet().stream().filter(k -> k.startsWith(prefix)).toList();
    }

    static Component tr(String key, Object... args) {
        return Component.translatable(key, args);
    }

    /** Every bar label that can occur: each trait name with each scale word. */
    static List<LensSheet.Bar> allBars(Map<String, String> lang) {
        List<LensSheet.Bar> out = new ArrayList<>();
        for (String name : keys(lang, "wildroots.tooltip.trait_name.")) {
            for (String word : keys(lang, "wildroots.tooltip.scale.")) {
                out.add(new LensSheet.Bar(tr(LensReading.TRAIT, tr(name), tr(word)), 3, 5, 2, 4));
            }
        }
        return out;
    }

    /** A card with every flag, note, header line and a long title, and the given bars. */
    static LensSheet kitchenSink(Map<String, String> lang, List<LensSheet.Bar> bars) {
        List<LensSheet.Flag> flags = new ArrayList<>();
        flags.add(new LensSheet.Flag(LensSheet.Icon.BITTER, tr("wildroots.tooltip.qual_state.bitterness.wild")));
        flags.add(new LensSheet.Flag(LensSheet.Icon.NONE, tr("wildroots.tooltip.qual_state.bitterness.domestic")));
        for (String p : List.of("bees", "hand", "neighbours", "self")) {
            flags.add(new LensSheet.Flag(LensSheet.Icon.POLLINATION_B, tr("wildroots.tooltip.pollination." + p)));
        }
        flags.add(new LensSheet.Flag(LensSheet.Icon.AILING, tr(LensReading.AILING)));
        for (String cultivar : keys(lang, "wildroots.cultivar.")) {
            if (cultivar.indexOf('.', "wildroots.cultivar.".length()) < 0) {
                flags.add(new LensSheet.Flag(LensSheet.Icon.LIKE_CULTIVAR, tr(LensReading.LOOKS_LIKE, tr(cultivar))));
            }
        }
        List<Component> notes = new ArrayList<>();
        notes.add(tr(LensSheet.VARY));
        notes.add(tr(LensPanel.CLICK));
        notes.add(tr(LensPanel.SHIFT));
        notes.add(tr(LensPanel.ROOT_VARY));
        notes.add(tr(LensRow.MORE_KEY));
        notes.addAll(LensSheet.environment(EnvModifiers.ALL));
        List<Component> header = new ArrayList<>();
        header.add(tr("wildroots.tooltip.lot.generation", "12"));
        header.add(tr("wildroots.tooltip.lot.line_name", "Ж".repeat(50)));
        header.add(tr("wildroots.tooltip.lot.from_wild"));
        header.add(tr("wildroots.grade.bitter"));
        Component title = tr(Lens.HEADER, tr("wildroots.species.wild_carrot"), tr("wildroots.tooltip.stage.harvest_ready.wild_carrot"));
        return new LensSheet(title, header, LensSheet.Icon.POLLINATION_S, Optional.of(tr(LensSheet.MOTHER)), bars,
                flags, notes);
    }

    /** Widths a screen can give: the card caps of the item panel and the window at GUI scales 1 to 4 and at 320 px. */
    static final int[] CAPS = {60, 80, 100, 120, 150, 190, 240, 280};

    // ------------------------------------------------------------------ the invariants

    static void assertFits(LensSheetLayout.Layout lay, String where) {
        int bottom = 0;
        for (LensSheetLayout.Block b : lay.blocks()) {
            assertTrue(b.right() <= lay.width(), where + ": " + b.kind() + " " + b.text().getString() + " right "
                    + b.right() + " > card " + lay.width() + " " + b.lines());
            assertTrue(b.textX() >= 0 && b.textX() < lay.width(), where + ": text starts outside the card");
            assertTrue(b.y() >= bottom, where + ": blocks overlap at " + b.y());
            assertTrue(b.lines().size() * LensSheetLayout.LINE <= b.h(), where + ": block shorter than its lines");
            bottom = b.y() + b.h();
        }
        assertTrue(lay.height() >= bottom, where + ": the height cuts the last block (" + lay.height() + " < "
                + bottom + ")");
    }

    @Test
    void layoutConstantsAreTheJournalBars() {
        assertEquals(TraitBar.SEGMENT_WIDTH, LensSheetLayout.SEGMENT_WIDTH);
        assertEquals(TraitBar.GAP, LensSheetLayout.SEGMENT_GAP);
        assertEquals(TraitBar.LABEL_GAP, LensSheetLayout.LABEL_GAP);
        assertEquals(TraitBar.ROW_HEIGHT, LensSheetLayout.ROW_HEIGHT);
        for (int n = 1; n <= 8; n++) {
            assertEquals(TraitBar.barWidth(n), LensSheetLayout.barWidth(n));
        }
    }

    @Test
    void noTextPastTheCardForEveryLabelInRuAndEn() {
        int checked = 0;
        for (String l : List.of("ru_ru", "en_us")) {
            Map<String, String> lang = lang(l);
            List<LensSheet.Bar> all = allBars(lang);
            assertTrue(all.size() > 100, "bars from the lang file: " + all.size());
            for (int px : new int[] {6, 7}) {
                LensSheetLayout.Measure m = new Fake(lang, px);
                for (int cap : CAPS) {
                    // each label alone, and in groups of seven (a real card)
                    for (int i = 0; i < all.size(); i++) {
                        List<LensSheet.Bar> one = List.of(all.get(i));
                        LensSheet s = new LensSheet(Component.literal("T"), List.of(), LensSheet.Icon.NONE,
                                Optional.empty(), one, List.of(), List.of());
                        int w = LensSheetLayout.width(m, s, cap);
                        assertFits(LensSheetLayout.layout(m, s, w), l + " px " + px + " cap " + cap + " bar " + i);
                        checked++;
                    }
                    for (int i = 0; i + 7 <= all.size(); i += 7) {
                        LensSheet s = kitchenSink(lang, all.subList(i, i + 7));
                        int w = LensSheetLayout.width(m, s, cap);
                        assertTrue(w <= Math.max(cap, LensSheetLayout.minWidth(s)), l + ": card " + w + " over cap " + cap);
                        assertFits(LensSheetLayout.layout(m, s, w), l + " px " + px + " cap " + cap + " group " + i);
                        checked++;
                    }
                }
            }
        }
        assertTrue(checked > 10_000, "checked " + checked);
    }

    /** 14.png: at the cap the item panel used, "Засухоустойчивость: очень низкая" overflowed; it now wraps inside. */
    @Test
    void theOwnersOverflowIsWrappedInsideTheCard() {
        Map<String, String> ru = lang("ru_ru");
        String bar = resolve(ru, tr(LensReading.TRAIT, tr("wildroots.tooltip.trait_name.drought_tolerance"),
                tr("wildroots.tooltip.scale.tolerance.1")));
        assertEquals("Засухоустойчивость: очень низкая", bar);
        LensSheet.Bar b = new LensSheet.Bar(tr(LensReading.TRAIT, tr("wildroots.tooltip.trait_name.drought_tolerance"),
                tr("wildroots.tooltip.scale.tolerance.1")), 1, 5, 0, 0);
        LensSheet s = new LensSheet(Component.literal("T"), List.of(), LensSheet.Icon.NONE, Optional.empty(),
                List.of(b), List.of(), List.of());
        LensSheetLayout.Measure wide = new Fake(ru, 7);
        // 32 characters × 7 px plus the bar: far over the old 190 px cap
        assertTrue(LensSheetLayout.labelX(b) + wide.width(b.label()) > 190);
        int w = LensSheetLayout.width(wide, s, 190);
        assertEquals(190, w, "the card keeps to the cap");
        LensSheetLayout.Block bar0 = LensSheetLayout.layout(wide, s, w).blocks().get(1);
        assertEquals(LensSheetLayout.Kind.BAR, bar0.kind());
        assertTrue(bar0.lines().size() >= 2, "the label wraps");
        assertTrue(bar0.h() >= 2 * LensSheetLayout.LINE, "and the row is as tall as its lines");
        assertFits(LensSheetLayout.layout(wide, s, w), "owner case");
        // with room (the real glyphs are about 6 px) it stays one line and the card is as wide as the text
        LensSheetLayout.Measure real = new Fake(ru, 6);
        int w2 = LensSheetLayout.width(real, s, 240);
        assertEquals(LensSheetLayout.labelX(b) + real.width(b.label()), w2);
        assertEquals(1, LensSheetLayout.layout(real, s, w2).blocks().get(1).lines().size());
    }

    @Test
    void theCardIsAsWideAsItsWidestElementAndNoWider() {
        Map<String, String> en = lang("en_us");
        LensSheetLayout.Measure m = new Fake(en, 6);
        LensSheet s = kitchenSink(en, List.of(new LensSheet.Bar(Component.literal("x"), 2, 5, 0, 0)));
        int natural = 0;
        natural = Math.max(natural, LensSheetLayout.ICON + LensSheetLayout.ICON_GAP + m.width(s.title()));
        for (Component c : s.header()) {
            natural = Math.max(natural, m.width(c));
        }
        for (LensSheet.Flag f : s.flags()) {
            natural = Math.max(natural, LensSheetLayout.ICON + LensSheetLayout.ICON_GAP + m.width(f.text()));
        }
        for (Component c : s.notes()) {
            natural = Math.max(natural, m.width(c));
        }
        natural = Math.max(natural, m.width(s.barsTitle().get()));
        assertEquals(natural, LensSheetLayout.width(m, s, 10_000));
        assertEquals(200, LensSheetLayout.width(m, s, 200));
    }

    @Test
    void theHeightFollowsTheRealNumberOfLines() {
        Map<String, String> ru = lang("ru_ru");
        LensSheetLayout.Measure m = new Fake(ru, 7);
        LensSheet s = kitchenSink(ru, allBars(ru).subList(0, 7));
        int tall = LensSheetLayout.layout(m, s, 100).height();
        int wide = LensSheetLayout.layout(m, s, 280).height();
        assertTrue(tall > wide, "narrower card, more lines, taller: " + tall + " vs " + wide);
        // a bar row grows by one line height per extra line
        LensSheet.Bar b = new LensSheet.Bar(Component.literal("a b c d e f g h i j k l m n o p"), 1, 5, 0, 0);
        LensSheet one = new LensSheet(Component.literal("T"), List.of(), LensSheet.Icon.NONE, Optional.empty(),
                List.of(b), List.of(), List.of());
        LensSheetLayout.Layout lay = LensSheetLayout.layout(m, one, 100);
        LensSheetLayout.Block bar = lay.blocks().get(1);
        assertEquals(Math.max(LensSheetLayout.ROW_HEIGHT, bar.lines().size() * LensSheetLayout.LINE), bar.h());
        assertTrue(bar.lines().size() >= 2);
    }

    @Test
    void aNarrowScreenKeepsTheCardInsideIt() {
        // 320 px at GUI scale 1 of a 320 px window: the item panel's cap is 320 - 40; the lens window's is less
        for (String l : List.of("ru_ru", "en_us")) {
            Map<String, String> lang = lang(l);
            LensSheetLayout.Measure m = new Fake(lang, 7);
            LensSheet s = kitchenSink(lang, allBars(lang).subList(0, 7));
            int panelCap = Math.min(240, 320 - 40);
            int windowCap = Math.min(240, 320 - 2 * 16 - 2 * 8 - (84 + 8));
            for (int cap : new int[] {panelCap, windowCap}) {
                int w = LensSheetLayout.width(m, s, cap);
                assertTrue(w <= cap, l + ": card " + w + " over " + cap);
                assertFits(LensSheetLayout.layout(m, s, w), l + " 320 px cap " + cap);
            }
        }
    }

    @Test
    void anEmptySheetAndAHeaderOnlySheetHaveNoOverflow() {
        LensSheetLayout.Measure m = new Fake(lang("en_us"), 6);
        LensSheet empty = new LensSheet(Component.empty(), List.of(), LensSheet.Icon.NONE, Optional.empty(), List.of(),
                List.of(), List.of());
        LensSheetLayout.Layout lay = LensSheetLayout.layout(m, empty, LensSheetLayout.width(m, empty, 100));
        assertEquals(0, lay.height());
        assertFalse(lay.blocks().iterator().hasNext());
        LensSheet hint = new LensSheet(Component.literal("Wild carrot"), List.of(), LensSheet.Icon.NONE, Optional.empty(),
                List.of(), List.of(), List.of(tr(LensPanel.CLICK)));
        assertFits(LensSheetLayout.layout(m, hint, LensSheetLayout.width(m, hint, 80)), "hint");
    }
}
