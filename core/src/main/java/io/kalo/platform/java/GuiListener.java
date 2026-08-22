package io.kalo.platform.java;

import io.kalo.content.gui.Gui;
import io.kalo.content.gui.GuiDefinition;
import io.kalo.manager.RegistryManager;
import net.kyori.adventure.key.Key;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * Handles GUI interactions: slot clicks, close actions, and inventory protection.
 *
 * <p>Guis are server-side inventory menus that cannot be modified by players.
 * Clicks trigger configured actions, and closing the inventory triggers close_actions.</p>
 */
public final class GuiListener implements Listener {
    private static final Logger LOGGER = Logger.getLogger(GuiListener.class.getName());

    /** Tracks which players have which GUI open: player UUID -> GUI key */
    private final Map<UUID, Key> openGuis = new ConcurrentHashMap<>();

    /** Cached GUI lookup by key */
    private final Map<String, Gui> guiByKey = new ConcurrentHashMap<>();

    public GuiListener() {
        rebuildLookup();
    }

    /**
     * Rebuilds the GUI lookup from registries.
     */
    public void rebuildLookup() {
        guiByKey.clear();
        try {
            RegistryManager.GlobalRegistries registries = RegistryManager.GlobalRegistries.registries();
            registries.gui().entries().forEach(entry ->
                    guiByKey.put(entry.key().asString(), entry.value()));
        } catch (RuntimeException ignored) {
            // Constructor can run before global registries have finished their first load.
        }
    }

    /**
     * Opens a GUI for a player.
     *
     * @param player the player
     * @param guiKey the GUI content key
     */
    public void openGui(@NotNull Player player, @NotNull Key guiKey) {
        Gui gui = guiByKey.get(guiKey.asString());
        if (gui == null) {
            LOGGER.warning("GUI '" + guiKey.asString() + "' not found");
            return;
        }

        GuiDefinition definition = gui.guiDefinition();
        Inventory inventory = Bukkit.createInventory(
                new GuiHolder(guiKey),
                definition.rows() * 9,
                MiniMessage.miniMessage().deserialize(definition.title())
        );

        // Populate slots
        for (GuiDefinition.SlotConfig slotConfig : definition.items()) {
            if (slotConfig.slot() < 0 || slotConfig.slot() >= inventory.getSize()) {
                continue;
            }

            ItemStack item = createItem(slotConfig);
            inventory.setItem(slotConfig.slot(), item);
        }

        player.openInventory(inventory);
        openGuis.put(player.getUniqueId(), guiKey);
    }

    /**
     * Creates an ItemStack from a slot configuration.
     */
    private @NotNull ItemStack createItem(@NotNull GuiDefinition.SlotConfig slotConfig) {
        // Parse material
        org.bukkit.Material material = parseMaterial(slotConfig.material());
        if (material == null) {
            material = org.bukkit.Material.PAPER;
        }

        ItemStack item = new ItemStack(material, slotConfig.amount());
        ItemMeta meta = item.getItemMeta();

        if (meta != null) {
            if (slotConfig.displayName() != null) {
                meta.displayName(MiniMessage.miniMessage().deserialize(slotConfig.displayName()));
            }

            if (!slotConfig.lore().isEmpty()) {
                meta.lore(slotConfig.lore().stream()
                        .map(line -> MiniMessage.miniMessage().deserialize(line))
                        .toList());
            }

            if (slotConfig.customModelData() > 0) {
                meta.setCustomModelData(slotConfig.customModelData());
            }

            item.setItemMeta(meta);
        }

        return item;
    }

    /**
     * Parses a material string to a Bukkit Material.
     */
    private @Nullable org.bukkit.Material parseMaterial(@NotNull String materialStr) {
        try {
            return org.bukkit.Material.valueOf(materialStr.toUpperCase());
        } catch (IllegalArgumentException e) {
            // Not a vanilla material - could be a custom item key
            // TODO: Support custom Kalo items in GUIs
            return null;
        }
    }

    /**
     * Handles inventory click events for GUIs.
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onInventoryClick(@NotNull InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof GuiHolder holder)) {
            return;
        }

        Player player = (Player) event.getWhoClicked();
        Key guiKey = holder.guiKey();
        Gui gui = guiByKey.get(guiKey.asString());

        if (gui == null) {
            return;
        }

        // Cancel the click to prevent moving items
        event.setCancelled(true);

        // Find the slot config for the clicked slot
        int slot = event.getRawSlot();
        if (slot < 0 || slot >= event.getInventory().getSize()) {
            return;
        }

        GuiDefinition definition = gui.guiDefinition();
        for (GuiDefinition.SlotConfig slotConfig : definition.items()) {
            if (slotConfig.slot() == slot) {
                executeActions(player, slotConfig.actions());
                break;
            }
        }
    }

    /**
     * Handles inventory drag events to prevent item movement.
     */
    @EventHandler
    public void onInventoryDrag(@NotNull InventoryDragEvent event) {
        if (event.getInventory().getHolder() instanceof GuiHolder) {
            event.setCancelled(true);
        }
    }

    /**
     * Handles inventory close events to execute close actions.
     */
    @EventHandler
    public void onInventoryClose(@NotNull InventoryCloseEvent event) {
        if (!(event.getInventory().getHolder() instanceof GuiHolder holder)) {
            return;
        }

        if (!(event.getPlayer() instanceof Player player)) {
            return;
        }

        Key guiKey = holder.guiKey();
        openGuis.remove(player.getUniqueId());

        Gui gui = guiByKey.get(guiKey.asString());
        if (gui == null) {
            return;
        }

        GuiDefinition definition = gui.guiDefinition();
        if (definition.closeActions() != null) {
            executeActions(player, definition.closeActions());
        }
    }

    /**
     * Executes a list of actions for a player.
     *
     * <p>Action formats:</p>
     * <ul>
     *   <li>{@code command:/somecommand} - run a command</li>
     *   <li>{@code message:<text>} - send a message</li>
     *   <li>{@code close} - close the inventory</li>
     *   <li>{@code gui:<key>} - open another GUI</li>
     * </ul>
     */
    private void executeActions(@NotNull Player player, @NotNull java.util.List<String> actions) {
        for (String action : actions) {
            if (action.startsWith("command:")) {
                String command = action.substring(8);
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command.replace("%player%", player.getName()));
            } else if (action.startsWith("message:")) {
                String message = action.substring(8);
                player.sendMessage(MiniMessage.miniMessage().deserialize(message));
            } else if (action.equals("close")) {
                player.closeInventory();
            } else if (action.startsWith("gui:")) {
                String guiKeyStr = action.substring(4);
                try {
                    Key guiKey = Key.key(guiKeyStr);
                    openGui(player, guiKey);
                } catch (Exception e) {
                    LOGGER.warning("Invalid GUI key: " + guiKeyStr);
                }
            }
        }
    }

    /**
     * Custom InventoryHolder to identify GUI inventories.
     */
    public static final class GuiHolder implements InventoryHolder {
        private final Key guiKey;

        public GuiHolder(@NotNull Key guiKey) {
            this.guiKey = guiKey;
        }

        public @NotNull Key guiKey() {
            return guiKey;
        }

        @Override
        public @NotNull Inventory getInventory() {
            throw new UnsupportedOperationException("GuiHolder is not a real holder");
        }
    }
}
