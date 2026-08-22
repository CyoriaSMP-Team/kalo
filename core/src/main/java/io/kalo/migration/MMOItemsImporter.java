package io.kalo.migration;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Converts MMOItems configs into Kalo definitions.
 *
 * <p>MMOItems stores items in {@code items/} subfolders grouped by type (SWORD, AXE, etc.).
 * Each item has a display name, material, custom model data, and optional lore:</p>
 *
 * <pre>
 * SWORD:                             ruby_sword:
 *   ruby_sword:                        type: item
 *     Material: NETHERITE_SWORD   →    display:
 *     Display: &lt;red&gt;Ruby Sword           name: "&lt;red&gt;Ruby Sword"
 *     Lore:                             model:
 *       - "&amp;7A legendary sword"         sprite: "item/ruby_sword"
 *     Custom-Model-Data: 1001          java:
 *                                       base_material: NETHERITE_SWORD
 *                                       custom_model_data: 1001
 * </pre>
 *
 * <p>MMOItems abilities, stats, and skills are its own system with no mechanical
 * equivalent in Kalo features — these are reported as unsupported.</p>
 *
 * <p><b>Caveat.</b> Written against the documented format, not validated against a corpus
 * of real packs. A first import is a draft to review.</p>
 */
public final class MMOItemsImporter implements Importer {

    @Override
    public @NotNull String name() {
        return "MMOItems";
    }

    @Override
    public int detect(@NotNull YamlConfiguration source) {
        // MMOItems items are grouped by type (SWORD, AXE, etc.) at the top level
        for (String key : source.getKeys(false)) {
            ConfigurationSection section = source.getConfigurationSection(key);
            if (section == null) continue;
            // Check if this looks like an MMOItems type group
            for (String child : section.getKeys(false)) {
                ConfigurationSection item = section.getConfigurationSection(child);
                if (item == null) continue;
                if (item.contains("Material") && (item.contains("Display") || item.contains("Displayname"))) {
                    return 85;
                }
            }
        }
        return 0;
    }

    @Override
    public @NotNull String convert(@NotNull YamlConfiguration source,
                                   @NotNull String namespace,
                                   @NotNull ImportReport report) {
        YamlConfiguration output = new YamlConfiguration();

        for (String typeGroup : source.getKeys(false)) {
            ConfigurationSection group = source.getConfigurationSection(typeGroup);
            if (group == null) continue;

            for (String key : group.getKeys(false)) {
                ConfigurationSection item = group.getConfigurationSection(key);
                if (item == null) continue;

                try {
                    convertItem(key, item, output, report);
                    report.imported(namespace + ":" + key);
                } catch (Exception e) {
                    report.failed(key, e.getMessage() != null ? e.getMessage() : e.toString());
                }
            }
        }

        return output.saveToString();
    }

    private static void convertItem(@NotNull String key,
                                    @NotNull ConfigurationSection item,
                                    @NotNull YamlConfiguration output,
                                    @NotNull ImportReport report) {
        Map<String, Object> converted = new LinkedHashMap<>();
        converted.put("type", "item");

        // Display name
        Map<String, Object> display = new LinkedHashMap<>();
        String name = item.getString("Display", item.getString("Displayname"));
        if (name != null) {
            display.put("name", name);
        }
        List<String> lore = item.getStringList("Lore");
        if (!lore.isEmpty()) {
            display.put("lore", lore);
        }
        if (!display.isEmpty()) {
            converted.put("display", display);
        }

        // Material + custom model data
        String material = item.getString("Material");
        if (material != null) {
            Material parsed = MigrationMaterials.item(material);
            Map<String, Object> java = new LinkedHashMap<>();
            java.put("base_material", parsed.name());
            int cmd = item.getInt("Custom-Model-Data", 0);
            if (cmd > 0) {
                java.put("custom_model_data", cmd);
            }
            converted.put("java", java);
        }

        // Model: use custom-model-data based sprite
        if (material != null) {
            converted.put("model", Map.of("sprite", "item/" + key.toLowerCase()));
        }

        reportUnknownKeys(key, item, report);

        output.createSection(key, converted);
    }

    private static void reportUnknownKeys(@NotNull String key,
                                          @NotNull ConfigurationSection item,
                                          @NotNull ImportReport report) {
        for (String child : item.getKeys(false)) {
            switch (child) {
                case "Material", "Display", "Displayname", "Lore", "Custom-Model-Data" -> {
                    // Handled above.
                }
                case "Abilities", "Abilities-Blacklist", "Skill-Abilities", "Attribute" ->
                        report.unsupported(key + "." + child + " (MMOItems abilities/skills have no Kalo equivalent)");
                default -> report.unsupported(key + "." + child);
            }
        }
    }

    @Override
    public @NotNull List<File> assetDirectories(@NotNull File pluginFolder) {
        List<File> dirs = new ArrayList<>();
        File models = new File(pluginFolder, "models");
        if (models.isDirectory()) dirs.add(models);
        File textures = new File(pluginFolder, "textures");
        if (textures.isDirectory()) dirs.add(textures);
        return dirs;
    }
}
