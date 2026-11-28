package io.kalo.platform.bedrock;

import net.kyori.adventure.key.Key;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Map;

/**
 * A Blockbench block whose geometry is animated on Bedrock through a client entity.
 *
 * <p>Bedrock block geometry is static. The block itself still exists for its hitbox and
 * collision, but its terrain texture is replaced with a fully transparent one; the
 * visible model is this client entity, spawned at the block position for nearby Bedrock
 * sessions and auto-playing the authored loop animations.</p>
 *
 * @param blockKey         the Kalo block/furniture key
 * @param entityIdentifier the Geyser client-entity identifier
 * @param animations       Bedrock short animation name to full animation id
 * @param loops            short names auto-started by {@code scripts.animate}
 */
public record BedrockAnimatedBlockRegistration(
        @NotNull Key blockKey,
        @NotNull String entityIdentifier,
        @NotNull Map<String, String> animations,
        @NotNull List<String> loops
) {
    public BedrockAnimatedBlockRegistration {
        animations = Map.copyOf(animations);
        loops = List.copyOf(loops);
    }

    public BedrockAnimatedBlockRegistration(@NotNull Key blockKey,
                                            @NotNull String entityIdentifier) {
        this(blockKey, entityIdentifier, Map.of(), List.of());
    }
}
