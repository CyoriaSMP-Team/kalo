package io.kalo.platform.java;

import io.kalo.utils.Constants;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * Persistent storage for furniture inventories.
 *
 * <p>Handles saving and restoring inventory contents for furniture pieces with
 * storage configuration. Inventories are saved to disk on close and loaded
 * when opened, keyed by world UID and block coordinates.</p>
 */
public final class FurnitureStorage {
    private static final Logger LOGGER = Logger.getLogger(FurnitureStorage.class.getName());

    private final File storageFolder;
    private final Map<String, Inventory> openInventories = new ConcurrentHashMap<>();

    public FurnitureStorage() {
        this.storageFolder = new File(Constants.dataFolder(), "furniture-storage");
        if (!storageFolder.exists()) {
            storageFolder.mkdirs();
        }
    }

    /**
     * Opens a storage inventory for a player at the given location.
     *
     * @param player the player opening the inventory
     * @param worldUid the world UID
     * @param x block X coordinate
     * @param y block Y coordinate
     * @param z block Z coordinate
     * @param rows number of inventory rows (1-6)
     * @param title display title
     */
    public void openStorage(@NotNull Player player, @NotNull UUID worldUid,
                           int x, int y, int z, int rows, @Nullable String title) {
        String key = storageKey(worldUid, x, y, z);
        Inventory inventory = openInventories.get(key);

        if (inventory == null) {
            String displayName = title != null ? title : "Storage";
            inventory = Bukkit.createInventory(null, rows * 9, displayName);

            // Load saved contents
            ItemStack[] contents = loadContents(key);
            if (contents != null) {
                inventory.setContents(contents);
            }

            openInventories.put(key, inventory);
        }

        player.openInventory(inventory);
    }

    /**
     * Saves the inventory contents to disk.
     *
     * @param worldUid the world UID
     * @param x block X coordinate
     * @param y block Y coordinate
     * @param z block Z coordinate
     * @param inventory the inventory to save
     */
    public void saveInventory(@NotNull UUID worldUid, int x, int y, int z,
                             @NotNull Inventory inventory) {
        String key = storageKey(worldUid, x, y, z);
        saveContents(key, inventory.getContents());
    }

    /**
     * Closes and saves all open inventories.
     */
    public void saveAll() {
        openInventories.forEach((key, inventory) -> saveContents(key, inventory.getContents()));
        openInventories.clear();
    }

    /**
     * Gets the storage file for a given key.
     */
    private @NotNull File storageFile(@NotNull String key) {
        return new File(storageFolder, key + ".yml");
    }

    /**
     * Generates a storage key from world UID and coordinates.
     */
    private static @NotNull String storageKey(@NotNull UUID worldUid, int x, int y, int z) {
        return worldUid + "_" + x + "_" + y + "_" + z;
    }

    /**
     * Loads inventory contents from disk.
     *
     * @return the saved contents, or null if no save exists
     */
    private @Nullable ItemStack[] loadContents(@NotNull String key) {
        File file = storageFile(key);
        if (!file.exists()) {
            return null;
        }

        try {
            YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
            ItemStack[] contents = new ItemStack[54]; // Max size (6 rows * 9)
            boolean hasContents = false;

            for (int i = 0; i < contents.length; i++) {
                if (config.contains("items." + i)) {
                    contents[i] = config.getItemStack("items." + i);
                    hasContents = true;
                }
            }

            return hasContents ? contents : null;
        } catch (Exception e) {
            LOGGER.warning("Failed to load furniture storage from " + file.getName() + ": " + e.getMessage());
            return null;
        }
    }

    /**
     * Saves inventory contents to disk.
     */
    private void saveContents(@NotNull String key, @NotNull ItemStack[] contents) {
        File file = storageFile(key);
        YamlConfiguration config = new YamlConfiguration();

        boolean hasContents = false;
        for (int i = 0; i < contents.length; i++) {
            if (contents[i] != null) {
                config.set("items." + i, contents[i]);
                hasContents = true;
            }
        }

        if (!hasContents) {
            // Delete empty storage files
            if (file.exists()) {
                file.delete();
            }
            return;
        }

        try {
            config.save(file);
        } catch (IOException e) {
            LOGGER.warning("Failed to save furniture storage to " + file.getName() + ": " + e.getMessage());
        }
    }
}
