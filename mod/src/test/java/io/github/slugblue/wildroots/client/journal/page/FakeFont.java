package io.github.slugblue.wildroots.client.journal.page;

/**
 * A fake font for layout tests: a conservative stand-in for the default
 * Minecraft font (about 5.6 px a character in Russian, from the owner's
 * screenshot, so 6 px here, wide letters 7), plus one pixel a character for
 * bold. Real text only gets narrower than this, so a layout that fits here
 * fits in the game.
 */
final class FakeFont implements TextFit.Measure {

    static final FakeFont INSTANCE = new FakeFont();

    static int charWidth(char c) {
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
