package io.github.slugblue.wildroots.client.journal.page;

/**
 * A fake font for layout tests: per-character widths of about the default
 * Minecraft font (6 px for most Latin and Cyrillic letters, narrower for
 * "il.,:;!'|", 7 for wide Cyrillic letters, bold one more). The layouts
 * measure with the real font at run time; this stand-in only lets JUnit
 * check the layout logic and the rough fit over every real string. It is an
 * approximation, not a bound: the average (5.6 px a Russian character)
 * comes from the owner's screenshot, the per-letter values are not measured.
 * To check them, print {@code font.width(String.valueOf(c))} for the letters
 * in a client and compare with {@link #charWidth}.
 */
public final class FakeFont implements TextFit.Measure {

    public static final FakeFont INSTANCE = new FakeFont();

    public static int charWidth(char c) {
        if ("il.,:;!'|".indexOf(c) >= 0) {
            return 3;
        }
        if (" tI[]".indexOf(c) >= 0) {
            return 4;
        }
        if ("fk(){}<>\"*".indexOf(c) >= 0) {
            return 5;
        }
        if ("жшщюыЖШЩЮЫЪъ—–".indexOf(c) >= 0) {
            return 7;
        }
        return 6;
    }

    @Override
    public int width(String s, boolean bold) {
        int w = 0;
        for (int i = 0; i < s.length(); i++) {
            w += charWidth(s.charAt(i)) + (bold ? 1 : 0);
        }
        return w;
    }
}
