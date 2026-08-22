package io.kalo.content.gui;

import io.kalo.content.ContentType;
import io.kalo.content.PackContext;
import io.kalo.content.feature.FeatureBuilder;
import io.kalo.manager.RegistryManager;
import io.kalo.pack.ResourcePack;
import io.kalo.registry.Registries;
import io.kalo.utils.Constants;
import io.kalo.utils.Plugins;
import net.kyori.adventure.key.Key;
import org.bukkit.configuration.ConfigurationSection;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.logging.Level;

/**
 * Handles custom GUIs: parses YAML and registers in registry.
 *
 * <p>GUIs are inventory-based menus with configurable slots, items, and actions.
 * They are opened via commands or other triggers.</p>
 */
public final class GuiType implements ContentType<Gui> {
    public static final Key KEY = Key.key(Constants.PLUGIN_ID, "gui");

    @Override
    public @NotNull String id() {
        return "gui";
    }

    @Override
    public @NotNull Class<Gui> clazz() {
        return Gui.class;
    }

    @Override
    public @NotNull Iterable<Gui> contents(@NotNull Registries registries) {
        return registries.gui();
    }

    @Override
    public boolean load(@NotNull PackContext pack, @NotNull Registries registries, @NotNull ConfigurationSection config) {
        Key key = pack.key(config.getName());
        try {
            GuiDefinition definition = parseDefinition(key, config);
            List<FeatureBuilder> features = parseFeatures(key, config.getConfigurationSection("features"));

            registries.gui().register(key, entry -> {
                entry.key(key).definition(definition).features(features);
            });
            return true;
        } catch (Exception e) {
            Plugins.logger().log(Level.WARNING, "Failed to load GUI '" + key.asString() + "'", e);
            return false;
        }
    }

    @Override
    public void compilePack(@NotNull ResourcePack resourcePack, @NotNull Iterable<Gui> contents) {
        // GUIs don't generate resource pack assets - they're server-side only
    }

    private static @NotNull GuiDefinition parseDefinition(@NotNull Key key, @NotNull ConfigurationSection config) {
        GuiDefinition.Builder builder = GuiDefinition.builder(key);

        builder.title(Objects.requireNonNull(config.getString("title"),
                "GUI '" + key.asString() + "' needs a title"));
        builder.rows(config.getInt("rows", 6));

        // Parse items
        List<GuiDefinition.SlotConfig> items = new ArrayList<>();
        ConfigurationSection itemsSection = config.getConfigurationSection("items");
        if (itemsSection != null) {
            for (String slotStr : itemsSection.getKeys(false)) {
                ConfigurationSection slotConfig = itemsSection.getConfigurationSection(slotStr);
                if (slotConfig == null) continue;

                int slot;
                try {
                    slot = Integer.parseInt(slotStr);
                } catch (NumberFormatException e) {
                    continue;
                }

                String material = slotConfig.getString("material", "STONE");
                String displayName = slotConfig.getString("display_name");
                List<String> lore = slotConfig.getStringList("lore");
                int amount = slotConfig.getInt("amount", 1);
                int customModelData = slotConfig.getInt("custom_model_data", 0);
                List<String> actions = slotConfig.getStringList("actions");

                Map<String, String> conditions = new HashMap<>();
                ConfigurationSection conditionsSection = slotConfig.getConfigurationSection("conditions");
                if (conditionsSection != null) {
                    for (String condKey : conditionsSection.getKeys(false)) {
                        conditions.put(condKey, Objects.toString(conditionsSection.get(condKey)));
                    }
                }

                items.add(new GuiDefinition.SlotConfig(slot, material, displayName, lore, amount, customModelData, actions, conditions));
            }
        }
        builder.items(items);

        // Parse close actions
        builder.closeActions(config.getStringList("close_actions"));

        return builder.build();
    }

    public static @NotNull List<FeatureBuilder> parseFeatures(@NotNull Key key, @Nullable ConfigurationSection config) {
        List<FeatureBuilder> features = new ArrayList<>();
        if (config == null) {
            return features;
        }

        for (String name : config.getKeys(false)) {
            ConfigurationSection featureConfig = config.getConfigurationSection(name);
            if (featureConfig == null) {
                continue;
            }

            String id = Objects.requireNonNull(featureConfig.getString("id"), "feature '" + name + "' is missing an id");
            io.kalo.content.feature.FeatureFactory<?> factory = RegistryManager.GlobalRegistries.registries().features()
                    .get(Key.key(id))
                    .orElseThrow(() -> new IllegalArgumentException("unknown feature '" + id + "' on " + key.asString()));

            features.add(new FeatureBuilder(factory, new io.kalo.content.feature.FeatureArguments(featureConfig.getConfigurationSection("arguments"))));
        }
        return features;
    }
}
