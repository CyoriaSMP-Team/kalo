package io.kalo.platform.java;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ViewRangeTest {

    @Test
    void fullBudgetKeepsDefaultRange() {
        assertEquals(64f, RigDisplay.viewRange(1.0f));
    }

    @Test
    void elevatedBudgetHalvesRange() {
        assertEquals(32f, RigDisplay.viewRange(0.5f));
    }

    @Test
    void criticalBudgetClampsToFloor() {
        assertEquals(16f, RigDisplay.viewRange(0.0f));
        assertEquals(16f, RigDisplay.viewRange(-1.0f));
    }

    @Test
    void overBudgetNeverExceedsDefault() {
        assertEquals(64f, RigDisplay.viewRange(4.0f));
    }
}
