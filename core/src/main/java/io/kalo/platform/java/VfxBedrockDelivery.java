package io.kalo.platform.java;

import io.kalo.content.vfx.definition.VfxModelPlacement;
import io.kalo.content.vfx.definition.VfxSimulation;
import io.kalo.integration.BedrockSupport;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * The one place a Java VFX sprite crosses into the Bedrock seam.
 *
 * <p>The Bedrock geometry is baked from the layer's own size at pack build time, so the
 * value carried here is the cast-level {@code scale:} multiplier, never the absolute
 * {@link VfxSimulation.Sprite#size()}. Sending the absolute size would apply the layer
 * size twice.</p>
 */
final class VfxBedrockDelivery {

    private VfxBedrockDelivery() {
    }

    static void spawn(@NotNull BedrockSupport support, @NotNull UUID displayId,
                      @NotNull String entityIdentifier, @NotNull UUID worldId,
                      @NotNull VfxSimulation.Sprite sprite, float yaw,
                      @Nullable UUID viewerId, double castScale) {
        support.clientRigSpawned(displayId, entityIdentifier, worldId,
                sprite.x(), sprite.y(), sprite.z(), yaw, viewerId, (float) castScale);
    }

    static void move(@NotNull BedrockSupport support, @NotNull UUID displayId,
                     @NotNull VfxSimulation.Sprite sprite, float yaw, double castScale) {
        support.clientRigMoved(displayId, sprite.x(), sprite.y(), sprite.z(), yaw,
                (float) castScale);
    }

    static void modelSpawn(@NotNull BedrockSupport support, @NotNull UUID displayId,
                           @NotNull String entityIdentifier, @NotNull UUID worldId,
                           @NotNull VfxModelPlacement.Placed placed,
                           @Nullable UUID viewerId) {
        support.clientRigSpawned(displayId, entityIdentifier, worldId,
                placed.x(), placed.y(), placed.z(), placed.yaw(), placed.pitch(),
                viewerId, placed.scale());
    }

    static void modelMove(@NotNull BedrockSupport support, @NotNull UUID displayId,
                          @NotNull VfxModelPlacement.Placed placed) {
        support.clientRigMoved(displayId, placed.x(), placed.y(), placed.z(),
                placed.yaw(), placed.pitch(), placed.scale());
    }
}
