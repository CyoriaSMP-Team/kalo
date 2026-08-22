package io.kalo.content.feature.event;

import io.kalo.content.Content;
import org.bukkit.entity.Player;
import org.bukkit.event.block.Action;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Fired when a player interacts with a Kalo item.
 */
public record ItemInteractEvent(
        @NotNull Content content,
        @NotNull Player player,
        @NotNull ItemStack item,
        @NotNull Action action,
        @Nullable org.bukkit.block.Block clickedBlock
) implements FeatureEvent {
}
