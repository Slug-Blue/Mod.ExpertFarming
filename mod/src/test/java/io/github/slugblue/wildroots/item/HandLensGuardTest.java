package io.github.slugblue.wildroots.item;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.slugblue.wildroots.lens.LensPanel;
import org.junit.jupiter.api.Test;

/** B12: which clicks are the client-only creative inventory (the lens cannot inspect there), and the hint. */
class HandLensGuardTest {

    @Test
    void onlyTheClientCreativeInventoryIsBlocked() {
        // creative inventory screen, any tab (survival tab included): client, creative, picker id 0
        assertTrue(LensPanel.creativeInventoryClick(true, true, 0));
        // survival inventory: client but not creative
        assertFalse(LensPanel.creativeInventoryClick(true, false, 0));
        // a chest or another menu while creative: id above 0
        assertFalse(LensPanel.creativeInventoryClick(true, true, 1));
        assertFalse(LensPanel.creativeInventoryClick(true, true, 7));
        // the server inspects whenever a click reaches it
        assertFalse(LensPanel.creativeInventoryClick(false, true, 0));
        assertFalse(LensPanel.creativeInventoryClick(false, false, 0));
    }

    @Test
    void clickHintIsTheTruthInEachContext() {
        assertEquals(LensPanel.CLICK, LensPanel.clickHintKey(false));
        assertEquals(LensPanel.CLICK_HAND, LensPanel.clickHintKey(true));
        // no client hook on a server or in a test: the click hint
        assertEquals(LensPanel.CLICK, LensPanel.clickHintKey());
        LensPanel.creativeScreen(() -> true);
        try {
            assertEquals(LensPanel.CLICK_HAND, LensPanel.clickHintKey());
        } finally {
            LensPanel.creativeScreen(null);
        }
        assertEquals(LensPanel.CLICK, LensPanel.clickHintKey());
    }
}
