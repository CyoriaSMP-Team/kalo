package io.kalo.content.vfx.definition;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Pure camera-frame planner for a {@code type: camera} layer.
 *
 * <p>The camera sits at the caster's eyes plus the layer's camera-space offset. Its yaw
 * and pitch follow the caster with the layer's offsets and a deterministic shake. The
 * requested FOV is converted into a dolly offset because Java's client has no FOV packet;
 * the transport receives both the position and the original FOV.</p>
 */
public final class VfxCameraPlan {

    private VfxCameraPlan() {
    }

    /** The first camera layer whose window contains {@code recipeTick}, or null. */
    public static @Nullable VfxCameraFrame activeFrame(@NotNull VfxRecipe recipe,
                                                       @NotNull VfxSimulation.CameraBasis view,
                                                       int recipeTick, double castScale) {
        for (VfxRecipe.Camera camera : recipe.cameras()) {
            if (recipeTick >= camera.layer().start()
                    && recipeTick < camera.layer().end()) {
                return frame(camera, view, recipeTick, castScale);
            }
        }
        return null;
    }

    public static @NotNull VfxCameraFrame frame(@NotNull VfxRecipe.Camera camera,
                                                @NotNull VfxSimulation.CameraBasis view,
                                                int recipeTick, double castScale) {
        double yawRadians = Math.toRadians(view.yaw());
        double pitchRadians = Math.toRadians(view.pitch());
        double cosPitch = Math.cos(pitchRadians);
        double forwardX = -Math.sin(yawRadians) * cosPitch;
        double forwardY = -Math.sin(pitchRadians);
        double forwardZ = Math.cos(yawRadians) * cosPitch;
        double rightX = -Math.cos(yawRadians);
        double rightZ = -Math.sin(yawRadians);
        // right is horizontal, so rightY is zero; up = right x forward.
        double upX = -rightZ * forwardY;
        double upY = rightZ * forwardX - rightX * forwardZ;
        double upZ = rightX * forwardY;

        double phase = recipeTick + camera.layer().start();
        double shake = camera.shake();
        double shakeX = shake * Math.sin(phase * 1.7);
        double shakeY = shake * Math.cos(phase * 2.3);
        double offsetX = camera.layer().at().x() * castScale + shakeX;
        double offsetY = camera.layer().at().y() * castScale + shakeY;
        double offsetZ = camera.layer().at().z() * castScale + dolly(camera, castScale);

        double x = view.x() + rightX * offsetX + upX * offsetY + forwardX * offsetZ;
        double y = view.y() + upY * offsetY + forwardY * offsetZ;
        double z = view.z() + rightZ * offsetX + upZ * offsetY + forwardZ * offsetZ;

        float yaw = (float) (view.yaw() + camera.yaw()
                + shake * 6.0 * Math.sin(phase * 1.3));
        float pitch = clamp((float) (view.pitch() + camera.pitch()
                + shake * 5.0 * Math.cos(phase * 1.9)), -90f, 90f);
        return new VfxCameraFrame(x, y, z, yaw, pitch, (float) camera.fov());
    }

    /**
     * A dolly approximation of the requested FOV: a narrower view moves the camera
     * towards the effect, a wider view moves it back. The clamp keeps the camera from
     * crossing the sprite plane.
     */
    private static double dolly(@NotNull VfxRecipe.Camera camera, double castScale) {
        double distance = camera.distance() * castScale;
        double offset = (1.0 - camera.fov() / 70.0) * distance;
        double limit = distance * 0.8;
        return clamp(offset, -limit, limit);
    }

    private static double clamp(double value, double minimum, double maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    private static float clamp(float value, float minimum, float maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }
}
