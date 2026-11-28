package io.kalo.platform.bedrock;

import io.kalo.pack.TransparentPng;
import org.jetbrains.annotations.NotNull;

/**
 * A fully transparent texture used to hide a static Bedrock block when its visible model
 * is a client entity.
 *
 * <p>Bedrock blocks cannot carry skeletal animation. Keeping the block registered is what
 * preserves placement, hardness, collisions and Geyser's palette entry; hiding only its
 * surface lets the animated entity be the visible mesh without double-drawing.</p>
 */
final class BedrockTransparentTexture {

    private BedrockTransparentTexture() {
    }

    static byte @NotNull [] png() {
        return TransparentPng.bytes();
    }
}
