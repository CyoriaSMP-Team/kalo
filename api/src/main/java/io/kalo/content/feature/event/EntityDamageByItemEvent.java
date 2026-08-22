package io.kalo.content.feature.event;

import io.kalo.content.Content;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

/**
 * Fired when an entity is damaged by a player holding a Kalo item.
 */
public record EntityDamageByItemEvent(
        @NotNull Content content,
        @NotNull Player attacker,
        @NotNull LivingEntity target,
        @NotNull ItemStack item,
        double damage
) implements FeatureEvent {
    public EntityDamageByItemEvent withDamage(double newDamage) {
        return new EntityDamageByItemEvent(content, attacker, target, item, newDamage);
    }
}
