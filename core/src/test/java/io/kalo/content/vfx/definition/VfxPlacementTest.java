package io.kalo.content.vfx.definition;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class VfxPlacementTest {

    @Test
    void offsetFollowsThePlayersFacing() {
        VfxRecipe.Vec3 ahead = VfxPlacement.offset(0, 64, 0, 0,
                new VfxRecipe.Vec3(0, 1, 3));
        assertEquals(0, ahead.x(), 1e-9);
        assertEquals(65, ahead.y(), 1e-9);
        assertEquals(3, ahead.z(), 1e-9);

        VfxRecipe.Vec3 west = VfxPlacement.offset(0, 64, 0, 90,
                new VfxRecipe.Vec3(0, 1, 3));
        assertEquals(-3, west.x(), 1e-9);
        assertEquals(65, west.y(), 1e-9);
        assertEquals(0, west.z(), 1e-9);

        VfxRecipe.Vec3 left = VfxPlacement.offset(0, 64, 0, 0,
                new VfxRecipe.Vec3(1, 0, 0));
        assertEquals(1, left.x(), 1e-9);
        assertEquals(0, left.z(), 1e-9);
    }
}
