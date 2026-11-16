package io.github.slugblue.wildroots.client.journal.page;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Owner playtest 3 (12.png): the trait legend page. Over the real ru and
 * en strings, with a fake font ({@link FakeFont}): every text, bar word and
 * flag fits the page width, every page its height, nothing is cut.
 */
class TraitLegendLayoutTest {

    static final TextFit.Measure M = FakeFont.INSTANCE;
    static final List<String> LANGS = LangFixture.LANGS;

    static Map<String, String> lang(String name) throws IOException {
        return LangFixture.lang(name);
    }

    static void report(List<String> v) {
        LangFixture.report(v);
    }

    static TraitLegendLayout.Strings legend(Map<String, String> l, String speciesPath) {
        List<String> names = new ArrayList<>();
        List<Integer> steps = new ArrayList<>();
        String[] traits = {"fruit_size", "yield", "growth_speed", "flavor", "cold_tolerance", "heat_tolerance",
            "drought_tolerance"};
        for (int i = 0; i < traits.length; i++) {
            String own = "wildroots.tooltip.trait_name." + traits[i] + "." + speciesPath;
            names.add(l.getOrDefault(own, l.get("wildroots.tooltip.trait_name." + traits[i])));
            steps.add(1 + i % 5);
        }
        String[] scales = {"size", "yield", "growth", "flavor", "sweetness", "tenderness", "tolerance", "expression"};
        String[][] words = new String[8][5];
        String[] scaleLines = new String[8];
        for (int s = 0; s < 8; s++) {
            for (int step = 1; step <= 5; step++) {
                words[s][step - 1] = l.get("wildroots.tooltip.scale." + scales[s] + "." + step);
            }
            scaleLines[s] = l.get("wildroots.journal.guide.traits.scale").replace("%s",
                    l.get("wildroots.journal.guide.traits.scale." + scales[s]));
        }
        String[] flags = {"bitter", "pollination", "ailing", "looks_like"};
        List<String> flagLabels = new ArrayList<>();
        for (String f : flags) {
            flagLabels.add(l.get("wildroots.journal.guide.traits.flag." + f));
        }
        return new TraitLegendLayout.Strings(l.get("wildroots.journal.guide.traits.intro"),
                l.get("wildroots.journal.guide.traits.grade"), l.get("wildroots.journal.guide.traits.how_to"),
                l.get("wildroots.species." + speciesPath), names, steps, words, scaleLines,
                l.get("wildroots.journal.guide.traits.flags"), flagLabels, l.get("wildroots.journal.ui.example"));
    }

    @Test
    void theTraitLegendFitsItsPagesInBothLanguages() throws IOException {
        List<String> v = new ArrayList<>();
        for (String lang : LANGS) {
            Map<String, String> l = lang(lang);
            for (String species : List.of("wild_carrot", "eastern_carrot")) {
                if (!l.containsKey("wildroots.species." + species)) {
                    continue;
                }
                List<TraitLegendLayout.Page> pages = TraitLegendLayout.layout(legend(l, species), M);
                assertTrue(pages.size() >= 1 && pages.size() <= 4, lang + " pages " + pages.size());
                for (int p = 0; p < pages.size(); p++) {
                    checkPage(v, lang + " " + species + " page " + (p + 1), pages.get(p));
                }
            }
        }
        report(v);
    }

    static void checkPage(List<String> v, String where, TraitLegendLayout.Page page) {
        if (page.height() > TraitLegendLayout.HEIGHT) {
            v.add(where + ": height " + page.height() + " > " + TraitLegendLayout.HEIGHT);
        }
        for (Object it : page.items()) {
            if (it instanceof TraitLegendLayout.Text t) {
                boolean bold = t.kind() == TraitLegendLayout.Kind.BOLD;
                for (String line : t.lines()) {
                    if (M.width(line, bold) > TraitLegendLayout.WIDTH) {
                        v.add(where + ": text \"" + line + "\" is " + M.width(line, bold) + " px");
                    }
                }
                if (t.y() + t.lines().size() * TraitLegendLayout.LINE > page.height()) {
                    v.add(where + ": text runs below its page");
                }
            } else if (it instanceof TraitLegendLayout.Bar b) {
                int right = 0;
                for (String line : b.label()) {
                    right = Math.max(right, M.width(line, false));
                }
                int limit = b.stacked() ? TraitLegendLayout.WIDTH : TraitLegendLayout.SIDE_WIDTH;
                if (right > limit) {
                    v.add(where + ": bar label " + b.label() + " is " + right + " px, limit " + limit);
                }
            } else if (it instanceof TraitLegendLayout.Flag f) {
                for (String line : f.lines()) {
                    if (M.width(line, false) > TraitLegendLayout.WIDTH - TraitLegendLayout.FLAG_TEXT_X) {
                        v.add(where + ": flag text \"" + line + "\" is " + M.width(line, false) + " px");
                    }
                }
            }
        }
    }

    @Test
    void everyWordOfEveryScaleFitsBesideItsBarOrAboveIt() throws IOException {
        for (String lang : LANGS) {
            TraitLegendLayout.Strings s = legend(lang(lang), "wild_carrot");
            for (String[] scale : s.words()) {
                for (String word : scale) {
                    List<String> two = TextFit.wrapMax(word, TraitLegendLayout.WIDTH, 2, false, M);
                    assertEquals(word, String.join(" ", two).replace("…", word.equals(String.join(" ", two)) ? "" : "…"),
                            lang + ": \"" + word + "\" is cut");
                }
            }
        }
    }

    @Test
    void theRussianLegendNeedsMorePagesThanTheEnglishOne() throws IOException {
        int en = TraitLegendLayout.layout(legend(lang("en_us"), "wild_carrot"), M).size();
        int ru = TraitLegendLayout.layout(legend(lang("ru_ru"), "wild_carrot"), M).size();
        assertTrue(ru >= en, "ru " + ru + " en " + en);
    }
}
