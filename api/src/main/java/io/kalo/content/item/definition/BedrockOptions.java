package io.kalo.content.item.definition;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Bedrock-platform escape hatch, read only by the Bedrock compiler.
 *
 * <p>{@code enabled} defaults to {@code true}: Bedrock support is not an add-on and
 * should not require opting in.</p>
 *
 * @param iconOverride Bedrock texture shorthand to use instead of the derived one
 * @param holdPose     optional per-item correction of how the item is held; {@code null}
 *                     keeps what Kalo derives from the Java model's {@code display}
 */
public record BedrockOptions(boolean enabled, @Nullable String iconOverride, @Nullable HoldPose holdPose) {

    public BedrockOptions(boolean enabled, @Nullable String iconOverride) {
        this(enabled, iconOverride, null);
    }

    public static @NotNull BedrockOptions defaults() {
        return new BedrockOptions(true, null, null);
    }

    /**
     * Held-item pose overrides, one per view. Values are in Bedrock bone units
     * (1/16 block) and degrees, and are added on top of the derived pose.
     */
    public record HoldPose(@Nullable View firstPerson, @Nullable View thirdPerson) {
    }

    /**
     * @param position offset {@code [x, y, z]} added to the derived position, or {@code null}
     * @param rotation offset {@code [x, y, z]} degrees added to the derived rotation, or {@code null}
     * @param scale    multiplier applied to the derived scale, or {@code null}
     */
    public record View(@Nullable List<Double> position, @Nullable List<Double> rotation, @Nullable Double scale) {
    }
}
