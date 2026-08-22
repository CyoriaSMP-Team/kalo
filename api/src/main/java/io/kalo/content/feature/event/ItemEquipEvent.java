package io.kalo.content.feature.event;

import io.kalo.content.Content;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

/**
 * Fired when a player equips a Kalo item.
 */
public record ItemEquipEvent(
        @NotNull Content content,
        @NotNull Player player,
        @NotNull ItemStack item,
        @NotNull org.bukkit.inventory.EquipmentSlot slot
) implements FeatureEvent {
}
