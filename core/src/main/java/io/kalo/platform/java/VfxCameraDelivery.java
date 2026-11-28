package io.kalo.platform.java;

import io.kalo.content.vfx.definition.VfxCameraFrame;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

/** Single hand-off point from the VFX runtime to the camera transport. */
final class VfxCameraDelivery {

    private VfxCameraDelivery() {
    }

    /** Shows the frame to the player for this tick. */
    static void apply(@NotNull VfxCameraTransport transport, @NotNull UUID playerId,
                      @NotNull VfxCameraFrame frame) {
        transport.apply(playerId, frame);
    }

    /** Ends the camera override for the player. */
    static void clear(@NotNull VfxCameraTransport transport, @NotNull UUID playerId) {
        transport.clear(playerId);
    }
}
