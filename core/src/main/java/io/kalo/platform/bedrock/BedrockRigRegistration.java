package io.kalo.platform.bedrock;

import net.kyori.adventure.key.Key;
import org.jetbrains.annotations.NotNull;

/**
 * A non-mob rigged piece (vehicle or emote prop) Bedrock renders as a client
 * entity.
 *
 * @param contentKey       the Kalo vehicle/emote key
 * @param entityIdentifier the Geyser client-entity identifier
 */
public record BedrockRigRegistration(
        @NotNull Key contentKey,
        @NotNull String entityIdentifier
) {
}
