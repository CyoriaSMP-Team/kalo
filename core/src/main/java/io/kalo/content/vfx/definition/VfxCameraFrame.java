package io.kalo.content.vfx.definition;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * One camera pose for a packet transport.
 *
 * <p>{@code fov} is the requested vertical field of view. Java has no vanilla FOV packet,
 * so the NMS transport currently applies it as a dolly/zoom offset and carries the true
 * value for a transport that can set it.</p>
 */
public record VfxCameraFrame(double x, double y, double z, float yaw, float pitch,
                             float fov) {
}
