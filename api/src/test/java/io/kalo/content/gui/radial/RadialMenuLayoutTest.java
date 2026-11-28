package io.kalo.content.gui.radial;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class RadialMenuLayoutTest {
    private RadialMenuLayout wheel() {
        return new RadialMenuLayout(List.of(
            new RadialMenuLayout.Segment("spawn", "Spawn", "/spawn"),
            new RadialMenuLayout.Segment("shop", "Shop", "/shop"),
            new RadialMenuLayout.Segment("warp", "Warp", "/warps"),
            new RadialMenuLayout.Segment("home", "Home", "/home")
        ), 0.2);
    }

    @Test void selectsClockwise() {
        assertEquals(0, wheel().selectedIndex(0, -1));
        assertEquals(1, wheel().selectedIndex(1, 0));
        assertEquals(2, wheel().selectedIndex(0, 1));
        assertEquals(3, wheel().selectedIndex(-1, 0));
    }

    @Test void deadZoneAndInvalidInputs() {
        assertEquals(-1, wheel().selectedIndex(0, 0));
        assertEquals(-1, wheel().selectedIndex(Double.NaN, 0));
        assertThrows(IllegalArgumentException.class, () -> new RadialMenuLayout(wheel().segments(), 1));
    }
}
