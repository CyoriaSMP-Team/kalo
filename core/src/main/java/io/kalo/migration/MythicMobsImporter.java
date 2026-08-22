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
 * Converts MythicMobs item configs into Kalo definitions.
 *
 * <p>MythicMobs stores custom items in {@code/items/} and mobs in {@code/mobs/}.
 * This importer handles the items section only — mobs are MythicMobs' own system
 * with no Kalo equivalent:</p>
 *
 * <pre>
 * ruby_sword:                        ruby_sword:
 *   Material: NETHERITE_SWORD         type: item
 *   Display: '&lt;red&gt;Ruby Sword'   →    display:
 *   Lore:                               name: "&lt;red&gt;Ruby Sword"
 *     - '&amp;7A legendary sword'       model:
 *   Options:                            sprite: "item/ruby_sword"
 *     HideAttributes: true             java:
 *                                       base_material: NETHERITE_SWORD
 * </pre>
 *
 * <p>MythicMobs skills, mechanics, and mob definitions are reported as unsupported
 * since they are runtime behaviours with no static Kalo equivalent.</p>
 *
 * <p><b>Caveat.</b> Written against the documented format, not validated against a corpus
 * of real packs. A first import is a draft to review.</p>
 */
public final class MythicMobsImporter implements Importer {

    @Override
    public @NotNull String name() {
        return "MythicMobs";
    }

    @Override
    public int detect(@NotNull YamlConfiguration source) {
        // MythicMobs items have Material at top level under a named key
        for (String key : source.getKeys(false)) {
            ConfigurationSection section = source.getConfigurationSection(key);
            if (section == null) continue;
            if (section.contains("Material") && (section.contains("Display") || section.contains("Displayname"))) {
                return 80;
            }
        }
        return 0;
    }

    @Override
    public @NotNull String convert(@NotNull YamlConfiguration source,
                                   @NotNull String namespace,
                                   @NotNull ImportReport report) {
        YamlConfiguration output = new YamlConfiguration();

        for (String key : source.getKeys(false)) {
            ConfigurationSection item = source.getConfigurationSection(key);
            if (item == null) continue;

            // Skip mob definitions — they have 'Type' or 'Health' instead of 'Material'
            if (item.contains("Type") || item.contains("Health")) {
                report.unsupported(key + " (MythicMobs mob definition — not a Kalo content type)");
                continue;
            }

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
            ConfigurationSection options = item.getConfigurationSection("Options");
            if (options != null && options.contains("CustomModelData")) {
                java.put("custom_model_data", options.getInt("CustomModelData"));
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
                case "Material", "Display", "Displayname", "Lore", "Options" -> {
                    // Handled above.
                }
                case "Skills", "Mechanics", "AggroRange", "FollowRange", "Damage",
                     "Health", "Armor", "KnockbackResistance" ->
                        report.unsupported(key + "." + child + " (MythicMobs mob mechanics have no Kalo equivalent)");
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
