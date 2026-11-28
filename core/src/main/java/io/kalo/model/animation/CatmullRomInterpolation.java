package io.kalo.model.animation;

import io.kalo.model.ModelIR.Vec3;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Uniform Catmull-Rom sampling for {@code lerp_mode: catmullrom} keyframes.
 *
 * <p>Blockbench builds a Three.js {@code SplineCurve} from the immediate neighbours and
 * samples its middle segment; for an interior segment that is exactly the uniform
 * Catmull-Rom formula below. Missing neighbours are represented by duplicating the
 * endpoint value, which is how Three.js collapses a two- or three-point curve.</p>
 */
public final class CatmullRomInterpolation {

    private CatmullRomInterpolation() {
    }

    /** Bedrock emits {@code lerp_mode: catmullrom} for both spellings. */
    public static boolean usesCatmullRom(@Nullable String leftInterpolation) {
        return "catmullrom".equalsIgnoreCase(leftInterpolation)
                || "smooth".equalsIgnoreCase(leftInterpolation);
    }

    public static @NotNull Vec3 sample(@NotNull Vec3 before, @NotNull Vec3 left,
                                       @NotNull Vec3 right, @NotNull Vec3 after,
                                       double factor) {
        double t = Math.max(0, Math.min(1, factor));
        return new Vec3(
                axis(before.x(), left.x(), right.x(), after.x(), t),
                axis(before.y(), left.y(), right.y(), after.y(), t),
                axis(before.z(), left.z(), right.z(), after.z(), t));
    }

    private static double axis(double p0, double p1, double p2, double p3, double t) {
        double v0 = (p2 - p0) * 0.5;
        double v1 = (p3 - p1) * 0.5;
        double t2 = t * t;
        double t3 = t2 * t;
        return (2 * p1 - 2 * p2 + v0 + v1) * t3
                + (-3 * p1 + 3 * p2 - 2 * v0 - v1) * t2
                + v0 * t + p1;
    }
}
