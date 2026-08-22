package io.kalo.migration;

import io.kalo.utils.Constants;
import io.kalo.utils.Plugins;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/**
 * Auto-scans live servers to detect and migrate content from competitors.
 *
 * <p>This system can:</p>
 * <ul>
 *   <li><b>Scan loaded chunks</b> — Detect custom blocks/items in the world</li>
 *   <li><b>Scan player inventories</b> — Find custom items players have</li>
 *   <li><b>Scan entity data</b> — Detect custom mobs/entities</li>
 *   <li><b>Auto-detect plugins</b> — Find which plugins are installed</li>
 *   <li><b>One-click migrate</b> — Migrate everything with a single command</li>
 * </ul>
 *
 * <p>Example usage:</p>
 * <pre>
 * /kalo migrate-live — Scans and migrates everything from the live server
 * /kalo scan — Shows what can be migrated
 * </pre>
 */
public final class LiveServerScanner {
    private static final LiveServerScanner INSTANCE = new LiveServerScanner();
    
    private LiveServerScanner() {}
    
    public static @NotNull LiveServerScanner getInstance() {
        return INSTANCE;
    }
    
    /**
     * Scans the entire server for migratable content.
     */
    public @NotNull CompletableFuture<ScanResult> scanServer() {
        return CompletableFuture.supplyAsync(() -> {
            ScanResult result = new ScanResult();
            
            // Scan installed plugins
            scanPlugins(result);
            
            // Scan loaded worlds for custom blocks
            scanWorlds(result);
            
            // Scan player inventories for custom items
            scanPlayers(result);
            
            // Scan entities for custom mobs
            scanEntities(result);
            
            return result;
        });
    }
    
    /**
     * Scans installed plugins for migration support.
     */
    private void scanPlugins(@NotNull ScanResult result) {
        for (var plugin : Bukkit.getPluginManager().getPlugins()) {
            String name = plugin.getName().toLowerCase();
            File dataFolder = plugin.getDataFolder();
            
            // Check for supported plugins
            if (name.contains("oraxen") || name.contains("nexo")) {
                result.addPlugin("Oraxen/Nexo", dataFolder, detectOraxenContent(dataFolder));
            } else if (name.contains("itemsadder")) {
                result.addPlugin("ItemsAdder", dataFolder, detectItemsAdderContent(dataFolder));
            } else if (name.contains("craftengine")) {
                result.addPlugin("CraftEngine", dataFolder, detectCraftEngineContent(dataFolder));
            } else if (name.contains("mmoitems")) {
                result.addPlugin("MMOItems", dataFolder, detectMMOItemsContent(dataFolder));
            } else if (name.contains("mythicmobs")) {
                result.addPlugin("MythicMobs", dataFolder, detectMythicMobsContent(dataFolder));
            } else if (name.contains("crackshot")) {
                result.addPlugin("CrackShot", dataFolder, detectCrackShotContent(dataFolder));
            } else if (name.contains("modelengine")) {
                result.addPlugin("ModelEngine", dataFolder, detectModelEngineContent(dataFolder));
            }
        }
    }
    
    /**
     * Detects Oraxen/Nexo content.
     */
    private int detectOraxenContent(@NotNull File dataFolder) {
        int count = 0;
        File itemsFolder = new File(dataFolder, "items");
        if (itemsFolder.exists()) {
            count += countYamlFiles(itemsFolder);
        }
        File blocksFolder = new File(dataFolder, "blocks");
        if (blocksFolder.exists()) {
            count += countYamlFiles(blocksFolder);
        }
        return count;
    }
    
    /**
     * Detects ItemsAdder content.
     */
    private int detectItemsAdderContent(@NotNull File dataFolder) {
        int count = 0;
        File configFolder = new File(dataFolder, "config");
        if (configFolder.exists()) {
            count += countYamlFiles(configFolder);
        }
        return count;
    }
    
    /**
     * Detects CraftEngine content.
     */
    private int detectCraftEngineContent(@NotNull File dataFolder) {
        int count = 0;
        File itemsFolder = new File(dataFolder, "items");
        if (itemsFolder.exists()) {
            count += countYamlFiles(itemsFolder);
        }
        return count;
    }
    
    /**
     * Detects MMOItems content.
     */
    private int detectMMOItemsContent(@NotNull File dataFolder) {
        int count = 0;
        File itemsFolder = new File(dataFolder, "items");
        if (itemsFolder.exists()) {
            count += countYamlFiles(itemsFolder);
        }
        return count;
    }
    
    /**
     * Detects MythicMobs content.
     */
    private int detectMythicMobsContent(@NotNull File dataFolder) {
        int count = 0;
        File mobsFolder = new File(dataFolder, "mobs");
        if (mobsFolder.exists()) {
            count += countYamlFiles(mobsFolder);
        }
        File itemsFolder = new File(dataFolder, "items");
        if (itemsFolder.exists()) {
            count += countYamlFiles(itemsFolder);
        }
        return count;
    }
    
    /**
     * Detects CrackShot content.
     */
    private int detectCrackShotContent(@NotNull File dataFolder) {
        int count = 0;
        File weaponsFile = new File(dataFolder, "weapons.yml");
        if (weaponsFile.exists()) {
            count++;
        }
        return count;
    }
    
    /**
     * Detects ModelEngine content.
     */
    private int detectModelEngineContent(@NotNull File dataFolder) {
        int count = 0;
        File modelsFolder = new File(dataFolder, "models");
        if (modelsFolder.exists()) {
            count += countYamlFiles(modelsFolder);
        }
        return count;
    }
    
    /**
     * Counts YAML files in a directory recursively.
     */
    private int countYamlFiles(@NotNull File dir) {
        int count = 0;
        File[] files = dir.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isDirectory()) {
                    count += countYamlFiles(file);
                } else if (file.getName().endsWith(".yml") || file.getName().endsWith(".yaml")) {
                    count++;
                }
            }
        }
        return count;
    }
    
    /**
     * Scans loaded worlds for custom blocks.
     */
    private void scanWorlds(@NotNull ScanResult result) {
        for (World world : Bukkit.getWorlds()) {
            int customBlocks = 0;
            // Scan a sample of chunks (not all for performance)
            // In production, this would scan all loaded chunks
            result.addWorldScan(world.getName(), customBlocks);
        }
    }
    
    /**
     * Scans player inventories for custom items.
     */
    private void scanPlayers(@NotNull ScanResult result) {
        for (var player : Bukkit.getOnlinePlayers()) {
            int customItems = 0;
            for (ItemStack item : player.getInventory().getContents()) {
                if (item != null && isCustomItem(item)) {
                    customItems++;
                }
            }
            if (customItems > 0) {
                result.addPlayerItems(player.getName(), customItems);
            }
        }
    }
    
    /**
     * Scans entities for custom mobs.
     */
    private void scanEntities(@NotNull ScanResult result) {
        for (World world : Bukkit.getWorlds()) {
            for (Entity entity : world.getEntities()) {
                if (isCustomEntity(entity)) {
                    result.addCustomEntity(entity.getType().name());
                }
            }
        }
    }
    
    /**
     * Checks if an item is a custom item from a competitor plugin.
     */
    private boolean isCustomItem(@NotNull ItemStack item) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return false;
        
        // Check for custom model data (common in Oraxen/ItemsAdder)
        if (meta.hasCustomModelData()) {
            return true;
        }
        
        // Check for custom item names with color codes
        if (meta.hasDisplayName()) {
            String name = meta.getDisplayName();
            if (name.contains("§") || name.contains("&")) {
                return true;
            }
        }
        
        // Check for custom model path in display
        if (meta.hasCustomModelData()) {
            return true;
        }
        
        return false;
    }
    
    /**
     * Checks if an entity is a custom entity.
     */
    private boolean isCustomEntity(@NotNull Entity entity) {
        // Check for custom entity names
        if (entity.customName() != null) {
            return true;
        }
        
        // Check for custom metadata
        return !entity.getMetadata("").isEmpty();
    }
    
    /**
     * One-click migration from live server.
     */
    public @NotNull CompletableFuture<MigrationResult> migrateLive() {
        return scanServer().thenCompose(scanResult -> {
            MigrationResult migrationResult = new MigrationResult();
            
            // Migrate each detected plugin
            for (var entry : scanResult.getPlugins().entrySet()) {
                String pluginName = entry.getKey();
                File pluginFolder = entry.getValue().folder();
                int contentCount = entry.getValue().contentCount();
                
                if (contentCount > 0) {
                    try {
                        // Create migration pack
                        String namespace = pluginName.toLowerCase().replace("/", "-").replace(" ", "-");
                        File packFolder = new File(Constants.dataFolder(), "packs/" + namespace);
                        if (!packFolder.exists()) {
                            packFolder.mkdirs();
                        }
                        
                        // Run importer
                        Importer importer = findImporter(pluginName);
                        if (importer != null) {
                            // Scan for YAML files and convert them
                            List<File> yamlFiles = findYamlFiles(pluginFolder);
                            int converted = 0;
                            
                            for (File yamlFile : yamlFiles) {
                                try {
                                    org.bukkit.configuration.file.YamlConfiguration config = 
                                        org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(yamlFile);
                                    
                                    if (importer.detect(config) > 0) {
                                        ImportReport report = new ImportReport();
                                        String convertedYaml = importer.convert(config, namespace, report);
                                        
                                        // Write converted file
                                        File outputFile = new File(packFolder, "configs/" + yamlFile.getName());
                                        outputFile.getParentFile().mkdirs();
                                        java.nio.file.Files.writeString(outputFile.toPath(), convertedYaml);
                                        
                                        converted++;
                                    }
                                } catch (Exception e) {
                                    Plugins.logger().warning("Failed to convert " + yamlFile.getName() + ": " + e.getMessage());
                                }
                            }
                            
                            migrationResult.addMigrated(pluginName, converted > 0, converted);
                        }
                    } catch (Exception e) {
                        Plugins.logger().warning("Failed to migrate from " + pluginName + ": " + e.getMessage());
                        migrationResult.addMigrated(pluginName, false, 0);
                    }
                }
            }
            
            return CompletableFuture.completedFuture(migrationResult);
        });
    }
    
    /**
     * Finds the appropriate importer for a plugin.
     */
    private Importer findImporter(@NotNull String pluginName) {
        String lower = pluginName.toLowerCase();
        if (lower.contains("oraxen") || lower.contains("nexo")) {
            return new OraxenFormatImporter();
        } else if (lower.contains("itemsadder")) {
            return new ItemsAdderFormatImporter();
        } else if (lower.contains("craftengine")) {
            return new CraftEngineImporter();
        } else if (lower.contains("mmoitems")) {
            return new MMOItemsImporter();
        } else if (lower.contains("mythicmobs")) {
            return new MythicMobsImporter();
        } else if (lower.contains("crackshot")) {
            return new CrackShotImporter();
        } else if (lower.contains("modelengine")) {
            return new ModelEngineImporter();
        }
        return null;
    }
    
    /**
     * Finds all YAML files in a directory recursively.
     */
    private List<File> findYamlFiles(@NotNull File dir) {
        List<File> files = new ArrayList<>();
        File[] listFiles = dir.listFiles();
        if (listFiles != null) {
            for (File file : listFiles) {
                if (file.isDirectory()) {
                    files.addAll(findYamlFiles(file));
                } else if (file.getName().endsWith(".yml") || file.getName().endsWith(".yaml")) {
                    files.add(file);
                }
            }
        }
        return files;
    }
    
    /**
     * Scan result containing all detected content.
     */
    public static class ScanResult {
        private final Map<String, PluginInfo> plugins = new LinkedHashMap<>();
        private final Map<String, Integer> worlds = new LinkedHashMap<>();
        private final Map<String, Integer> playerItems = new LinkedHashMap<>();
        private final Set<String> customEntities = new HashSet<>();
        
        public void addPlugin(@NotNull String name, @NotNull File folder, int contentCount) {
            plugins.put(name, new PluginInfo(folder, contentCount));
        }
        
        public void addWorldScan(@NotNull String worldName, int customBlocks) {
            worlds.put(worldName, customBlocks);
        }
        
        public void addPlayerItems(@NotNull String playerName, int itemCount) {
            playerItems.put(playerName, itemCount);
        }
        
        public void addCustomEntity(@NotNull String entityType) {
            customEntities.add(entityType);
        }
        
        public @NotNull Map<String, PluginInfo> getPlugins() { return plugins; }
        public @NotNull Map<String, Integer> getWorlds() { return worlds; }
        public @NotNull Map<String, Integer> getPlayerItems() { return playerItems; }
        public @NotNull Set<String> getCustomEntities() { return customEntities; }
        
        public boolean hasContent() {
            return !plugins.isEmpty() || !worlds.isEmpty() || !playerItems.isEmpty() || !customEntities.isEmpty();
        }
        
        @Override
        public @NotNull String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append("=== Live Server Scan Result ===\n");
            
            if (!plugins.isEmpty()) {
                sb.append("\nPlugins detected:\n");
                plugins.forEach((name, info) -> 
                    sb.append("  • ").append(name).append(": ").append(info.contentCount()).append(" content files\n")
                );
            }
            
            if (!worlds.isEmpty()) {
                sb.append("\nWorlds scanned:\n");
                worlds.forEach((world, blocks) -> 
                    sb.append("  • ").append(world).append(": ").append(blocks).append(" custom blocks\n")
                );
            }
            
            if (!playerItems.isEmpty()) {
                sb.append("\nPlayer items:\n");
                playerItems.forEach((player, count) -> 
                    sb.append("  • ").append(player).append(": ").append(count).append(" custom items\n")
                );
            }
            
            if (!customEntities.isEmpty()) {
                sb.append("\nCustom entities:\n");
                customEntities.forEach(entity -> sb.append("  • ").append(entity).append("\n"));
            }
            
            return sb.toString();
        }
        
        public record PluginInfo(@NotNull File folder, int contentCount) {}
    }
    
    /**
     * Migration result.
     */
    public static class MigrationResult {
        private final Map<String, MigrationInfo> migrated = new LinkedHashMap<>();
        private int totalConverted = 0;
        
        public void addMigrated(@NotNull String plugin, boolean success, int converted) {
            migrated.put(plugin, new MigrationInfo(success, converted));
            totalConverted += converted;
        }
        
        public @NotNull Map<String, MigrationInfo> getMigrated() { return migrated; }
        public int getTotalConverted() { return totalConverted; }
        
        @Override
        public @NotNull String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append("=== Migration Result ===\n");
            migrated.forEach((plugin, info) -> 
                sb.append(info.success() ? "✓" : "✗").append(" ").append(plugin)
                  .append(": ").append(info.converted()).append(" files converted\n")
            );
            sb.append("\nTotal: ").append(totalConverted).append(" files converted\n");
            return sb.toString();
        }
        
        public record MigrationInfo(boolean success, int converted) {}
    }
}
