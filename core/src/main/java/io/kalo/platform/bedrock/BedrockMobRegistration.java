package io.kalo.platform.bedrock;

import net.kyori.adventure.key.Key;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Map;

/**
 * A custom mob that Bedrock renders through a registered client entity.
 *
 * @param mobKey           the Kalo mob key
 * @param entityIdentifier the Geyser client-entity identifier
 * @param animations       short animation name to full id
 * @param loops            short loop names auto-started by the entity
 */
public record BedrockMobRegistration(
        @NotNull Key mobKey,
        @NotNull String entityIdentifier,
        @NotNull Map<String, String> animations,
        @NotNull List<String> loops
) {
    public BedrockMobRegistration {
        animations = Map.copyOf(animations);
        loops = List.copyOf(loops);
    }

    public BedrockMobRegistration(@NotNull Key mobKey, @NotNull String entityIdentifier) {
        this(mobKey, entityIdentifier, Map.of(), List.of());
    }
}
