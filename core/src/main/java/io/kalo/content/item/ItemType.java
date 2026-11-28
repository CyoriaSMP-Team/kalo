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
import io.kalo.content.liquid.LiquidBucketRegistry;
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
            Key liquid = null;
            String liquidId = config.getString("liquid");
            if (liquidId != null && !liquidId.isBlank()) {
                if (definition.java().baseMaterial() != Material.BUCKET) {
                    throw new IllegalArgumentException(
                            "liquid: requires material: BUCKET");
                }
                liquid = liquidId.indexOf(':') >= 0
                        ? Key.key(liquidId)
                        : pack.key(liquidId);
            }
            if (definition.model() instanceof ModelDefinition.Blockbench blockbench) {
                io.kalo.model.ModelIR imported = io.kalo.model.ModelRegistry.load(
                        pack.folder().toPath(), blockbench.source());
                io.kalo.model.ModelDiagnostics.log(logger,
                        "Blockbench model " + blockbench.source().asString(),
                        imported.diagnostics());
            }
            List<FeatureBuilder> features = parseFeatures(key, config.getConfigurationSection("features"));

            registries.item().register(key, entry -> {
                entry.key(key);
                entry.definition(definition);
                entry.features(features);
            });
            if (liquid != null) {
                LiquidBucketRegistry.register(key, liquid);
            }
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
        uncompilable = JavaPackCompiler.compileItems(resourcePack, contents);
    }

    /** Items the Java compiler could not emit, so Bedrock must skip them too. */
    private volatile java.util.Set<String> uncompilable = java.util.Set.of();

    public @NotNull java.util.Set<String> uncompilable() {
        return uncompilable;
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
                    bedrock.getString("icon"),
                    bedrock.isSet("scale") ? bedrock.getDouble("scale") : null,
                    bedrock.isSet("rotation") ? bedrock.getDoubleList("rotation") : null));
        }

        ConfigurationSection place = config.getConfigurationSection("place");
        if (place != null) {
            builder.placement(parsePlacement(place, key.namespace()));
        }

        ItemDefinition definition = builder.build();
        if (definition.placement() != null
                && !(definition.model() instanceof ModelDefinition.Blockbench)) {
            throw new IllegalArgumentException(
                    "place: requires a 'blockbench' model; a placed model needs bones and"
                            + " animations, which the legacy Java-JSON path does not carry");
        }
        return definition;
    }

    private static @NotNull io.kalo.content.item.definition.PlacementDefinition parsePlacement(
            @NotNull ConfigurationSection config, @NotNull String namespace) {
        double seatValue = config.getDouble("seat", 0);
        Double seat = config.contains("seat") ? seatValue : null;
        String idle = config.getString("idle");
        String interact = config.getString("interact");
        if (idle == null) {
            idle = config.getString("animation");
        }

        io.kalo.content.item.definition.PlacementDefinition.Storage storage = null;
        ConfigurationSection storageSection = config.getConfigurationSection("storage");
        if (storageSection != null) {
            storage = new io.kalo.content.item.definition.PlacementDefinition.Storage(
                    storageSection.getInt("rows", 3), storageSection.getString("title"));
        }

        java.util.List<io.kalo.content.item.definition.PlacementDefinition.State> states =
                new java.util.ArrayList<>();
        for (java.util.Map<?, ?> raw : config.getMapList("states")) {
            Double scale = number(raw.get("scale"));
            Integer light = integer(raw.get("light"));
            Boolean solid = bool(raw.get("solid"));
            String model = raw.get("model") == null ? null : raw.get("model").toString();
            String sound = raw.get("sound") == null ? null : raw.get("sound").toString();
            states.add(new io.kalo.content.item.definition.PlacementDefinition.State(
                    scale, light, solid,
                    model == null ? null : resolveKey(namespace, model), sound));
        }

        return new io.kalo.content.item.definition.PlacementDefinition(
                config.getDouble("scale", 1.0),
                config.getDouble("width", 1.0),
                config.getDouble("height", 1.0),
                seat,
                config.getInt("light", 0),
                idle,
                interact,
                storage,
                states);
    }

    private static @org.jetbrains.annotations.Nullable Double number(
            @org.jetbrains.annotations.Nullable Object value) {
        return value instanceof Number number ? number.doubleValue() : null;
    }

    private static @org.jetbrains.annotations.Nullable Integer integer(
            @org.jetbrains.annotations.Nullable Object value) {
        return value instanceof Number number ? number.intValue() : null;
    }

    private static @org.jetbrains.annotations.Nullable Boolean bool(
            @org.jetbrains.annotations.Nullable Object value) {
        return value instanceof Boolean bool ? bool : null;
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
            return new ModelDefinition.Custom(resolveKey(key.namespace(), custom),
                    parseTextures(key, config));
        }

        String blockbench = config.getString("blockbench");
        if (blockbench != null) {
            if (!blockbench.endsWith(".bbmodel")) {
                throw new IllegalArgumentException(
                        "blockbench model path must end in .bbmodel");
            }
            return new ModelDefinition.Blockbench(resolveKey(key.namespace(), blockbench),
                    parseTextures(key, config));
        }

        throw new IllegalArgumentException(
                "model section must declare one of 'sprite', 'vanilla', 'custom' or 'blockbench'");
    }

    private static @NotNull Map<String, Key> parseTextures(@NotNull Key key,
                                                            @NotNull ConfigurationSection config) {
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
        return textures;
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
