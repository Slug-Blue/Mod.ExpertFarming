package io.github.slugblue.wildroots.genetics.testutil;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/** Self-check of the χ² helper against table values. */
class ChiSquareTest {

    @Test
    void criticalValuesAtOnePercent() {
        assertEquals(0.01, ChiSquare.pValue(6.635, 1), 1e-4);
        assertEquals(0.01, ChiSquare.pValue(9.210, 2), 1e-4);
        assertEquals(0.01, ChiSquare.pValue(21.666, 9), 1e-4);
        assertEquals(0.05, ChiSquare.pValue(3.841, 1), 1e-4);
        assertEquals(0.01, ChiSquare.pValue(88.379, 60), 1e-3);
    }
}
