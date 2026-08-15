package io.kalo.content.item;

import io.kalo.config.ConfigSchema;
import io.kalo.content.ContentType;
import io.kalo.content.PackContext;
import io.kalo.content.feature.FeatureArguments;
import io.kalo.content.feature.FeatureBuilder;
import io.kalo.content.feature.FeatureFactory;
import io.kalo.content.item.definition.BedrockOptions;
import io.kalo.content.item.definition.DisplayProperties;
import io.kalo.content.item.definition.ItemBehaviour;
import io.kalo.content.item.definition.ItemDefinition;
import io.kalo.content.item.definition.JavaOptions;
import io.kalo.content.item.definition.ModelDefinition;
import io.kalo.manager.RegistryManager;
import io.kalo.pack.ResourcePack;
import io.kalo.platform.java.JavaPackCompiler;
import io.kalo.registry.Registries;
import io.kalo.utils.Constants;
import io.kalo.utils.Plugins;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class ItemType implements ContentType<Item> {
    public static final Key KEY = Key.key(Constants.PLUGIN_ID, "item");

    private static final ConfigSchema ITEM_CONFIG_SCHEMA = new ItemConfigSchema();

    public static Key key() {
        return KEY;
    }

    @Override
    public @NotNull String id() {
        return "item";
    }

    @Override
    public @NotNull Class<Item> clazz() {
        return Item.class;
    }

    @Override
    public @NotNull Iterable<Item> contents(@NotNull Registries registries) {
        return registries.item();
    }

    @Override
    public boolean load(@NotNull PackContext pack, @NotNull Registries registries, @NotNull ConfigurationSection config) {
        Logger logger = Plugins.logger();

        ConfigSchema.Result validationResult = ITEM_CONFIG_SCHEMA.validate(config);
        if (!validationResult.isSuccess()) {
            logger.warning("Failed to load '" + config.getName() + "' in pack '" + pack.namespace() + "'");
            validationResult.getErrors().forEach(error -> logger.warning("  " + error));
            return false;
        }

        // Namespaced by the owning pack. Without this every pack's content would land in
        // the minecraft: namespace and collide across packs.
        Key key = pack.key(config.getName());

        try {
            ItemDefinition definition = parseDefinition(key, config);
            List<FeatureBuilder> features = parseFeatures(key, config.getConfigurationSection("features"));

            registries.item().register(key, entry -> {
                entry.key(key);
                entry.definition(definition);
                entry.features(features);
            });
            return true;
        } catch (Exception e) {
            // Swallowing this was the single worst thing for pack authors: a typo in a
            // material name produced a silent disappearance with no indication of why.
            logger.log(Level.WARNING, "Failed to load item '" + key.asString() + "': " + e.getMessage(), e);
            return false;
        }
    }

    @Override
    public void compilePack(@NotNull ResourcePack resourcePack, @NotNull Iterable<Item> contents) {
        JavaPackCompiler.compileItems(resourcePack, contents);
    }

    public static @NotNull ItemDefinition parseDefinition(@NotNull Key key, @NotNull ConfigurationSection config) {
        ItemDefinition.Builder builder = ItemDefinition.builder(key);

        ConfigurationSection display = config.getConfigurationSection("display");
        if (display != null) {
            builder.display(parseDisplay(display));
        }

        ConfigurationSection model = config.getConfigurationSection("model");
        if (model != null) {
            builder.model(parseModel(key, model));
        }

        ConfigurationSection behaviour = config.getConfigurationSection("behaviour");
        if (behaviour == null) {
            behaviour = config.getConfigurationSection("behavior");
        }
        if (behaviour != null) {
            builder.behaviour(parseBehaviour(behaviour));
        }

        ConfigurationSection java = config.getConfigurationSection("java");
        if (java != null) {
            builder.java(parseJavaOptions(java));
        }

        ConfigurationSection bedrock = config.getConfigurationSection("bedrock");
        if (bedrock != null) {
            builder.bedrock(new BedrockOptions(
                    bedrock.getBoolean("enabled", true),
                    bedrock.getString("icon")));
        }

        return builder.build();
    }

    private static @NotNull DisplayProperties parseDisplay(@NotNull ConfigurationSection config) {
        Component name = deserialize(config.getString("name"));
        List<Component> lore = config.getStringList("lore").stream()
                .map(MiniMessage.miniMessage()::deserialize)
                .toList();
        return new DisplayProperties(name, lore, config.getBoolean("glint", false));
    }

    private static @NotNull ModelDefinition parseModel(@NotNull Key key, @NotNull ConfigurationSection config) {
        String sprite = config.getString("sprite");
        if (sprite != null) {
            return new ModelDefinition.Sprite(resolveKey(key.namespace(), sprite));
        }

        String vanilla = config.getString("vanilla");
        if (vanilla != null) {
            return new ModelDefinition.Vanilla(resolveKey("minecraft", vanilla));
        }

        String custom = config.getString("custom");
        if (custom != null) {
            Map<String, Key> textures = new HashMap<>();
            ConfigurationSection texturesSection = config.getConfigurationSection("textures");
            if (texturesSection != null) {
                for (String slot : texturesSection.getKeys(false)) {
                    String texture = texturesSection.getString(slot);
                    if (texture != null) {
                        textures.put(slot, resolveKey(key.namespace(), texture));
                    }
                }
            }
            return new ModelDefinition.Custom(resolveKey(key.namespace(), custom), textures);
        }

        throw new IllegalArgumentException(
                "model section must declare one of 'sprite', 'vanilla' or 'custom'");
    }

    private static @NotNull ItemBehaviour parseBehaviour(@NotNull ConfigurationSection config) {
        ItemBehaviour defaults = ItemBehaviour.defaults();
        // Integer.valueOf is not redundant: a ternary mixing int and Integer unboxes both
        // branches, so the null default would NPE on any item without a durability.
        Integer durability = config.contains("durability")
                ? Integer.valueOf(config.getInt("durability"))
                : defaults.maxDurability();
        // A damageable item cannot stack, so honour the durability over a stale stack
        // size rather than failing the whole definition on a contradiction.
        int stackSize = durability != null ? 1 : config.getInt("stack_size", defaults.maxStackSize());
        return new ItemBehaviour(stackSize, durability, config.getBoolean("fire_resistant", defaults.fireResistant()));
    }

    private static @NotNull JavaOptions parseJavaOptions(@NotNull ConfigurationSection config) {
        String material = config.getString("base_material");
        if (material == null) {
            return JavaOptions.defaults();
        }
        Material parsed = Material.matchMaterial(material.toUpperCase(Locale.ROOT));
        if (parsed == null || !parsed.isItem()) {
            throw new IllegalArgumentException("'" + material + "' is not a valid item material");
        }
        return new JavaOptions(parsed);
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
            FeatureFactory<?> factory = RegistryManager.GlobalRegistries.registries().features()
                    .get(Key.key(id))
                    .orElseThrow(() -> new IllegalArgumentException(
                            "unknown feature '" + id + "' on " + key.asString()));

            features.add(new FeatureBuilder(factory, new FeatureArguments(featureConfig.getConfigurationSection("arguments"))));
        }
        return features;
    }

    /** Applies {@code fallbackNamespace} to an unqualified key so packs can omit their own. */
    private static @NotNull Key resolveKey(@NotNull String fallbackNamespace, @NotNull String value) {
        int separator = value.indexOf(':');
        if (separator < 0) {
            return Key.key(fallbackNamespace, value);
        }
        return Key.key(value.substring(0, separator), value.substring(separator + 1));
    }

    private static @Nullable Component deserialize(@Nullable String value) {
        return value != null ? MiniMessage.miniMessage().deserialize(value) : null;
    }
}
