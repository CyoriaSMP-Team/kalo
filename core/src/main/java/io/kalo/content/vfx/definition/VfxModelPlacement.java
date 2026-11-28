package io.kalo.content.vfx.definition;

import org.jetbrains.annotations.NotNull;

/**
 * Platform-neutral placement for a model layer at one recipe tick.
 *
 * <p>The runtime only turns this into a display entity; the arithmetic is kept here so
 * the offset rotation, spin and scale can be tested without a server.</p>
 */
public final class VfxModelPlacement {

    public record Placed(double x, double y, double z, float yaw, float pitch,
                         float roll, float scale) {
    }

    private VfxModelPlacement() {
    }

    public static @NotNull Placed at(@NotNull VfxRecipe.Vec3 origin, double castYaw,
                                     @NotNull VfxRecipe.Model model, int recipeTick) {
        double radians = Math.toRadians(castYaw);
        double cos = Math.cos(radians);
        double sin = Math.sin(radians);
        double x = origin.x() + rotateX(model.at().x(), model.at().z(), cos, sin);
        double y = origin.y() + model.at().y();
        double z = origin.z() + rotateZ(model.at().x(), model.at().z(), cos, sin);
        double age = Math.max(0, recipeTick - model.start());
        double yaw = castYaw + model.yaw() + model.spin() * age;
        return new Placed(x, y, z, (float) yaw, (float) model.pitch(),
                (float) model.roll(), (float) model.scale());
    }

    private static double rotateX(double x, double z, double cos, double sin) {
        return x * cos - z * sin;
    }

    private static double rotateZ(double x, double z, double cos, double sin) {
        return x * sin + z * cos;
    }
}
