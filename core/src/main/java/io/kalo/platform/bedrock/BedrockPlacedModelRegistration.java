package io.kalo.platform.bedrock;

import net.kyori.adventure.key.Key;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Map;

/**
 * One item model the Bedrock runtime can place as a Kalo client entity.
 *
 * @param javaKey          the item/content key the Java stack carries
 * @param entityIdentifier the Bedrock client entity identifier registered with Geyser
 * @param geometry         the Bedrock geometry identifier
 * @param texture          the texture path without the {@code .png} suffix
 * @param animations       short animation name to full Bedrock animation id
 * @param loops            animation short names that start automatically
 * @param scale            placement scale multiplier
 */
public record BedrockPlacedModelRegistration(
        @NotNull Key javaKey,
        @NotNull String entityIdentifier,
        @NotNull String geometry,
        @NotNull String texture,
        @NotNull Map<String, String> animations,
        @NotNull List<String> loops,
        double scale
) {
    public BedrockPlacedModelRegistration {
        animations = Map.copyOf(animations);
        loops = List.copyOf(loops);
    }
}
