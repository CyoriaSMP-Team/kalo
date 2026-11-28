package io.kalo.integration;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * Callback surface for everything Kalo wants to tell or ask a Bedrock runtime.
 *
 * <p>Keeping this an interface is what lets the rest of the plugin load on a server with no
 * Geyser installed: no implementation type is referenced here, so nothing has to be
 * resolved until an implementation exists. When none does, {@link #NONE} stands in — it
 * reports "no" and discards events instead of throwing a linkage error from an unrelated
 * class.</p>
 *
 * <p>Every event method has an empty default body, so an implementation only has to
 * override the notifications it can actually act on.</p>
 */
public interface BedrockSupport {

    /** Placeholder for Java-only servers: unavailable, and nobody is on Bedrock. */
    BedrockSupport NONE = new BedrockSupport() {
        @Override
        public boolean available() {
            return false;
        }

        @Override
        public boolean isBedrock(@NotNull UUID playerId) {
            return false;
        }
    };

    /** True when a Bedrock runtime is installed and its hooks can be called. */
    boolean available();

    /** True when this player's connection came in over the Bedrock protocol. */
    boolean isBedrock(@NotNull UUID playerId);

    /**
     * A placed rig entered the world, without placement details. Runtimes that need a
     * position override the overload below instead; this one is the fallback for callers
     * that only know the identity.
     */
    default void rigPlaced(@NotNull UUID hitboxId, @NotNull String modelId) {
    }

    /**
     * A placed rig entered the world at a known position. An implementation typically
     * spawns a client-side entity for each Bedrock player near enough to see it.
     */
    default void rigPlaced(@NotNull UUID hitboxId, @NotNull String modelId,
                           double x, double y, double z, float yaw, float scale) {
        rigPlaced(hitboxId, modelId);
    }

    /** A placed rig was taken out of the world. */
    default void rigRemoved(@NotNull UUID hitboxId) {
    }

    /** A named clip started on a placed rig. */
    default void animationStarted(@NotNull UUID hitboxId, @NotNull String modelId,
                                 @NotNull String animation) {
    }

    /** A custom block was placed. Blocks with animation are drawn by a client entity. */
    default void blockPlaced(@NotNull java.util.UUID worldId, int x, int y, int z,
                             @NotNull String blockId) {
    }

    /** An interaction clip on a worn 3D armor piece started for a Bedrock player. */
    default void armorAnimationStarted(@NotNull java.util.UUID playerId,
                                       @NotNull String armorId,
                                       @NotNull String animation) {
    }

    /** A custom block was removed from the world. */
    default void blockRemoved(@NotNull java.util.UUID worldId, int x, int y, int z) {
    }

    /** A right-click triggered a clip on an animated block's client entity. */
    default void blockAnimationStarted(@NotNull java.util.UUID worldId, int x, int y, int z,
                                       @NotNull String blockId,
                                       @NotNull String animation) {
    }

    /** A custom mob spawned; Bedrock sees it as a client entity. */
    default void mobSpawned(@NotNull java.util.UUID entityId, @NotNull String mobId,
                            @NotNull java.util.UUID worldId, double x, double y, double z,
                            float yaw) {
    }

    /** A custom mob changed position or facing. */
    default void mobMoved(@NotNull java.util.UUID entityId, double x, double y, double z,
                          float yaw) {
    }

    /** A custom mob started one of its interaction clips. */
    default void mobAnimationStarted(@NotNull java.util.UUID entityId,
                                     @NotNull String animation) {
    }

    /** A custom mob died, was removed, or left the loaded world. */
    default void mobRemoved(@NotNull java.util.UUID entityId) {
    }

    /**
     * A generic rigged object (vehicle, emote prop, …) entered the world. The caller has
     * already worked out the Bedrock entity identifier it should be rendered as.
     */
    default void clientRigSpawned(@NotNull java.util.UUID entityId,
                            @NotNull String entityIdentifier,
                            @NotNull java.util.UUID worldId,
                            double x, double y, double z, float yaw) {
    }

    /**
     * Variant that also carries a Bedrock scale multiplier.
     *
     * <p>VFX pass the cast-level {@code scale:} option through here. Geometry is already
     * authored at its layer size, so the multiplier is relative to that and must not
     * repeat it.</p>
     */
    default void clientRigSpawned(@NotNull java.util.UUID entityId,
                            @NotNull String entityIdentifier,
                            @NotNull java.util.UUID worldId,
                            double x, double y, double z, float yaw, float scale) {
        clientRigSpawned(entityId, entityIdentifier, worldId, x, y, z, yaw);
    }

    /** Variant that shows the rig only to the given player when {@code viewerId} is set. */
    default void clientRigSpawned(@NotNull java.util.UUID entityId,
                            @NotNull String entityIdentifier,
                            @NotNull java.util.UUID worldId,
                            double x, double y, double z, float yaw,
                            @Nullable java.util.UUID viewerId) {
        clientRigSpawned(entityId, entityIdentifier, worldId, x, y, z, yaw);
    }

    /** Variant carrying both a viewer restriction and a scale multiplier. */
    default void clientRigSpawned(@NotNull java.util.UUID entityId,
                            @NotNull String entityIdentifier,
                            @NotNull java.util.UUID worldId,
                            double x, double y, double z, float yaw,
                            @Nullable java.util.UUID viewerId, float scale) {
        // Implementations are allowed to support only one of the two extras. Prefer the
        // viewer-scoped call when nothing is scaled; otherwise send the scale, because a
        // wrong size is more noticeable than an extra player seeing the rig.
        if (viewerId != null && scale == 1f) {
            clientRigSpawned(entityId, entityIdentifier, worldId, x, y, z, yaw, viewerId);
        } else {
            clientRigSpawned(entityId, entityIdentifier, worldId, x, y, z, yaw, scale);
        }
    }

    /**
     * Variant with pitch as well as yaw. Pitch is only meaningful to VFX model layers; the
     * default drops it and keeps the yaw-plus-scale behaviour.
     */
    default void clientRigSpawned(@NotNull java.util.UUID entityId,
                            @NotNull String entityIdentifier,
                            @NotNull java.util.UUID worldId,
                            double x, double y, double z, float yaw, float pitch,
                            @Nullable java.util.UUID viewerId, float scale) {
        clientRigSpawned(entityId, entityIdentifier, worldId, x, y, z, yaw,
                viewerId, scale);
    }

    /** A generic rig changed position or facing. */
    default void clientRigMoved(@NotNull java.util.UUID entityId,
                          double x, double y, double z, float yaw) {
    }

    /** A generic rig moved, optionally with a new scale multiplier. */
    default void clientRigMoved(@NotNull java.util.UUID entityId,
                          double x, double y, double z, float yaw, float scale) {
        clientRigMoved(entityId, x, y, z, yaw);
    }

    /** A generic rig moved, with pitch and a scale multiplier. */
    default void clientRigMoved(@NotNull java.util.UUID entityId,
                          double x, double y, double z, float yaw, float pitch,
                          float scale) {
        clientRigMoved(entityId, x, y, z, yaw, scale);
    }

    /** A generic rig left the world. */
    default void clientRigRemoved(@NotNull java.util.UUID entityId) {
    }

    /** A placed rig swapped to another state model in place. */
    default void rigStateChanged(@NotNull UUID hitboxId, @NotNull String modelId,
                                 @NotNull String stateModelId, double x, double y, double z,
                                 float yaw, float scale) {
        rigRemoved(hitboxId);
        rigPlaced(hitboxId, stateModelId, x, y, z, yaw, scale);
    }

    /** A player's Bedrock session ended; drop any state kept per player. */
    default void forget(@NotNull UUID playerId) {
    }

    /** XUID of a player connected over Bedrock, or null for a Java player. */
    default @org.jetbrains.annotations.Nullable String xuid(
            @NotNull java.util.UUID playerId) {
        return null;
    }

    /** A Bedrock player joined; show them the placed models that are already active. */
    default void refresh() {
    }

    /** The generated Bedrock pack changed: stage the new copy and refresh live sessions. */
    default void packUpdated() {
    }
}
