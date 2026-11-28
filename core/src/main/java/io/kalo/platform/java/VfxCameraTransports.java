package io.kalo.platform.java;

import org.jetbrains.annotations.NotNull;

/**
 * Picks the camera transport implementation.
 *
 * <p>Resolution is reflective and repeated per call; a missing class, a changed
 * constructor or any other linkage failure yields {@link VfxCameraTransport#NONE} rather
 * than an exception, so an unsupported server keeps running without camera effects.</p>
 */
public final class VfxCameraTransports {

    private VfxCameraTransports() {
    }

    /** Returns the packet transport, or the no-op transport when it cannot be built. */
    public static @NotNull VfxCameraTransport create() {
        try {
            VfxCameraTransport transport = NmsVfxCameraTransport.tryCreate();
            return transport == null ? VfxCameraTransport.NONE : transport;
        } catch (Throwable unavailable) {
            return VfxCameraTransport.NONE;
        }
    }
}
