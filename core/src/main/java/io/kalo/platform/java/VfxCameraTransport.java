package io.kalo.platform.java;

import io.kalo.content.vfx.definition.VfxCameraFrame;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

/**
 * Outbound half of VFX camera layers: one pose per tick, per player.
 *
 * <p>Bukkit has no camera API. A working transport therefore sends a camera packet that
 * points the player's view at a temporary entity and moves that entity as the pose
 * changes. If the packet cannot be resolved, {@link #NONE} is installed instead and camera
 * layers quietly do nothing; their sprites still appear relative to the normal view.</p>
 */
public interface VfxCameraTransport {

    /** Does nothing and reports itself unavailable. */
    VfxCameraTransport NONE = new VfxCameraTransport() {
        @Override
        public boolean available() {
            return false;
        }

        @Override
        public void apply(@NotNull UUID playerId, @NotNull VfxCameraFrame frame) {
        }

        @Override
        public void clear(@NotNull UUID playerId) {
        }
    };

    /** True when {@link #apply(UUID, VfxCameraFrame)} actually reaches the client. */
    boolean available();

    /** Moves the player's client camera to the frame for the current tick. */
    void apply(@NotNull UUID playerId, @NotNull VfxCameraFrame frame);

    /** Gives the player their own camera back and releases the anchor. */
    void clear(@NotNull UUID playerId);
}
