package io.kalo.content.item.definition;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Bedrock-platform escape hatch, read only by the Bedrock compiler.
 *
 * <p>Lets one definition opt out of Bedrock output, choose the flat inventory icon
 * shorthand, and correct the held pose. The definition layer stays platform-neutral; only
 * this named section and the Bedrock compiler know what those choices mean.</p>
 *
 * <p>{@code enabled} defaults to {@code true}: Bedrock support is not an add-on and
 * should not require opting in.</p>
 *
 * @param iconOverride Bedrock texture shorthand to use instead of the derived one
 * @param scale        held-item scale, replacing the compiler's half-size base; null keeps
 *                     the default. A 16-unit model needs roughly 0.5 to fill a hand, so
 *                     small props usually want less
 * @param rotation     held-item rotation in Bedrock degrees (X, Y, Z); null keeps the
 *                     rotation derived from the Java display transform
 */
public record BedrockOptions(boolean enabled, @Nullable String iconOverride,
                             @Nullable Double scale, @Nullable List<Double> rotation) {

    public BedrockOptions {
        rotation = rotation == null ? null : List.copyOf(rotation);
        if (rotation != null && rotation.size() != 3) {
            throw new IllegalArgumentException("bedrock.rotation needs exactly three numbers");
        }
        if (scale != null && (!Double.isFinite(scale) || scale <= 0 || scale > 4)) {
            throw new IllegalArgumentException("bedrock.scale must be between 0 and 4");
        }
    }

    /** Most definitions only opt out or rename their icon. */
    public BedrockOptions(boolean enabled, @Nullable String iconOverride) {
        this(enabled, iconOverride, null, null);
    }

    public static @NotNull BedrockOptions defaults() {
        return new BedrockOptions(true, null);
    }
}
