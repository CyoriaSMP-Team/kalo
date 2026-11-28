package io.kalo.platform.java;

import net.kyori.adventure.key.Key;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Prevents a player from picking up, using or placing a Kalo item they are not entitled to.
 *
 * <p>Pack filtering hides the model; this gate makes the content unusable server-side even
 * if another player drops it, an admin gives it, or it was already in an inventory.</p>
 */
public final class ContentEntitlementListener implements Listener {

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
    public void onPickup(@NotNull EntityPickupItemEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        Key key = contentKey(event.getItem().getItemStack());
        if (key != null && !ContentEntitlements.allows(player, key)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
    public void onInteract(@NotNull PlayerInteractEvent event) {
        Key key = contentKey(event.getItem());
        if (key != null && !ContentEntitlements.allows(event.getPlayer(), key)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
    public void onInteractEntity(@NotNull PlayerInteractEntityEvent event) {
        EquipmentSlot hand = event.getHand();
        Key key = contentKey(event.getPlayer().getInventory().getItem(hand));
        if (key != null && !ContentEntitlements.allows(event.getPlayer(), key)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
    public void onPlace(@NotNull BlockPlaceEvent event) {
        Key key = contentKey(event.getItemInHand());
        if (key != null && !ContentEntitlements.allows(event.getPlayer(), key)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
    public void onPrepareCraft(@NotNull PrepareItemCraftEvent event) {
        Key key = contentKey(event.getInventory().getResult());
        if (key == null) {
            return;
        }
        for (org.bukkit.entity.HumanEntity viewer : event.getViewers()) {
            if (viewer instanceof Player player
                    && !ContentEntitlements.allows(player, key)) {
                event.getInventory().setResult(null);
                return;
            }
        }
    }

    private static @Nullable Key contentKey(@Nullable ItemStack stack) {
        if (stack == null || stack.getType().isAir() || !stack.hasItemMeta()) {
            return null;
        }
        String value = stack.getItemMeta().getPersistentDataContainer()
                .get(JavaItemCompiler.ITEM_ID_KEY, PersistentDataType.STRING);
        if (value == null) {
            return null;
        }
        try {
            return Key.key(value);
        } catch (RuntimeException malformed) {
            return null;
        }
    }
}
