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
        if (items == null) {
            report.warn("No items section found — nothing to import");
            return output.saveToString();
        }

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

        // Blocks and furniture live in their own sections with a different shape; saying
        // so beats letting someone think an empty result means they had none.
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

    private static void reportUnconvertedSections(@NotNull YamlConfiguration source,
                                                  @NotNull ImportReport report) {
        for (String section : List.of("blocks", "furniture", "armors_rendering", "entities", "recipes")) {
            if (source.contains(section)) {
                ConfigurationSection content = source.getConfigurationSection(section);
                int count = content != null ? content.getKeys(false).size() : 0;
                report.unsupported(section + " (" + count + " entr(y/ies); only items are imported so far)");
            }
        }
    }
}
