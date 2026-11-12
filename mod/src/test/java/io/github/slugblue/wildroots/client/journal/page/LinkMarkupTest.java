package io.github.slugblue.wildroots.client.journal.page;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.github.slugblue.wildroots.client.journal.page.LinkMarkup.Segment;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.function.Predicate;
import org.junit.jupiter.api.Test;

/**
 * WR-181c: link markup of journal v2 §2.4. An open entry becomes a link,
 * a closed or unknown one plain text; anything malformed stays literal;
 * nothing is lost.
 */
class LinkMarkupTest {

    static final Predicate<String> OPEN = Set.of("guide.cycle", "guide.stages")::contains;

    static Segment plain(String s) {
        return new Segment(s, Optional.empty(), false);
    }

    static Segment link(String text, String id) {
        return new Segment(text, Optional.of(id), false);
    }

    @Test
    void openIdsBecomeLinks() {
        assertEquals(List.of(plain("See the "), link("biennial cycle", "guide.cycle"), plain(".")),
                LinkMarkup.parse("See the [[guide.cycle|biennial cycle]].", OPEN));
        assertEquals(List.of(link("a", "guide.cycle"), link("b", "guide.stages")),
                LinkMarkup.parse("[[guide.cycle|a]][[guide.stages|b]]", OPEN));
    }

    @Test
    void closedOrUnknownIdsArePlainText() {
        assertEquals(List.of(plain("See the lens, now.")), LinkMarkup.parse("See [[guide.lens|the lens]], now.", OPEN),
                "closed: the shown text, no link, merged with the text around");
        assertEquals(List.of(plain("x unknown")), LinkMarkup.parse("x [[guide.nope|unknown]]", OPEN));
    }

    @Test
    void anIdWithoutTextShowsTheTitle() {
        assertEquals(List.of(plain("Go "), new Segment("guide.cycle", Optional.of("guide.cycle"), true)),
                LinkMarkup.parse("Go [[guide.cycle]]", OPEN));
        assertEquals(List.of(new Segment("guide.lens", Optional.empty(), true)), LinkMarkup.parse("[[guide.lens]]", OPEN),
                "closed: the title as plain text");
        assertEquals("Go Cycle", LinkMarkup.plainText("Go [[guide.cycle]]", id -> "Cycle"));
    }

    @Test
    void malformedMarkupStaysLiteral() {
        for (String s : List.of("[[", "]]", "a [[guide.cycle|b", "[[Guide.Cycle|x]]", "[[guide.cycle|]]",
                "[[guide.cycle|a|b]]", "[[ guide.cycle|x]]", "[[|x]]", "plain [ [x] ]")) {
            assertEquals(s, LinkMarkup.plainText(s, id -> "T"), s);
        }
        assertEquals(List.of(plain("[[a "), link("b", "guide.cycle"), plain("]]")),
                LinkMarkup.parse("[[a [[guide.cycle|b]]]]", OPEN), "no nesting");
    }

    @Test
    void randomTextNeverThrowsAndKeepsPlainText() {
        Random r = new Random(1812);
        String alphabet = "ab [|]].guide_cycl";
        for (int i = 0; i < 5000; i++) {
            StringBuilder b = new StringBuilder();
            int n = r.nextInt(40);
            for (int k = 0; k < n; k++) {
                b.append(alphabet.charAt(r.nextInt(alphabet.length())));
            }
            String s = b.toString();
            List<Segment> segs = LinkMarkup.parse(s, OPEN);
            if (!s.contains("[[")) {
                assertEquals(s.isEmpty() ? List.of() : List.of(plain(s)), segs, s);
            }
        }
    }
}
