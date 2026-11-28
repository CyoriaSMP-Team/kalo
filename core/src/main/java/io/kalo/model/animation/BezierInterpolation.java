package io.kalo.model.animation;

import io.kalo.model.ModelIR.BezierHandles;
import io.kalo.model.ModelIR.Keyframe;
import io.kalo.model.ModelIR.Vec3;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Cubic Bezier interpolation for Blockbench keyframes.
 *
 * <p>Handles are per axis. For a segment from {@code left} to {@code right}, Blockbench
 * uses the left keyframe's right handle and the right keyframe's left handle. Time
 * handles are clamped to the segment just as Blockbench does; the curve is then solved for
 * the requested time rather than approximating it with a fixed point grid.</p>
 */
public final class BezierInterpolation {

    private static final int SOLVE_ITERATIONS = 40;

    private BezierInterpolation() {
    }

    /** Blockbench uses Bezier when either endpoint declares it. */
    public static boolean usesBezier(@Nullable String left, @Nullable String right) {
        return "bezier".equalsIgnoreCase(left) || "bezier".equalsIgnoreCase(right);
    }

    public static @NotNull Vec3 sample(@NotNull Keyframe left, @NotNull Keyframe right,
                                       double time) {
        return sample(left.time(), left.dataPoints().getFirst(), left.bezier(),
                right.time(), right.dataPoints().getFirst(), right.bezier(), time);
    }

    public static @NotNull Vec3 sample(double leftTime, @NotNull Vec3 leftValue,
                                       @NotNull BezierHandles leftHandles,
                                       double rightTime, @NotNull Vec3 rightValue,
                                       @NotNull BezierHandles rightHandles,
                                       double time) {
        double gap = rightTime - leftTime;
        if (gap <= 0) {
            return rightValue;
        }
        return new Vec3(
                axis(leftTime, leftValue.x(), leftHandles.rightTime().x(),
                        leftHandles.rightValue().x(), rightTime, rightValue.x(),
                        rightHandles.leftTime().x(), rightHandles.leftValue().x(), time),
                axis(leftTime, leftValue.y(), leftHandles.rightTime().y(),
                        leftHandles.rightValue().y(), rightTime, rightValue.y(),
                        rightHandles.leftTime().y(), rightHandles.leftValue().y(), time),
                axis(leftTime, leftValue.z(), leftHandles.rightTime().z(),
                        leftHandles.rightValue().z(), rightTime, rightValue.z(),
                        rightHandles.leftTime().z(), rightHandles.leftValue().z(), time));
    }

    private static double axis(double leftTime, double leftValue,
                               double rightHandleTime, double rightHandleValue,
                               double rightTime, double rightValue,
                               double leftHandleTime, double leftHandleValue,
                               double targetTime) {
        double gap = rightTime - leftTime;
        double p1x = leftTime + clamp(rightHandleTime, 0, gap);
        double p2x = rightTime + clamp(leftHandleTime, -gap, 0);
        double p1y = leftValue + rightHandleValue;
        double p2y = rightValue + leftHandleValue;

        double u = solve(leftTime, p1x, p2x, rightTime, targetTime);
        return cubic(leftValue, p1y, p2y, rightValue, u);
    }

    private static double solve(double p0x, double p1x, double p2x, double p3x,
                                double target) {
        double low = 0;
        double high = 1;
        for (int iteration = 0; iteration < SOLVE_ITERATIONS; iteration++) {
            double middle = (low + high) / 2;
            if (cubic(p0x, p1x, p2x, p3x, middle) < target) {
                low = middle;
            } else {
                high = middle;
            }
        }
        return (low + high) / 2;
    }

    private static double cubic(double p0, double p1, double p2, double p3, double u) {
        double inverse = 1 - u;
        return inverse * inverse * inverse * p0
                + 3 * inverse * inverse * u * p1
                + 3 * inverse * u * u * p2
                + u * u * u * p3;
    }

    private static double clamp(double value, double minimum, double maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }
}
