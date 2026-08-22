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
 * Converts CrackShot weapon configs into Kalo definitions.
 *
 * <p>CrackShot stores weapons in {@code guns.yml} under a {@code Weapons:} section.
 * Each weapon has a material, display name, and shooting mechanics:</p>
 *
 * <pre>
 * Weapons:                           ruby_blaster:
 *   ruby_blaster:                      type: item
 *     Material: NETHERITE_SWORD   →    display:
 *     Displayname: '&lt;red&gt;Ruby         name: "&lt;red&gt;Ruby Blaster"
 *       Blaster'                      model:
 *     Lore:                             sprite: "item/ruby_blaster"
 *       - '&amp;7A powerful weapon'      java:
 *     Shoot-Key: LEFT_CLICK            base_material: NETHERITE_SWORD
 * </pre>
 *
 * <p>CrackShot's shooting mechanics, ammo system, and explosion effects are its own
 * runtime system with no Kalo equivalent — these are reported as unsupported.</p>
 *
 * <p><b>Caveat.</b> Written against the documented format, not validated against a corpus
 * of real packs. A first import is a draft to review.</p>
 */
public final class CrackShotImporter implements Importer {

    @Override
    public @NotNull String name() {
        return "CrackShot";
    }

    @Override
    public int detect(@NotNull YamlConfiguration source) {
        // CrackShot has a top-level "Weapons:" section
        ConfigurationSection weapons = source.getConfigurationSection("Weapons");
        if (weapons != null) {
            for (String key : weapons.getKeys(false)) {
                ConfigurationSection item = weapons.getConfigurationSection(key);
                if (item != null && item.contains("Material")) {
                    return 80;
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

        ConfigurationSection weapons = source.getConfigurationSection("Weapons");
        if (weapons == null) {
            return output.saveToString();
        }

        for (String key : weapons.getKeys(false)) {
            ConfigurationSection item = weapons.getConfigurationSection(key);
            if (item == null) continue;

            try {
                convertItem(key, item, output, report);
                report.imported(namespace + ":" + key);
            } catch (Exception e) {
                report.failed(key, e.getMessage() != null ? e.getMessage() : e.toString());
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
        String name = item.getString("Displayname");
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
            int cmd = item.getInt("Durability", 0);
            if (cmd > 0) {
                java.put("custom_model_data", cmd);
            }
            converted.put("java", java);
        }

        // Model
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
                case "Material", "Displayname", "Lore", "Durability" -> {
                    // Handled above.
                }
                case "Shoot-Key", "Shoot-Cooldown", "Shoot-Sound", "Shoot-Damage",
                     "Shoot-Range", "Shoot-Projectiles", "Shoot-Spread",
                     "Ammo-Item", "Ammo-Max", "Ammo-Refill" ->
                        report.unsupported(key + "." + child + " (CrackShot shooting mechanics have no Kalo equivalent)");
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
