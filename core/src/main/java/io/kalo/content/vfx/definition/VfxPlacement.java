package io.kalo.content.vfx.definition;

import org.jetbrains.annotations.NotNull;

/** Pure cast placement math for the {@code offset} option. */
public final class VfxPlacement {

    private VfxPlacement() {
    }

    /**
     * Applies Studio's left/up/ahead offset to a feet position. Yaw 0 faces south (+Z),
     * yaw 90 faces west (-X).
     */
    public static @NotNull VfxRecipe.Vec3 offset(double x, double y, double z,
                                                 double yaw,
                                                 @NotNull VfxRecipe.Vec3 offset) {
        double radians = Math.toRadians(yaw);
        double cos = Math.cos(radians);
        double sin = Math.sin(radians);
        double left = offset.x();
        double ahead = offset.z();
        return new VfxRecipe.Vec3(
                x + left * cos - ahead * sin,
                y + offset.y(),
                z - left * sin + ahead * cos);
    }
}
