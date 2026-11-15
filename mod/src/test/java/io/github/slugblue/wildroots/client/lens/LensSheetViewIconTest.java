package io.github.slugblue.wildroots.client.lens;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.github.slugblue.wildroots.lens.LensSheet;
import org.junit.jupiter.api.Test;

/** Review 1 nit: every drawn icon maps to the journal legend's sprite of the same name. */
class LensSheetViewIconTest {

    @Test
    void everyIconMapsByName() {
        for (LensSheet.Icon i : LensSheet.Icon.values()) {
            if (i != LensSheet.Icon.NONE) {
                assertEquals(i.name(), LensSheetView.flag(i).name());
            }
        }
    }
}
