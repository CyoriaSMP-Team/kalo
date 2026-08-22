package io.kalo.content.feature.event;

import io.kalo.content.Content;
import org.bukkit.entity.Entity;
import org.jetbrains.annotations.NotNull;

/**
 * Fired when an entity with Kalo mob feature damages another entity.
 */
public record EntityDamageByEntityEvent(
        @NotNull Content content,
        @NotNull Entity damager,
        @NotNull Entity damaged,
        double damage
) implements FeatureEvent {
    public EntityDamageByEntityEvent withDamage(double newDamage) {
        return new EntityDamageByEntityEvent(content, damager, damaged, newDamage);
    }
}
