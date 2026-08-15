package io.kalo.content.block;

import io.kalo.config.ConfigSchema;
import io.kalo.content.ContentType;
import io.kalo.content.PackContext;
import io.kalo.content.block.definition.BlockBehaviour;
import io.kalo.content.block.definition.BlockCarrier;
import io.kalo.content.block.definition.BlockDefinition;
import io.kalo.content.block.definition.BlockModelDefinition;
import io.kalo.content.block.definition.JavaBlockOptions;
import io.kalo.content.feature.FeatureArguments;
import io.kalo.content.feature.FeatureBuilder;
import io.kalo.content.feature.FeatureFactory;
import io.kalo.content.item.definition.BedrockOptions;
import io.kalo.content.item.definition.DisplayProperties;
import io.kalo.manager.RegistryManager;
import io.kalo.pack.ResourcePack;
import io.kalo.platform.java.BlockStateAllocator;
import io.kalo.platform.java.JavaBlockCompiler;
import io.kalo.registry.Registries;
import io.kalo.utils.Constants;
import io.kalo.utils.Plugins;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
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

public final class BlockType implements ContentType<Block> {
    public static final Key KEY = Key.key(Constants.PLUGIN_ID, "block");

    private static final ConfigSchema BLOCK_CONFIG_SCHEMA = new BlockConfigSchema();

    private final BlockStateAllocator allocator;

    public BlockType(@NotNull BlockStateAllocator allocator) {
        this.allocator = allocator;
    }

    public @NotNull BlockStateAllocator allocator() {
        return allocator;
    }

    @Override
    public @NotNull String id() {
        return "block";
    }

    @Override
    public @NotNull Class<Block> clazz() {
        return Block.class;
    }

    @Override
    public @NotNull Iterable<Block> contents(@NotNull Registries registries) {
        return registries.block();
    }

    @Override
    public boolean load(@NotNull PackContext pack, @NotNull Registries registries, @NotNull ConfigurationSection config) {
        Logger logger = Plugins.logger();

        ConfigSchema.Result validationResult = BLOCK_CONFIG_SCHEMA.validate(config);
        if (!validationResult.isSuccess()) {
            logger.warning("Failed to load '" + config.getName() + "' in pack '" + pack.namespace() + "'");
            validationResult.getErrors().forEach(error -> logger.warning("  " + error));
            return false;
        }

        Key key = pack.key(config.getName());

        try {
            BlockDefinition definition = parseDefinition(key, config);
            List<FeatureBuilder> features = parseFeatures(key, config.getConfigurationSection("features"));

            registries.block().register(key, entry -> {
                entry.key(key);
                entry.definition(definition);
                entry.features(features);
            });
            return true;
        } catch (Exception e) {
            logger.log(Level.WARNING, "Failed to load block '" + key.asString() + "': " + e.getMessage(), e);
            return false;
        }
    }

    @Override
    public void compilePack(@NotNull ResourcePack resourcePack, @NotNull Iterable<Block> contents) {
        JavaBlockCompiler.compileBlocks(resourcePack, contents, allocator);
    }

    public static @NotNull BlockDefinition parseDefinition(@NotNull Key key, @NotNull ConfigurationSection config) {
        BlockDefinition.Builder builder = BlockDefinition.builder(key);

        ConfigurationSection display = config.getConfigurationSection("display");
        if (display != null) {
            builder.display(parseDisplay(display));
        }

        builder.model(parseModel(key, Objects.requireNonNull(config.getConfigurationSection("model"))));

        ConfigurationSection behaviour = config.getConfigurationSection("behaviour");
        if (behaviour == null) {
            behaviour = config.getConfigurationSection("behavior");
        }
        if (behaviour != null) {
            BlockBehaviour defaults = BlockBehaviour.defaults();
            builder.behaviour(new BlockBehaviour(
                    (float) behaviour.getDouble("hardness", defaults.hardness()),
                    behaviour.getBoolean("requires_tool", defaults.requiresTool())));
        }

        ConfigurationSection java = config.getConfigurationSection("java");
        if (java != null) {
            String carrier = java.getString("carrier");
            if (carrier != null) {
                builder.java(new JavaBlockOptions(BlockCarrier.valueOf(carrier.toUpperCase(Locale.ROOT))));
            }
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
        String name = config.getString("name");
        List<Component> lore = config.getStringList("lore").stream()
                .map(MiniMessage.miniMessage()::deserialize)
                .toList();
        return new DisplayProperties(
                name != null ? MiniMessage.miniMessage().deserialize(name) : null,
                lore,
                config.getBoolean("glint", false));
    }

    private static @NotNull BlockModelDefinition parseModel(@NotNull Key key, @NotNull ConfigurationSection config) {
        String cubeAll = config.getString("cube_all");
        if (cubeAll != null) {
            return new BlockModelDefinition.CubeAll(resolveKey(key.namespace(), cubeAll));
        }

        ConfigurationSection cube = config.getConfigurationSection("cube");
        if (cube != null) {
            Map<String, Key> faces = new HashMap<>();
            for (String face : cube.getKeys(false)) {
                String texture = cube.getString(face);
                if (texture != null) {
                    faces.put(face, resolveKey(key.namespace(), texture));
                }
            }
            if (faces.isEmpty()) {
                throw new IllegalArgumentException("cube section declares no faces");
            }
            return new BlockModelDefinition.Cube(faces);
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
            return new BlockModelDefinition.Custom(resolveKey(key.namespace(), custom), textures);
        }

        throw new IllegalArgumentException("model section must declare one of 'cube_all', 'cube' or 'custom'");
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
                    .orElseThrow(() -> new IllegalArgumentException("unknown feature '" + id + "' on " + key.asString()));

            features.add(new FeatureBuilder(factory, new FeatureArguments(featureConfig.getConfigurationSection("arguments"))));
        }
        return features;
    }

    private static @NotNull Key resolveKey(@NotNull String fallbackNamespace, @NotNull String value) {
        int separator = value.indexOf(':');
        if (separator < 0) {
            return Key.key(fallbackNamespace, value);
        }
        return Key.key(value.substring(0, separator), value.substring(separator + 1));
    }
}
