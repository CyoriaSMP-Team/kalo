package io.kalo.migration;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.jetbrains.annotations.NotNull;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Converts Neko content packs.
 *
 * <p>Kalo is a fork of Neko, so this is the one importer whose source format is known
 * exactly rather than from documentation — it is the shape Kalo itself had before the IR
 * replaced it.</p>
 *
 * <pre>
 * ruby_sword:                        ruby_sword:
 *   type: item                         type: item
 *   properties:                 →      display:
 *     type: NETHERITE_SWORD              name: "&lt;red&gt;Ruby&lt;/red&gt;"
 *     name: "&lt;red&gt;Ruby&lt;/red&gt;"          java:
 *     lore: [...]                        base_material: NETHERITE_SWORD
 *   features:                          features:
 *     hello:                             hello:
 *       id: "neko:hello_world"             id: "kalo:hello_world"
 * </pre>
 *
 * <p>Neko items had no model at all — {@code properties} was material, name and lore — so
 * an imported item renders as its base material until a model is added. That is called
 * out per item rather than left as a surprise.</p>
 */
public final class NekoImporter implements Importer {

    @Override
    public @NotNull String name() {
        return "Neko";
    }

    @Override
    public int detect(@NotNull YamlConfiguration source) {
        // A `properties` section under a `type: item` entry is Neko's shape and nobody
        // else's — Oraxen uses Pack/material, ItemsAdder nests under items.
        for (String key : source.getKeys(false)) {
            ConfigurationSection section = source.getConfigurationSection(key);
            if (section == null) {
                continue;
            }
            if (section.isConfigurationSection("properties") && section.contains("type")) {
                return 90;
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
            ConfigurationSection content = source.getConfigurationSection(key);
            if (content == null) {
                continue;
            }
            try {
                convertContent(key, content, output, report);
                report.imported(namespace + ":" + key);
            } catch (Exception e) {
                report.failed(key, e.getMessage() != null ? e.getMessage() : e.toString());
            }
        }

        return output.saveToString();
    }

    private static void convertContent(@NotNull String key,
                                       @NotNull ConfigurationSection content,
                                       @NotNull YamlConfiguration output,
                                       @NotNull ImportReport report) {
        String type = content.getString("type", "item");
        if (!type.equals("item")) {
            // Neko only ever shipped an item type; anything else is from a fork.
            report.unsupported(key + ".type = " + type + " (only Neko's item type is known)");
            return;
        }

        Map<String, Object> converted = new LinkedHashMap<>();
        converted.put("type", "item");

        ConfigurationSection properties = content.getConfigurationSection("properties");
        if (properties == null) {
            throw new IllegalArgumentException("item has no properties section");
        }

        Map<String, Object> display = new LinkedHashMap<>();
        String name = properties.getString("name");
        if (name != null) {
            // Neko used MiniMessage already, so names carry over untouched.
            display.put("name", name);
        }
        List<String> lore = properties.getStringList("lore");
        if (!lore.isEmpty()) {
            display.put("lore", lore);
        }
        if (!display.isEmpty()) {
            converted.put("display", display);
        }

        String material = properties.getString("type");
        if (material != null) {
            Material parsed = Material.matchMaterial(material.toUpperCase(Locale.ROOT));
            if (parsed == null) {
                throw new IllegalArgumentException("unknown material '" + material + "'");
            }
            converted.put("java", Map.of("base_material", parsed.name()));
        }

        // Neko had no model support at all, so there is nothing to carry and the item
        // will render as its base material until someone adds one.
        report.unsupported(key + " has no model — Neko could not define one; "
                + "add a 'model:' section to give it its own appearance");

        Map<String, Object> features = convertFeatures(key, content.getConfigurationSection("features"), report);
        if (!features.isEmpty()) {
            converted.put("features", features);
        }

        output.createSection(key, converted);
    }

    /**
     * Feature ids move from the {@code neko:} namespace to {@code kalo:}; the argument
     * shape is unchanged because Kalo inherited it.
     */
    private static @NotNull Map<String, Object> convertFeatures(@NotNull String key,
                                                                ConfigurationSection features,
                                                                @NotNull ImportReport report) {
        Map<String, Object> converted = new LinkedHashMap<>();
        if (features == null) {
            return converted;
        }

        for (String name : features.getKeys(false)) {
            ConfigurationSection feature = features.getConfigurationSection(name);
            if (feature == null) {
                continue;
            }
            String id = feature.getString("id");
            if (id == null) {
                report.unsupported(key + ".features." + name + " (no id)");
                continue;
            }

            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("id", id.startsWith("neko:") ? "kalo:" + id.substring("neko:".length()) : id);

            ConfigurationSection arguments = feature.getConfigurationSection("arguments");
            if (arguments != null) {
                Map<String, Object> args = new LinkedHashMap<>();
                arguments.getKeys(false).forEach(argument -> args.put(argument, arguments.get(argument)));
                entry.put("arguments", args);
            }

            if (id.startsWith("neko:") && !id.equals("neko:hello_world")) {
                // Only the built-in survived the fork under the same name; anything else
                // came from a Neko add-on that has no Kalo counterpart.
                report.unsupported(key + ".features." + name + " uses '" + id
                        + "', which has no Kalo equivalent unless an add-on provides it");
            }

            converted.put(name, entry);
        }
        return converted;
    }
}
