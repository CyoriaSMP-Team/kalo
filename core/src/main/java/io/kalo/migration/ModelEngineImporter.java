package io.kalo.migration;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Converts ModelEngine model configs into Kalo definitions.
 *
 * <p>ModelEngine stores models in {@code models/} as YAML files with Bedrock geometry
 * references. Each model has a resource pack path and display settings:</p>
 *
 * <pre>
 * ruby_sword_model:                   ruby_sword:
 *   model:                            type: item
 *     geo: models/item/ruby.geo.json  model:
 *   textures:                           custom: "models/item/ruby"
 *     - textures/item/ruby.png       java:
 *   display:                            base_material: PAPER
 *     rotation: [0, 0, 0]
 *     scale: [1, 1, 1]
 * </pre>
 *
 * <p>ModelEngine's animation system, AI behaviors, and entity bindings are its own
 * runtime system with no Kalo equivalent — these are reported as unsupported.</p>
 *
 * <p><b>Caveat.</b> Written against the documented format, not validated against a corpus
 * of real packs. ModelEngine is primarily for entity models; importing as items is a
 * best-effort conversion. A first import is a draft to review.</p>
 */
public final class ModelEngineImporter implements Importer {

    @Override
    public @NotNull String name() {
        return "ModelEngine";
    }

    @Override
    public int detect(@NotNull YamlConfiguration source) {
        // ModelEngine models have a "model" section with "geo" key
        ConfigurationSection model = source.getConfigurationSection("model");
        if (model != null && model.contains("geo")) {
            return 75;
        }
        return 0;
    }

    @Override
    public @NotNull String convert(@NotNull YamlConfiguration source,
                                   @NotNull String namespace,
                                   @NotNull ImportReport report) {
        YamlConfiguration output = new YamlConfiguration();

        for (String key : source.getKeys(false)) {
            ConfigurationSection modelDef = source.getConfigurationSection(key);
            if (modelDef == null) continue;

            try {
                convertModel(key, modelDef, output, report);
                report.imported(namespace + ":" + key);
            } catch (Exception e) {
                report.failed(key, e.getMessage() != null ? e.getMessage() : e.toString());
            }
        }

        return output.saveToString();
    }

    private static void convertModel(@NotNull String key,
                                     @NotNull ConfigurationSection modelDef,
                                     @NotNull YamlConfiguration output,
                                     @NotNull ImportReport report) {
        Map<String, Object> converted = new LinkedHashMap<>();
        converted.put("type", "item");

        // Model reference
        ConfigurationSection model = modelDef.getConfigurationSection("model");
        if (model != null) {
            String geo = model.getString("geo");
            if (geo != null) {
                // Strip extension and leading path for Kalo model key
                String modelPath = geo.replaceAll("\\.(geo\\.json|json)$", "");
                if (modelPath.startsWith("models/")) {
                    modelPath = modelPath.substring("models/".length());
                }
                converted.put("model", Map.of("custom", modelPath));
            }
        }

        // Display settings (rotation, scale, translation)
        ConfigurationSection display = modelDef.getConfigurationSection("display");
        if (display != null) {
            reportUnsupportedDisplay(key, display, report);
        }

        // Textures
        List<String> textures = modelDef.getStringList("textures");
        if (!textures.isEmpty()) {
            report.unsupported(key + ".textures (ModelEngine textures are Bedrock format; "
                    + "add a 'model:' section to Kalo manually)");
        }

        // Use PAPER as base material for model-defined items
        converted.put("java", Map.of("base_material", "PAPER"));

        reportUnknownKeys(key, modelDef, report);

        output.createSection(key, converted);
    }

    private static void reportUnsupportedDisplay(@NotNull String key,
                                                 @NotNull ConfigurationSection display,
                                                 @NotNull ImportReport report) {
        for (String child : display.getKeys(false)) {
            report.unsupported(key + ".display." + child
                    + " (ModelEngine display settings are Bedrock format; "
                    + "configure in Kalo's model section manually)");
        }
    }

    private static void reportUnknownKeys(@NotNull String key,
                                          @NotNull ConfigurationSection modelDef,
                                          @NotNull ImportReport report) {
        for (String child : modelDef.getKeys(false)) {
            switch (child) {
                case "model", "textures", "display" -> {
                    // Handled above.
                }
                case "animations", "animation_controllers", "ai", "behaviors",
                     "mount", "ride", "hitbox", "scale" ->
                        report.unsupported(key + "." + child
                                + " (ModelEngine entity features have no Kalo equivalent)");
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
