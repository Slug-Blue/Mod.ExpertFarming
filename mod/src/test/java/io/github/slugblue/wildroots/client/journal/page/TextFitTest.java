package io.github.slugblue.wildroots.client.journal.page;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;

/** Owner playtest 3: the text fitting helpers never produce a line wider than the page. */
class TextFitTest {

    static final TextFit.Measure M = FakeFont.INSTANCE;

    @Test
    void wrapKeepsWordsAndBreaksTheOnesThatDoNotFit() {
        assertEquals(List.of("Пять делений —", "пять слов."), TextFit.wrap("Пять делений — пять слов.", 90, false, M));
        List<String> broken = TextFit.wrap("Засухоустойчивость", 60, false, M);
        assertTrue(broken.size() > 1);
        assertEquals("Засухоустойчивость", String.join("", broken), "nothing lost");
        broken.forEach(l -> assertTrue(M.width(l, false) <= 60, l));
    }

    @Test
    void ellipsizeAndWrapMaxNeverExceedTheWidth() {
        assertEquals("Горчит", TextFit.ellipsize("Горчит", 100, false, M));
        String cut = TextFit.ellipsize("Засухоустойчивость растения", 60, false, M);
        assertTrue(cut.endsWith("…") && M.width(cut, false) <= 60, cut);
        List<String> two = TextFit.wrapMax("Значки рядом со шкалами и ещё много слов подряд без конца", 116, 2, true, M);
        assertEquals(2, two.size());
        two.forEach(l -> assertTrue(M.width(l, true) <= 116, l));
        assertTrue(two.get(1).endsWith("…"));
    }

    @Test
    void randomTextNeverOverflows() {
        Random r = new Random(3);
        String alphabet = "абвгд ежзийкл mnop qrs.,-—ЖШ";
        for (int i = 0; i < 3000; i++) {
            StringBuilder b = new StringBuilder();
            for (int k = r.nextInt(80); k > 0; k--) {
                b.append(alphabet.charAt(r.nextInt(alphabet.length())));
            }
            int width = 20 + r.nextInt(120);
            boolean bold = r.nextBoolean();
            for (String l : TextFit.wrap(b.toString(), width, bold, M)) {
                assertTrue(M.width(l, bold) <= width || l.length() == 1, "'" + l + "' in " + width);
            }
            for (String l : TextFit.wrapMax(b.toString(), width, 2, bold, M)) {
                assertTrue(M.width(l, bold) <= width || l.length() <= 2, "'" + l + "' in " + width);
            }
        }
    }
}
