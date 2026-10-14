package io.kalo.platform.java;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Registry-free tests: ItemStack creation must be tested on a live Paper server. */
class GuiItemResolverTest {
    @Test
    void explicitNamespacesResolveToKeys() {
        assertEquals("mypack:legendary_sword",
                GuiItemResolver.customKey("mypack:legendary_sword").toString());
    }

    @Test
    void invalidOrUnqualifiedReferencesAreRejected() {
        assertNull(GuiItemResolver.customKey("MISSING_STONE"));
        assertNull(GuiItemResolver.customKey("bad namespace:item"));
        assertNull(GuiItemResolver.customKey(""));
    }

    @Test
    void stackAmountIsAlwaysWithinMaterialLimit() {
        assertEquals(64, GuiItemResolver.boundedAmount(1000, 64));
        assertEquals(1, GuiItemResolver.boundedAmount(-3, 64));
        assertEquals(1, GuiItemResolver.boundedAmount(64, 1));
    }
}
