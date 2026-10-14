package io.kalo.content.feature.builtin;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ManaSettingsTest {
    @Test
    void delayPreventsImmediateRegenAfterManaUse() {
        var config = new CooldownFeature.ManaSettings(100.0, 2.5, 20L);
        assertEquals(40.0, config.afterTick(40, 1000, 1500));
        assertEquals(42.5, config.afterTick(40, 1000, 2000));
    }

    @Test
    void regenNeverExceedsMaxOrUnderflows() {
        var config = new CooldownFeature.ManaSettings(100.0, 12.0, 0L);
        assertEquals(100.0, config.afterTick(95, 0, 1000));
        assertEquals(12.0, config.afterTick(-10, 0, 1000));
        assertEquals(100.0, config.afterTick(Double.NaN, 0, 1000));
    }

    @Test
    void rejectsInvalidSettings() {
        assertThrows(IllegalArgumentException.class,
                () -> new CooldownFeature.ManaSettings(0.0, 1.0, 20));
        assertThrows(IllegalArgumentException.class,
                () -> new CooldownFeature.ManaSettings(100.0, Double.NaN, 20));
        assertThrows(IllegalArgumentException.class,
                () -> new CooldownFeature.ManaSettings(100.0, 1.0, -1));
        assertThrows(IllegalArgumentException.class,
                () -> new CooldownFeature.ManaSettings(100.0, 1.0, Long.MAX_VALUE));
    }
}
