package io.kalo.content.furniture.definition;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Display transform properties for furniture rendering.
 *
 * <p>Controls how the furniture model is displayed in the world: rotation tracking,
 * translation offset, scale, brightness override, and shadow properties.</p>
 *
 * @param displayTransform  display transform type: NONE, HEAD, FIXED, GROUND, etc.
 * @param trackingRotation  billboard mode: FIXED, VERTICAL, HORIZONTAL, CENTER
 * @param translation       [x, y, z] offset from block center
 * @param scale             [x, y, z] scale factors
 * @param brightness        light level override, null for default
 * @param shadowRadius      shadow radius, null for default
 * @param shadowStrength    shadow strength (0.0-1.0), null for default
 * @param viewRange         view range multiplier, null for default (1.0)
 * @param displayWidth      culling width, null for default
 * @param displayHeight     culling height, null for default
 */
public record FurnitureDisplay(
        @Nullable String displayTransform,
        @Nullable String trackingRotation,
        @Nullable double[] translation,
        @Nullable double[] scale,
        @Nullable Brightness brightness,
        @Nullable Float shadowRadius,
        @Nullable Float shadowStrength,
        @Nullable Float viewRange,
        @Nullable Float displayWidth,
        @Nullable Float displayHeight
) {

    /**
     * Light level override.
     *
     * @param blockLight  block light level (0-15)
     * @param skyLight    sky light level (0-15)
     */
    public record Brightness(int blockLight, int skyLight) {
        public Brightness {
            if (blockLight < 0 || blockLight > 15) {
                throw new IllegalArgumentException("blockLight must be 0-15, got " + blockLight);
            }
            if (skyLight < 0 || skyLight > 15) {
                throw new IllegalArgumentException("skyLight must be 0-15, got " + skyLight);
            }
        }
    }

    public static @NotNull FurnitureDisplay defaults() {
        return new FurnitureDisplay(null, null, null, null, null, null, null, null, null, null);
    }
}
