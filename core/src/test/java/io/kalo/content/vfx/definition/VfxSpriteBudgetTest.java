package io.kalo.content.vfx.definition;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VfxSpriteBudgetTest {

    @Test
    void theOldestCastsStopUntilThePlannedCountFits() {
        List<VfxSpriteBudget.Usage> usage = List.of(
                new VfxSpriteBudget.Usage(30, 40),
                new VfxSpriteBudget.Usage(10, 50),
                new VfxSpriteBudget.Usage(20, 60));

        assertEquals(List.of(10L),
                VfxSpriteBudget.stopOldest(usage, 120));
        assertTrue(VfxSpriteBudget.stopOldest(usage, 0).isEmpty());
        assertEquals(List.of(10L, 20L, 30L),
                VfxSpriteBudget.stopOldest(usage, 30));
    }
}
