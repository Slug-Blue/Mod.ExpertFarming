package io.github.slugblue.wildroots.client.journal.page;

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

    /** The display_traits of a species, read from the shipped species file (what the client gets synced). */
    static Map<String, String> display(String species) throws IOException {
        java.nio.file.Path f = LangFixture.modDir().resolve(
                "src/main/resources/resourcepacks/lines/carrot/data/wildroots/crop_species/" + species + ".json");
        Map<String, String> out = new java.util.HashMap<>();
        if (java.nio.file.Files.exists(f)) {
            var o = com.google.gson.JsonParser.parseString(java.nio.file.Files.readString(f,
                    java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
            if (o.has("display_traits")) {
                o.getAsJsonObject("display_traits").entrySet().forEach(e -> out.put(e.getKey(),
                        e.getValue().getAsString()));
            }
        }
        return out;
    }

    /** The legend strings exactly as the page builds them ({@link TraitLegendView#strings}), over a lang file. */
    static TraitLegendLayout.Strings legend(Map<String, String> l, String speciesPath) throws IOException {
        List<Integer> example = List.of(4, 3, 2, 3, 3, 2, 2);
        return TraitLegendView.strings(speciesPath, example, display(speciesPath), key -> {
            String v = l.get(key);
            assertTrue(v != null, "the legend uses a key that the lang file lacks: " + key);
            return v;
        });
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
                    // a word is cut (ends with an ellipsis) only if it needs more than the two lines allowed
                    int lines = TextFit.wrap(word, TraitLegendLayout.WIDTH, false, M).size();
                    assertTrue(lines <= 2, lang + ": \"" + word + "\" needs " + lines + " lines, so it would be cut");
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
