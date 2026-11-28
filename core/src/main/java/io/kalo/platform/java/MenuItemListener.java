package io.kalo.platform.java;

import io.kalo.utils.Plugins;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * A hotbar item that opens a menu for players who cannot type a command.
 *
 * <p>Bedrock clients can end up with chat blocked at the account level, and then no
 * command reaches the server at all — the menu is unreachable however correct the command
 * registration is. A right click needs no chat, so it works on every client that can play.
 *
 * <p>The item is marked in its persistent data container, so a renamed or retextured copy
 * still works and an ordinary compass does not.
 */
public final class MenuItemListener implements Listener {

    private final Plugin plugin;
    private final NamespacedKey marker;
    private final Material material;
    private final int slot;
    private final String name;
    private final List<String> lore;
    private final String command;

    public MenuItemListener(@NotNull Plugin plugin) {
        this.plugin = plugin;
        this.marker = new NamespacedKey(plugin, "menu-item");
        var config = plugin.getConfig();
        String configured = config.getString("menu-item.material", "COMPASS");
        Material parsed = Material.matchMaterial(configured);
        this.material = parsed == null || !parsed.isItem() ? Material.COMPASS : parsed;
        this.slot = Math.max(0, Math.min(8, config.getInt("menu-item.slot", 8)));
        this.name = config.getString("menu-item.name", "<gold>เมนู</gold>");
        this.lore = config.getStringList("menu-item.lore");
        this.command = config.getString("menu-item.command", "rtpmenu");
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        // Inventory writes belong on the player's own region thread, one tick after the
        // join so a profile plugin that clears the inventory does not win the race.
        player.getScheduler().execute(plugin, () -> ensureItem(player), null, 1L);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != org.bukkit.inventory.EquipmentSlot.HAND) return;
        switch (event.getAction()) {
            case RIGHT_CLICK_AIR, RIGHT_CLICK_BLOCK -> { }
            default -> {
                return;
            }
        }
        ItemStack item = event.getItem();
        if (!isMenuItem(item)) return;
        event.setCancelled(true);
        // Runs as the player on the entity thread: the same path a typed command takes,
        // so permissions and DeluxeMenus' own handling stay identical.
        event.getPlayer().performCommand(command);
    }

    private boolean isMenuItem(ItemStack item) {
        if (item == null || item.getType() != material) return false;
        ItemMeta meta = item.getItemMeta();
        return meta != null && meta.getPersistentDataContainer()
                .has(marker, PersistentDataType.BYTE);
    }

    private void ensureItem(Player player) {
        if (!player.isOnline()) return;
        for (ItemStack existing : player.getInventory().getContents()) {
            if (isMenuItem(existing)) return;
        }
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return;
        meta.displayName(MiniMessage.miniMessage().deserialize(name));
        if (!lore.isEmpty()) {
            meta.lore(lore.stream().map(line -> MiniMessage.miniMessage().deserialize(line)).toList());
        }
        meta.getPersistentDataContainer().set(marker, PersistentDataType.BYTE, (byte) 1);
        item.setItemMeta(meta);

        int target = slot;
        ItemStack current = player.getInventory().getItem(target);
        if (current != null && current.getType() != Material.AIR) {
            target = player.getInventory().firstEmpty();
            if (target < 0 || target > 8) {
                // A full inventory is not a reason to drop an item on the ground.
                Plugins.logger().fine("No hotbar space for the menu item: " + player.getName());
                return;
            }
        }
        player.getInventory().setItem(target, item);
    }
}
