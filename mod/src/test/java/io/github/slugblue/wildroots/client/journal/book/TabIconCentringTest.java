package io.github.slugblue.wildroots.client.journal.book;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

/** Tab icons are centred by their visible pixels, in the art and in the draw position. */
class TabIconCentringTest {

    private static final String DIR = "/assets/wildroots/textures/gui/journal/";

    private static BufferedImage read(String name) throws IOException {
        try (InputStream in = TabIconCentringTest.class.getResourceAsStream(DIR + name)) {
            return ImageIO.read(in);
        }
    }

    @Test
    void everyIconIsCentredInItsCellByVisiblePixels() throws IOException {
        BufferedImage img = read("journal_icons.png");
        for (int i = 0; i < JournalAtlas.ICON_SPRITES.size(); i++) {
            int ox = (i % 4) * 16;
            int oy = (i / 4) * 16;
            int minX = 16, minY = 16, maxX = -1, maxY = -1;
            for (int y = 0; y < 16; y++) {
                for (int x = 0; x < 16; x++) {
                    if ((img.getRGB(ox + x, oy + y) >>> 24) != 0) {
                        minX = Math.min(minX, x);
                        maxX = Math.max(maxX, x);
                        minY = Math.min(minY, y);
                        maxY = Math.max(maxY, y);
                    }
                }
            }
            String n = JournalAtlas.ICON_SPRITES.get(i);
            assertTrue(maxX >= 0, n + " is empty");
            // centre offset in px = (left margin - right margin) / 2, within 1 px
            assertTrue(Math.abs(minX - (15 - maxX)) <= 2, n + " off centre horizontally: " + minX + ".." + maxX);
            assertTrue(Math.abs(minY - (15 - maxY)) <= 2, n + " off centre vertically: " + minY + ".." + maxY);
        }
    }

    @Test
    void theDrawPositionIsCentredOnTheVisibleFaceOfAllTabs() throws IOException {
        BufferedImage img = read("journal.png");
        JournalAtlas.Sprite tab = JournalAtlas.TAB;
        for (int state = 0; state < 3; state++) {
            int u = tab.u() + state * tab.width();
            // the face: the flat colour on the middle row/column, up to where the cover starts
            int face = img.getRGB(u + 12, tab.v() + 10);
            int faceL = -1, faceR = -1, faceT = -1, faceB = -1;
            for (int x = 0; x < BookGeometry.TABS_OUT; x++) {
                if (img.getRGB(u + x, tab.v() + 10) == face) {
                    faceL = faceL < 0 ? x : faceL;
                    faceR = x;
                }
            }
            for (int y = 0; y < tab.height(); y++) {
                if (img.getRGB(u + 12, tab.v() + y) == face) {
                    faceT = faceT < 0 ? y : faceT;
                    faceB = y;
                }
            }
            assertTrue(faceL >= 0 && faceT >= 0, "no face in state " + state);
            // doubled centres, to stay in integers
            int faceCx = faceL + faceR + 1;
            int faceCy = faceT + faceB + 1;
            int iconCx = 2 * BookGeometry.TAB_ICON_X + 16;
            int iconCy = 2 * BookGeometry.TAB_ICON_Y + 16;
            assertTrue(Math.abs(faceCx - iconCx) <= 2, "state " + state + " x: face " + faceL + ".." + faceR);
            assertTrue(Math.abs(faceCy - iconCy) <= 2, "state " + state + " y: face " + faceT + ".." + faceB);
        }
        // all 8 tabs share the same offset from their own top
        BookGeometry geo = BookGeometry.center(480, 270);
        for (int i = 0; i < BookGeometry.MAX_TABS; i++) {
            assertEquals(geo.tabY(i) + BookGeometry.TAB_ICON_Y - geo.tabY(0) - i * BookGeometry.TAB_H,
                    BookGeometry.TAB_ICON_Y);
        }
    }
}
