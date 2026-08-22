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
 * Converts CraftEngine configs.
 *
 * <p>CraftEngine namespaces content in the key itself ({@code default:ruby_sword}) and
 * groups by kind at the top level, which is closer to Kalo than to Oraxen:</p>
 *
 * <pre>
 * items:                             ruby_sword:
 *   default:ruby_sword:                type: item
 *     material: paper           →      model:
 *     data:                              sprite: "item/ruby_sword"
 *       item-model: default:ruby       java:
 *       display-name: "Ruby"             base_material: PAPER
 * </pre>
 *
 * <p>Written from the documented format, like the other importers, and it reports every
 * key it does not recognise so a mismatch is visible to whoever runs the import rather
 * than showing up as missing content later.</p>
 */
public final class CraftEngineImporter implements Importer {

    @Override
    public @NotNull String name() {
        return "CraftEngine";
    }

    @Override
    public int detect(@NotNull YamlConfiguration source) {
        // Kind-grouped sections whose child keys are namespaced — distinctive enough that
        // no other vendor here looks like it.
        for (String kind : List.of("items", "blocks")) {
            ConfigurationSection section = source.getConfigurationSection(kind);
            if (section == null) {
                continue;
            }
            for (String key : section.getKeys(false)) {
                if (key.indexOf(':') > 0) {
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

        convertSection(source.getConfigurationSection("items"), "item", output, report);
        int blocks = convertSection(source.getConfigurationSection("blocks"), "block", output, report);
        int furniture = convertSection(source.getConfigurationSection("furniture"), "furniture", output, report);
        BlockImportNotice.addTo(report, blocks + furniture);
        FurnitureImportNotice.addTo(report, furniture);

        convertRecipes(source, output, namespace, report);

        for (String other : List.of("sounds", "categories")) {
            if (source.contains(other)) {
                ConfigurationSection section = source.getConfigurationSection(other);
                int count = section != null ? section.getKeys(false).size() : 0;
                report.unsupported(other + " (" + count + " entr(y/ies); not imported from CraftEngine yet)");
            }
        }

        return output.saveToString();
    }

    private static int convertSection(ConfigurationSection section,
                                      @NotNull String kaloType,
                                      @NotNull YamlConfiguration output,
                                      @NotNull ImportReport report) {
        if (section == null) {
            return 0;
        }

        int converted = 0;
        for (String namespacedKey : section.getKeys(false)) {
            ConfigurationSection content = section.getConfigurationSection(namespacedKey);
            if (content == null) {
                continue;
            }

            // "default:ruby_sword" -> "ruby_sword"; the pack being imported into supplies
            // the namespace, so carrying CraftEngine's would double it up.
            int separator = namespacedKey.indexOf(':');
            String key = separator >= 0 ? namespacedKey.substring(separator + 1) : namespacedKey;

            try {
                output.createSection(key, convertContent(key, kaloType, content, report));
                report.imported(namespacedKey);
                converted++;
            } catch (Exception e) {
                report.failed(namespacedKey, e.getMessage() != null ? e.getMessage() : e.toString());
            }
        }
        return converted;
    }

    private static @NotNull Map<String, Object> convertContent(@NotNull String key,
                                                               @NotNull String kaloType,
                                                               @NotNull ConfigurationSection content,
                                                               @NotNull ImportReport report) {
        Map<String, Object> converted = new LinkedHashMap<>();
        converted.put("type", kaloType);

        if (kaloType.equals("block") || kaloType.equals("furniture")) {
            converted.put("java", Map.of("mode", "virtual"));
        }

        ConfigurationSection data = content.getConfigurationSection("data");

        Map<String, Object> display = new LinkedHashMap<>();
        String name = data != null ? data.getString("display-name") : null;
        if (name != null) {
            display.put("name", name);
        }
        List<String> lore = data != null ? data.getStringList("lore") : List.of();
        if (!lore.isEmpty()) {
            display.put("lore", lore);
        }
        if (!display.isEmpty()) {
            converted.put("display", display);
        }

        // CraftEngine already targets the item-model system, so the model reference maps
        // across directly rather than needing a CustomModelData translation.
        String itemModel = data != null ? data.getString("item-model") : null;
        if (itemModel != null) {
            String value = itemModel.contains(":")
                    ? itemModel.substring(itemModel.indexOf(':') + 1)
                    : itemModel;
            converted.put("model", Map.of(
                    kaloType.equals("block") ? "cube_all" : "sprite", value));
        }

        String material = content.getString("material");
        if (material != null && kaloType.equals("item")) {
            Material parsed = MigrationMaterials.item(material);
            converted.put("java", Map.of("base_material", parsed.name()));
        }

        reportUnknownKeys(key, content, data, report);

        return converted;
    }

    private static void reportUnknownKeys(@NotNull String key,
                                          @NotNull ConfigurationSection content,
                                          ConfigurationSection data,
                                          @NotNull ImportReport report) {
        for (String child : content.getKeys(false)) {
            switch (child) {
                case "material", "data" -> {
                    // Handled above.
                }
                case "behavior", "behaviors", "behaviour", "behaviours" ->
                        report.unsupported(key + "." + child + " (Kalo uses features; port these by hand)");
                case "entity", "hitbox", "sit", "seats", "rotation", "rotatable" ->
                        report.unsupported(key + "." + child + " (CraftEngine furniture features have no Kalo equivalent)");
                default -> report.unsupported(key + "." + child);
            }
        }
        if (data != null) {
            for (String child : data.getKeys(false)) {
                if (!List.of("item-model", "display-name", "lore").contains(child)) {
                    report.unsupported(key + ".data." + child);
                }
            }
        }
    }

    private static void convertRecipes(@NotNull YamlConfiguration source,
                                       @NotNull YamlConfiguration output,
                                       @NotNull String namespace,
                                       @NotNull ImportReport report) {
        ConfigurationSection recipes = source.getConfigurationSection("recipes");
        if (recipes == null) return;

        for (String key : recipes.getKeys(false)) {
            ConfigurationSection recipe = recipes.getConfigurationSection(key);
            if (recipe == null) continue;

            try {
                convertRecipe(key, recipe, output, report);
                report.imported(namespace + ":" + key);
            } catch (Exception e) {
                report.failed(key, e.getMessage() != null ? e.getMessage() : e.toString());
            }
        }
    }

    private static void convertRecipe(@NotNull String key,
                                      @NotNull ConfigurationSection recipe,
                                      @NotNull YamlConfiguration output,
                                      @NotNull ImportReport report) {
        String type = recipe.getString("type", "crafting_shaped");

        if (type.equals("crafting_shaped")) {
            List<String> shape = recipe.getStringList("shape");
            ConfigurationSection ingredients = recipe.getConfigurationSection("ingredients");
            String result = recipe.getString("result");
            int amount = recipe.getInt("amount", 1);

            if (result == null || ingredients == null || shape.isEmpty()) {
                throw new IllegalArgumentException("recipe missing shape, ingredients, or result");
            }

            Map<String, Object> converted = new LinkedHashMap<>();
            converted.put("type", "shaped");
            converted.put("shape", shape);
            converted.put("result", Map.of("key", result, "amount", amount));

            Map<String, Object> ingredientMap = new LinkedHashMap<>();
            for (String ingredientKey : ingredients.getKeys(false)) {
                ingredientMap.put(ingredientKey, ingredients.get(ingredientKey));
            }
            converted.put("ingredients", ingredientMap);

            output.createSection(key, converted);
        } else if (type.equals("crafting_shapeless")) {
            ConfigurationSection ingredients = recipe.getConfigurationSection("ingredients");
            String result = recipe.getString("result");
            int amount = recipe.getInt("amount", 1);

            if (result == null || ingredients == null) {
                throw new IllegalArgumentException("recipe missing ingredients or result");
            }

            Map<String, Object> converted = new LinkedHashMap<>();
            converted.put("type", "shapeless");
            converted.put("result", Map.of("key", result, "amount", amount));

            List<Map<String, Object>> ingredientList = new ArrayList<>();
            for (String ingredientKey : ingredients.getKeys(false)) {
                ingredientList.add(Map.of("key", ingredientKey));
            }
            converted.put("ingredients", ingredientList);

            output.createSection(key, converted);
        } else {
            report.unsupported(key + ".type = " + type + " (only crafting_shaped and crafting_shapeless are supported)");
        }
    }

    @Override
    public @NotNull List<File> assetDirectories(@NotNull File pluginFolder) {
        List<File> dirs = new ArrayList<>();
        File pack = new File(pluginFolder, "pack");
        File textures = new File(pack, "textures");
        if (textures.isDirectory()) dirs.add(textures);
        File models = new File(pack, "models");
        if (models.isDirectory()) dirs.add(models);
        return dirs;
    }
}
