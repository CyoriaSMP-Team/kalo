package io.kalo.migration;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Converts ItemsAdder content files into Kalo definitions.
 *
 * <p>ItemsAdder nests differently from Oraxen: the namespace is declared in the file
 * rather than taken from the folder, and content sits under an {@code items:} map.</p>
 *
 * <pre>
 * info:                              ruby_sword:
 *   namespace: mypack                  type: item
 * items:                        →      display:
 *   ruby_sword:                          name: "Ruby Sword"
 *     display_name: "Ruby Sword"       model:
 *     resource:                          sprite: "item/ruby_sword"
 *       material: NETHERITE_SWORD      java:
 *       generate: true                   base_material: NETHERITE_SWORD
 *       textures:
 *         - item/ruby_sword.png
 * </pre>
 *
 * <p>As with {@link OraxenImporter}, anything the importer cannot express is reported
 * rather than approximated. ItemsAdder's {@code behaviours} are its own behaviour system
 * and have no mechanical equivalent in Kalo's features.</p>
 *
 * <p><b>Caveat.</b> Written against the documented format, not validated against a corpus
 * of real packs. A first import is a draft to review.</p>
 */
public final class ItemsAdderImporter {

    private static final Set<String> KNOWN_ITEM_KEYS = Set.of(
            "display_name", "displayname", "lore", "resource", "durability",
            "permission", "enable_light", "specific_properties"
    );

    private ItemsAdderImporter() {
    }

    /**
     * @return the namespace declared in the file, or {@code null} if it has none — which
     *         is how an ItemsAdder file is told apart from an Oraxen one
     */
    public static @Nullable String namespaceOf(@NotNull YamlConfiguration source) {
        ConfigurationSection info = source.getConfigurationSection("info");
        return info != null ? info.getString("namespace") : null;
    }

    /** Whether this looks like an ItemsAdder file at all. */
    public static boolean looksLikeItemsAdder(@NotNull YamlConfiguration source) {
        return source.contains("info") || source.contains("items");
    }

    public static @NotNull String convert(@NotNull YamlConfiguration source, @NotNull ImportReport report) {
        YamlConfiguration output = new YamlConfiguration();

        String namespace = namespaceOf(source);
        if (namespace == null) {
            report.warn("No info.namespace in the source; content keys will take the target pack's namespace");
        }

        ConfigurationSection items = source.getConfigurationSection("items");
        if (items != null) {
            for (String key : items.getKeys(false)) {
                ConfigurationSection item = items.getConfigurationSection(key);
                if (item == null) {
                    continue;
                }
                try {
                    convertItem(key, item, output, report);
                    report.imported((namespace != null ? namespace : "imported") + ":" + key);
                } catch (Exception e) {
                    report.failed(key, e.getMessage() != null ? e.getMessage() : e.toString());
                }
            }
        }

        // Not an early return when items is absent: a blocks-only file is perfectly
        // normal in ItemsAdder, and bailing here would import nothing from it.
        int blocks = convertBlocks(source, output, namespace, report);
        int furniture = convertFurniture(source, output, namespace, report);
        int recipes = convertRecipes(source, output, namespace, report);

        if (items == null && blocks == 0 && furniture == 0 && recipes == 0) {
            report.warn("No items, blocks, furniture or recipes section found — nothing to import");
        }
        BlockImportNotice.addTo(report, blocks + furniture);
        FurnitureImportNotice.addTo(report, furniture);

        // Whatever is left still has no importer; saying so beats letting someone think
        // an empty result means they had none.
        reportUnconvertedSections(source, report);

        return output.saveToString();
    }

    private static void convertItem(@NotNull String key,
                                    @NotNull ConfigurationSection item,
                                    @NotNull YamlConfiguration output,
                                    @NotNull ImportReport report) {
        Map<String, Object> converted = new LinkedHashMap<>();
        converted.put("type", "item");

        Map<String, Object> display = new LinkedHashMap<>();
        String name = item.getString("display_name", item.getString("displayname"));
        if (name != null) {
            if (name.indexOf('&') >= 0 || name.indexOf('§') >= 0) {
                report.warn("'" + key + "' uses legacy colour codes in its name; rewrite as MiniMessage");
            }
            display.put("name", name);
        }
        List<String> lore = item.getStringList("lore");
        if (!lore.isEmpty()) {
            display.put("lore", lore);
        }
        if (!display.isEmpty()) {
            converted.put("display", display);
        }

        ConfigurationSection resource = item.getConfigurationSection("resource");
        Map<String, Object> model = convertModel(key, resource, report);
        if (model != null) {
            converted.put("model", model);
        }

        // ItemsAdder puts the base material inside `resource`, unlike Oraxen.
        String material = resource != null ? resource.getString("material") : null;
        if (material != null) {
            Material parsed = Material.matchMaterial(material.toUpperCase(Locale.ROOT));
            if (parsed == null) {
                throw new IllegalArgumentException("unknown material '" + material + "'");
            }
            converted.put("java", Map.of("base_material", parsed.name()));
        }

        Map<String, Object> behaviour = new LinkedHashMap<>();
        ConfigurationSection durability = item.getConfigurationSection("durability");
        if (durability != null && durability.contains("max_custom_durability")) {
            behaviour.put("durability", durability.getInt("max_custom_durability"));
        }
        if (!behaviour.isEmpty()) {
            converted.put("behaviour", behaviour);
        }

        reportUnknownKeys(key, item, report);

        output.createSection(key, converted);
    }

    private static @Nullable Map<String, Object> convertModel(@NotNull String key,
                                                              @Nullable ConfigurationSection resource,
                                                              @NotNull ImportReport report) {
        if (resource == null) {
            return null;
        }

        String modelPath = resource.getString("model_path");
        if (modelPath != null) {
            return Map.of("custom", OraxenImporter.stripExtension(modelPath));
        }

        List<String> textures = resource.getStringList("textures");
        if (textures.isEmpty()) {
            String texture = resource.getString("texture");
            if (texture != null) {
                textures = List.of(texture);
            }
        }
        if (textures.isEmpty()) {
            return null;
        }
        if (textures.size() > 1) {
            report.unsupported(key + ".resource.textures (" + textures.size()
                    + " layers; only the first is used)");
        }

        return Map.of("sprite", OraxenImporter.stripExtension(textures.get(0)));
    }

    private static void reportUnknownKeys(@NotNull String key,
                                          @NotNull ConfigurationSection item,
                                          @NotNull ImportReport report) {
        for (String child : item.getKeys(false)) {
            if (KNOWN_ITEM_KEYS.contains(child)) {
                continue;
            }
            if (child.equals("behaviours") || child.equals("behaviors")) {
                report.unsupported(key + ".behaviours (Kalo uses features; port these by hand)");
                continue;
            }
            report.unsupported(key + "." + child);
        }
    }

    /** ItemsAdder keeps blocks in their own section rather than as items with a mechanic. */
    private static int convertBlocks(@NotNull YamlConfiguration source,
                                     @NotNull YamlConfiguration output,
                                     @Nullable String namespace,
                                     @NotNull ImportReport report) {
        ConfigurationSection blocks = source.getConfigurationSection("blocks");
        if (blocks == null) {
            return 0;
        }

        int converted = 0;
        for (String key : blocks.getKeys(false)) {
            ConfigurationSection block = blocks.getConfigurationSection(key);
            if (block == null) {
                continue;
            }
            try {
                convertBlock(key, block, output, report);
                report.imported((namespace != null ? namespace : "imported") + ":" + key);
                converted++;
            } catch (Exception e) {
                report.failed(key, e.getMessage() != null ? e.getMessage() : e.toString());
            }
        }
        return converted;
    }

    private static void convertBlock(@NotNull String key,
                                     @NotNull ConfigurationSection block,
                                     @NotNull YamlConfiguration output,
                                     @NotNull ImportReport report) {
        Map<String, Object> converted = new LinkedHashMap<>();
        converted.put("type", "block");

        String name = block.getString("display_name", block.getString("displayname"));
        if (name != null) {
            converted.put("display", Map.of("name", name));
        }

        ConfigurationSection resource = block.getConfigurationSection("resource");
        String modelPath = resource != null ? resource.getString("model_path") : null;
        if (modelPath != null) {
            converted.put("model", Map.of("custom", OraxenImporter.stripExtension(modelPath)));
        } else {
            java.util.List<String> textures = resource != null ? resource.getStringList("textures") : List.of();
            if (textures.isEmpty() && resource != null && resource.getString("texture") != null) {
                textures = List.of(resource.getString("texture"));
            }
            if (textures.isEmpty()) {
                throw new IllegalArgumentException("block has no model_path or texture in its resource section");
            }
            converted.put("model", Map.of("cube_all", OraxenImporter.stripExtension(textures.get(0))));
        }

        ConfigurationSection properties = block.getConfigurationSection("specific_properties");
        ConfigurationSection blockProperties = properties != null
                ? properties.getConfigurationSection("block") : null;
        if (blockProperties != null && blockProperties.contains("hardness")) {
            converted.put("behaviour", Map.of("hardness", blockProperties.getDouble("hardness")));
        }
        if (blockProperties != null && blockProperties.contains("placed_model")) {
            // ItemsAdder can render a placed block differently from its item form; Kalo
            // uses one model for both.
            report.unsupported(key + ".specific_properties.block.placed_model "
                    + "(Kalo uses one model for the item and the placed block)");
        }

        output.createSection(key, converted);
    }

    /** ItemsAdder furniture is entity-backed; Kalo's is a block. See FurnitureImportNotice. */
    private static int convertFurniture(@NotNull YamlConfiguration source,
                                        @NotNull YamlConfiguration output,
                                        @Nullable String namespace,
                                        @NotNull ImportReport report) {
        ConfigurationSection furniture = source.getConfigurationSection("furniture");
        if (furniture == null) {
            return 0;
        }

        int converted = 0;
        for (String key : furniture.getKeys(false)) {
            ConfigurationSection piece = furniture.getConfigurationSection(key);
            if (piece == null) {
                continue;
            }
            try {
                convertBlock(key, piece, output, report);
                ConfigurationSection out = output.getConfigurationSection(key);
                if (out != null) {
                    out.set("type", "furniture");
                }

                for (String property : piece.getKeys(false)) {
                    switch (property) {
                        case "entity" -> report.unsupported(key + ".entity "
                                + "(Kalo furniture is block-backed, not entity-backed)");
                        case "hitbox" -> report.unsupported(key + ".hitbox "
                                + "(Kalo furniture occupies exactly one block)");
                        case "sit", "seats" -> report.unsupported(key + "." + property
                                + " (Kalo furniture cannot be sat on)");
                        case "rotation", "rotatable" -> report.unsupported(key + "." + property
                                + " (Kalo furniture faces one way)");
                        case "display_name", "displayname", "resource", "specific_properties" -> {
                            // Handled by convertBlock.
                        }
                        default -> report.unsupported(key + "." + property);
                    }
                }

                report.imported((namespace != null ? namespace : "imported") + ":" + key);
                converted++;
            } catch (Exception e) {
                report.failed(key, e.getMessage() != null ? e.getMessage() : e.toString());
            }
        }
        return converted;
    }

    /**
     * ItemsAdder groups recipes by the station that crafts them, so a crafting table
     * recipe sits one level deeper than in Oraxen.
     */
    private static int convertRecipes(@NotNull YamlConfiguration source,
                                      @NotNull YamlConfiguration output,
                                      @Nullable String namespace,
                                      @NotNull ImportReport report) {
        ConfigurationSection recipes = source.getConfigurationSection("recipes");
        if (recipes == null) {
            return 0;
        }

        int converted = 0;
        for (String station : recipes.getKeys(false)) {
            ConfigurationSection group = recipes.getConfigurationSection(station);
            if (group == null) {
                continue;
            }
            if (!station.equals("crafting_table")) {
                // Furnaces, blast furnaces and ItemsAdder's own stations have no Kalo
                // equivalent yet; a crafting-table recipe would be the wrong shape.
                report.unsupported("recipes." + station
                        + " (only crafting_table recipes are imported so far)");
                continue;
            }

            for (String key : group.getKeys(false)) {
                ConfigurationSection recipe = group.getConfigurationSection(key);
                if (recipe == null) {
                    continue;
                }
                try {
                    ConfigurationSection ingredients = recipe.getConfigurationSection("ingredients");
                    if (ingredients == null) {
                        throw new IllegalArgumentException("recipe has no ingredients section");
                    }

                    ConfigurationSection resultSection = recipe.getConfigurationSection("result");
                    String result = resultSection != null ? resultSection.getString("item") : null;
                    if (result == null) {
                        throw new IllegalArgumentException("recipe has no result item");
                    }
                    int amount = resultSection.getInt("amount", 1);

                    output.createSection(key, RecipeImport.convert(key,
                            recipe.getStringList("pattern"), ingredients, result, amount,
                            (ingredient, slot) -> ingredient.getString("item"), report));
                    report.imported((namespace != null ? namespace : "imported") + ":" + key);
                    converted++;
                } catch (Exception e) {
                    report.failed(key, e.getMessage() != null ? e.getMessage() : e.toString());
                }
            }
        }
        return converted;
    }

    private static void reportUnconvertedSections(@NotNull YamlConfiguration source,
                                                  @NotNull ImportReport report) {
        for (String section : List.of("armors_rendering", "entities")) {
            if (source.contains(section)) {
                ConfigurationSection content = source.getConfigurationSection(section);
                int count = content != null ? content.getKeys(false).size() : 0;
                report.unsupported(section + " (" + count + " entr(y/ies); only items are imported so far)");
            }
        }
    }
}
