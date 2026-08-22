package io.kalo.content.feature.event;

import io.kalo.content.Content;
import org.bukkit.entity.Entity;
import org.jetbrains.annotations.NotNull;

/**
 * Fired when an entity with Kalo mob feature spawns.
 */
public record EntitySpawnEvent(
        @NotNull Content content,
        @NotNull Entity entity
) implements FeatureEvent {
}
