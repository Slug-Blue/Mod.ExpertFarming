package io.github.slugblue.wildroots.client.journal.page;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Predicate;
import org.junit.jupiter.api.Test;

/**
 * Owner playtest 3 (12.png): text left its page. A generic checker over
 * every real ru and en string the journal pages show, with a fake font
 * (conservative, see {@link FakeFont}): for each slot a page draws a
 * string in, the string, after the wrapping the renderer does, must fit
 * the slot - and the layouts must fit their page. A slot that cuts text
 * with an ellipsis is checked as "must not need one" where the text is
 * static (it comes from lang), and is only wrapped or cut with a tooltip
 * where the text is the player's data (record and entry names).
 */
class JournalTextFitTest {

    static final TextFit.Measure M = FakeFont.INSTANCE;
    static final int HALF = PageRect.HALF_WIDTH;
    static final int SPREAD = PageRect.SPREAD_WIDTH;

    static final List<String> LANGS = LangFixture.LANGS;

    static Map<String, String> lang(String n) throws IOException {
        return LangFixture.lang(n);
    }

    static void report(List<String> v) {
        LangFixture.report(v);
    }

    static String plain(String s) {
        return LangFixture.plain(s);
    }

    static List<String> keys(Map<String, String> l, Predicate<String> p) {
        return l.keySet().stream().filter(p).toList();
    }

    // ------------------------------------------------------------------ the checks

    /** The longest word of a wrapped text must fit (the wrap cannot do more than break it). */
    static void wordsFit(List<String> v, String lang, String key, String text, int width, boolean bold) {
        for (String word : plain(text).strip().split("\\s+")) {
            if (M.width(word, bold) > width) {
                v.add(lang + " " + key + ": the word \"" + word + "\" is " + M.width(word, bold) + " px, slot " + width);
            }
        }
    }

    /** A single line (no wrap) that must fit as it is. */
    static void oneLine(List<String> v, String lang, String slot, String text, int width, boolean bold) {
        int w = M.width(plain(text), bold);
        if (w > width) {
            v.add(lang + " " + slot + ": \"" + text + "\" is " + w + " px, slot " + width);
        }
    }

    /** A one-line title that may shrink to 70 % before it needs an ellipsis (header, list rows). */
    static void shrinks(List<String> v, String lang, String slot, String text, int width) {
        int w = M.width(plain(text), false);
        if (TextFit.shrinkScale(w, width, TextFit.MIN_SHRINK) * w > width) {
            v.add(lang + " " + slot + ": \"" + text + "\" is " + w + " px, slot " + width + " even at 70 %");
        }
    }

    /** Wrapped to at most {@code lines} lines without an ellipsis. */
    static void inLines(List<String> v, String lang, String slot, String text, int width, int lines, boolean bold) {
        List<String> l = TextFit.wrap(plain(text), width, bold, M);
        if (l.size() > lines) {
            v.add(lang + " " + slot + ": \"" + text + "\" needs " + l.size() + " lines of " + width + " px, slot has "
                    + lines);
        }
    }

    @Test
    void guideTextsWrapWithinThePage() throws IOException {
        List<String> v = new ArrayList<>();
        for (String lang : LANGS) {
            Map<String, String> l = lang(lang);
            // guide pages, hints, tools, species and cultivar texts: wrapped over half pages
            for (String k : keys(l, k -> k.startsWith("wildroots.journal.guide.") && !k.endsWith(".title")
                    || k.startsWith("wildroots.journal.hint.") && !k.endsWith(".title")
                    || k.startsWith("wildroots.journal.tool.") || k.startsWith("wildroots.journal.species.")
                    || k.startsWith("wildroots.journal.note.") && !k.endsWith(".title")
                    || k.startsWith("wildroots.journal.ui.section."))) {
                wordsFit(v, lang, k, l.get(k), HALF, false);
            }
            // headings (bold, wrapped): titles of hints and notes
            for (String k : keys(l, k -> k.startsWith("wildroots.journal.hint.") && k.endsWith(".title")
                    || k.startsWith("wildroots.journal.note.") && k.endsWith(".title")
                    || k.startsWith("wildroots.journal.ui.section."))) {
                wordsFit(v, lang, k, l.get(k), HALF - 14, true);
            }
        }
        report(v);
    }

    @Test
    void staticSingleLineSlotsFit() throws IOException {
        List<String> v = new ArrayList<>();
        for (String lang : LANGS) {
            Map<String, String> l = lang(lang);
            // header title of a guide entry or category: regular, left of the star, ellipsized when longer
            for (String k : keys(l, k -> k.startsWith("wildroots.journal.guide.") && k.endsWith(".title")
                    || k.startsWith("wildroots.journal.category.") || k.startsWith("wildroots.journal.tab."))) {
                shrinks(v, lang, k + " (header)", l.get(k), HALF - 28);
            }
            // rows of the contents and of category lists: icon 15 px, new dot 6 px
            for (String k : keys(l, k -> k.startsWith("wildroots.journal.guide.") && k.endsWith(".title")
                    || k.startsWith("wildroots.journal.category.") || k.startsWith("wildroots.journal.tab."))) {
                shrinks(v, lang, k + " (row)", l.get(k), HALF - 15 - 6 - 4);
            }
            // contents headings (bold), counters
            for (String k : List.of("wildroots.journal.group.guide", "wildroots.journal.group.records",
                    "wildroots.journal.ui.new_list", "wildroots.journal.ui.pinned")) {
                oneLine(v, lang, k + " (heading)", l.get(k), HALF, true);
            }
            for (String k : List.of("wildroots.journal.ui.found_count", "wildroots.journal.ui.collected_count",
                    "wildroots.journal.ui.count_of", "wildroots.journal.ui.page")) {
                oneLine(v, lang, k + " (counter)", l.get(k), HALF, false);
            }
            // "already owned" badge: a check mark, a space and the text
            oneLine(v, lang, "ui.owned", "✓ " + l.get("wildroots.journal.ui.owned"), HALF - 4, false);
            // the see-also heading, the search hint and results line
            oneLine(v, lang, "ui.see_also", l.get("wildroots.journal.ui.see_also"), HALF, false);
            wordsFit(v, lang, "ui.search.none (wrapped note)", l.get("wildroots.journal.ui.search.none"), HALF, false);
            // the species and cultivar names of a record page are centred under the picture: two lines allowed
            for (String k : keys(l, k -> k.startsWith("wildroots.species.") && !k.contains(".history")
                    && !k.contains(".seen") || k.startsWith("wildroots.cultivar.") && !k.endsWith(".note")
                    && !k.endsWith(".goal"))) {
                inLines(v, lang, k + " (record name)", l.get(k), HALF, 2, false);
            }
            // names of items shown large: two lines
            for (String k : keys(l, k -> k.startsWith("item.wildroots.") && !k.endsWith(".desc")
                    && !k.contains(".desc.") && !k.endsWith(".hint") && !k.contains(".desc"))) {
                inLines(v, lang, k + " (spotlight name)", l.get(k), HALF, 2, false);
            }
        }
        report(v);
    }

    /**
     * Captions that need more than the {@value PageRect#CAPTION_LINES} lines under the window even over the
     * whole spread (shown cut, with a tooltip). Empty: every caption fits; the test below keeps the list
     * honest if one is ever added.
     */
    static final java.util.Set<String> TOO_LONG_CAPTIONS = java.util.Set.of();

    @Test
    void theListOfTooLongCaptionsHoldsOnlyTooLongOnes() throws IOException {
        for (String entry : TOO_LONG_CAPTIONS) {
            String lang = entry.substring(0, entry.indexOf(' '));
            String key = entry.substring(entry.indexOf(' ') + 1);
            String step = plain(lang(lang).get("wildroots.journal.ui.step"));
            assertTrue(TextFit.wrap(step + ". " + plain(lang(lang).get(key)), SPREAD, false, M).size()
                    > PageRect.CAPTION_LINES, entry + " now fits: take it off the list");
        }
    }

    @Test
    void sceneTextsFitTheirSlots() throws IOException {
        List<String> v = new ArrayList<>();
        for (String lang : LANGS) {
            Map<String, String> l = lang(lang);
            String step = plain(l.get("wildroots.journal.ui.step").replace("%1$s", "10").replace("%2$s", "16"));
            // the caption under the scene window: "Step n of m. text", two lines over the spread
            for (String k : keys(l, k -> k.startsWith("wildroots.journal.scene.")
                    && k.matches("wildroots\\.journal\\.scene\\.[a-z_]+\\.[0-9](\\.brush)?"))) {
                if (!TOO_LONG_CAPTIONS.contains(lang + " " + k)) {
                    inLines(v, lang, k + " (caption)", step + ". " + l.get(k), SPREAD, PageRect.CAPTION_LINES, false);
                }
            }
            // callouts: one line in a 13 px box, at most 150 px so that four fit in the window
            for (String k : keys(l, k -> k.startsWith("wildroots.tooltip.stage.")
                    || k.startsWith("wildroots.journal.scene.") && k.contains(".label."))) {
                oneLine(v, lang, k + " (callout)", l.get(k), 150, false);
            }
            // the restart word beside its button, the controls hint
            oneLine(v, lang, "ui.scene.restart", l.get("wildroots.journal.ui.scene.restart"), 90, false);
        }
        report(v);
    }
}
