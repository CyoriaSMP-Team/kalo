package io.kalo.platform.bedrock;

import net.kyori.adventure.key.Key;
import org.jetbrains.annotations.NotNull;

/**
 * Reconstructs the animation ids the Bedrock compiler emits.
 *
 * <p>Attachables are not client entities, so there is no registration object to carry the
 * animation table; the id is deterministic from the armor key and clip name.</p>
 */
public final class BedrockAnimationIds {

    private BedrockAnimationIds() {
    }

    public static @NotNull String armor(@NotNull Key armorKey, @NotNull String animation) {
        String stem = armorKey.namespace() + "_" + armorKey.value() + "_armor";
        return "animation.kalo." + sanitize(stem) + "." + sanitize(animation);
    }

    private static @NotNull String sanitize(@NotNull String value) {
        String cleaned = value.toLowerCase(java.util.Locale.ROOT)
                .replaceAll("[^a-z0-9_]+", "_")
                .replaceAll("_{2,}", "_")
                .replaceAll("^_+|_+$", "");
        return cleaned.isBlank() ? "animation" : cleaned;
    }
}
