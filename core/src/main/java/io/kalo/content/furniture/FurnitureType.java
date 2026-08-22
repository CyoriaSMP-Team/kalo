package io.kalo.content.furniture;

import io.kalo.config.ConfigSchema;
import io.kalo.content.ContentType;
import io.kalo.content.PackContext;
import io.kalo.content.block.BlockConfigSchema;
import io.kalo.content.block.definition.BlockBehaviour;
import io.kalo.content.block.definition.BlockCarrier;
import io.kalo.content.block.definition.BlockModelDefinition;
import io.kalo.content.block.definition.JavaBlockMode;
import io.kalo.content.block.definition.JavaBlockOptions;
import io.kalo.content.furniture.definition.FurnitureBehaviour;
import io.kalo.content.furniture.definition.FurnitureDefinition;
import io.kalo.content.furniture.definition.FurnitureDisplay;
import io.kalo.content.feature.FeatureBuilder;
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

/**
 * Parses furniture definitions from YAML and compiles them into resource pack assets.
 *
 * <p>Furniture is a block with extra properties: rotation, seating, hitboxes, storage,
 * and jukebox support. The YAML format extends the block format with a {@code furniture}
 * section.</p>
 */
public final class FurnitureType implements ContentType<Furniture> {
    public static final Key KEY = Key.key(Constants.PLUGIN_ID, "furniture");
    private static final ConfigSchema FURNITURE_CONFIG_SCHEMA = new BlockConfigSchema();
    private final BlockStateAllocator allocator;

    public FurnitureType(@NotNull BlockStateAllocator allocator) {
        this.allocator = allocator;
    }

    @Override
    public @NotNull String id() {
        return "furniture";
    }

    @Override
    public @NotNull Class<Furniture> clazz() {
        return Furniture.class;
    }

    @Override
    public @NotNull Iterable<Furniture> contents(@NotNull Registries registries) {
        return registries.furniture();
    }

    @Override
    public boolean load(@NotNull PackContext pack, @NotNull Registries registries, @NotNull ConfigurationSection config) {
        ConfigSchema.Result result = FURNITURE_CONFIG_SCHEMA.validate(config);
        if (!result.isSuccess()) {
            Plugins.logger().warning("Failed to load furniture '" + config.getName() + "' in pack '" + pack.namespace() + "'");
            result.getErrors().forEach(error -> Plugins.logger().warning("  " + error));
            return false;
        }

        Key key = pack.key(config.getName());
        try {
            FurnitureDefinition definition = parseDefinition(key, config);
            List<FeatureBuilder> features = parseFeatures(key, config.getConfigurationSection("features"));

            registries.furniture().register(key, entry -> {
                entry.key(key).definition(definition).features(features);
            });
            return true;
        } catch (Exception e) {
            Plugins.logger().log(Level.WARNING, "Failed to load furniture '" + key.asString() + "'", e);
            return false;
        }
    }

    private volatile java.util.Set<String> uncompilable = java.util.Set.of();

    @Override
    public void compilePack(@NotNull ResourcePack resourcePack, @NotNull Iterable<Furniture> contents) {
        List<io.kalo.content.block.Block> blocks = new ArrayList<>();
        contents.forEach(blocks::add);
        uncompilable = JavaBlockCompiler.compileBlocks(resourcePack, blocks, allocator).keySet();
    }

    /**
     * Keys that failed to compile on Java.
     */
    public @NotNull java.util.Set<String> uncompilable() {
        return uncompilable;
    }

    /**
     * Parses a furniture definition from YAML config.
     */
    public static @NotNull FurnitureDefinition parseDefinition(@NotNull Key key, @NotNull ConfigurationSection config) {
        FurnitureDefinition.Builder builder = FurnitureDefinition.builder(key);

        // Parse display (same as blocks)
        ConfigurationSection display = config.getConfigurationSection("display");
        if (display != null) {
            builder.display(parseDisplay(display));
        }

        // Parse model (same as blocks)
        builder.model(parseModel(key, Objects.requireNonNull(config.getConfigurationSection("model"))));

        // Parse block behaviour (base hardness, requires_tool)
        BlockBehaviour blockBehaviour = BlockBehaviour.defaults();
        ConfigurationSection behaviour = config.getConfigurationSection("behaviour");
        if (behaviour == null) {
            behaviour = config.getConfigurationSection("behavior");
        }
        if (behaviour != null) {
            blockBehaviour = new BlockBehaviour(
                    (float) behaviour.getDouble("hardness", blockBehaviour.hardness()),
                    behaviour.getBoolean("requires_tool", blockBehaviour.requiresTool()));
        }

        // Parse furniture-specific behaviour
        FurnitureBehaviour furnitureBehaviour = parseFurnitureBehaviour(config.getConfigurationSection("furniture"), blockBehaviour);

        // Parse Java options (same as blocks)
        JavaBlockOptions javaOptions = JavaBlockOptions.defaults();
        ConfigurationSection java = config.getConfigurationSection("java");
        if (java != null) {
            String modeValue = java.getString("mode", "native");
            JavaBlockMode mode = JavaBlockMode.valueOf(modeValue.toUpperCase(Locale.ROOT));
            String carrier = java.getString("carrier");
            if (carrier != null) {
                javaOptions = new JavaBlockOptions(mode, BlockCarrier.fromId(carrier));
            } else if (mode == JavaBlockMode.VIRTUAL) {
                javaOptions = JavaBlockOptions.virtual();
            }
        }

        // Parse Bedrock options
        BedrockOptions bedrockOptions = BedrockOptions.defaults();
        ConfigurationSection bedrock = config.getConfigurationSection("bedrock");
        if (bedrock != null) {
            bedrockOptions = new BedrockOptions(
                    bedrock.getBoolean("enabled", true),
                    bedrock.getString("icon"));
        }

        // Parse display transform
        FurnitureDisplay displayTransform = parseDisplayTransform(config.getConfigurationSection("display_transform"));

        // Build the block definition part
        // The behaviour is set later in the builder when we have the full FurnitureBehaviour
        builder.java(javaOptions);
        builder.bedrock(bedrockOptions);
        builder.displayTransform(displayTransform);

        // Store furniture behaviour in a way that can be accessed later
        // For now, we'll store it in the definition builder's behaviour
        // TODO: This needs to be properly integrated into FurnitureDefinition
        // For now, we'll create a custom FurnitureBehaviour and store it

        return builder.build();
    }

    /**
     * Parses furniture-specific behaviour from YAML.
     */
    private static @NotNull FurnitureBehaviour parseFurnitureBehaviour(@Nullable ConfigurationSection config, @NotNull BlockBehaviour blockBehaviour) {
        if (config == null) {
            return FurnitureBehaviour.defaults();
        }

        // Parse rotation
        boolean rotatable = config.getBoolean("rotatable", false);
        String restrictedRotation = config.getString("restricted_rotation");

        // Parse seat
        FurnitureBehaviour.Seat seat = null;
        ConfigurationSection seatConfig = config.getConfigurationSection("seat");
        if (seatConfig != null) {
            double height = seatConfig.getDouble("height", 0.5);
            List<Double> offset = seatConfig.getDoubleList("offset");
            if (offset.isEmpty()) {
                offset = List.of(0.0, 0.5, 0.0);
            }
            String direction = seatConfig.getString("direction");
            seat = new FurnitureBehaviour.Seat(height, offset, direction);
        }

        // Parse hitbox (barrier offsets)
        List<double[]> hitbox = new ArrayList<>();
        ConfigurationSection hitboxConfig = config.getConfigurationSection("hitbox");
        if (hitboxConfig != null) {
            List<?> barriers = hitboxConfig.getList("barriers");
            if (barriers != null) {
                for (Object barrier : barriers) {
                    if (barrier instanceof List<?> coords && coords.size() >= 3) {
                        double x = coords.get(0) instanceof Number n ? n.doubleValue() : 0;
                        double y = coords.get(1) instanceof Number n ? n.doubleValue() : 0;
                        double z = coords.get(2) instanceof Number n ? n.doubleValue() : 0;
                        hitbox.add(new double[]{x, y, z});
                    }
                }
            }
        }
        if (hitbox.isEmpty()) {
            hitbox.add(new double[]{0, 0, 0});
        }

        // Parse storage
        FurnitureBehaviour.Storage storage = null;
        ConfigurationSection storageConfig = config.getConfigurationSection("storage");
        if (storageConfig != null) {
            String type = storageConfig.getString("type", "STORAGE");
            int rows = storageConfig.getInt("rows", 6);
            String title = storageConfig.getString("title");
            String openSound = storageConfig.getString("open_sound");
            String closeSound = storageConfig.getString("close_sound");
            storage = new FurnitureBehaviour.Storage(type, rows, title, openSound, closeSound);
        }

        // Parse jukebox
        FurnitureBehaviour.Jukebox jukebox = null;
        ConfigurationSection jukeboxConfig = config.getConfigurationSection("jukebox");
        if (jukeboxConfig != null) {
            double volume = jukeboxConfig.getDouble("volume", 1.0);
            double pitch = jukeboxConfig.getDouble("pitch", 1.0);
            String permission = jukeboxConfig.getString("permission");
            jukebox = new FurnitureBehaviour.Jukebox(volume, pitch, permission);
        }

        // Parse waterloggable
        boolean waterloggable = config.getBoolean("waterloggable", false);

        // Parse light
        int light = config.getInt("light", 0);

        // Parse limited placing
        FurnitureBehaviour.LimitedPlacing limitedPlacing = null;
        ConfigurationSection limitedConfig = config.getConfigurationSection("limited_placing");
        if (limitedConfig != null) {
            boolean roof = limitedConfig.getBoolean("roof", true);
            boolean floor = limitedConfig.getBoolean("floor", true);
            boolean wall = limitedConfig.getBoolean("wall", true);
            String type = limitedConfig.getString("type");
            List<String> blockTypes = limitedConfig.getStringList("block_types");
            List<String> blockTags = limitedConfig.getStringList("block_tags");
            List<String> nexoBlocks = limitedConfig.getStringList("nexo_blocks");
            limitedPlacing = new FurnitureBehaviour.LimitedPlacing(
                    roof, floor, wall, type, blockTypes, blockTags, nexoBlocks);
        }

        return new FurnitureBehaviour(
                blockBehaviour.hardness(), blockBehaviour.requiresTool(),
                rotatable, restrictedRotation, seat, hitbox, storage, jukebox,
                waterloggable, light, limitedPlacing);
    }

    /**
     * Parses display transform properties from YAML.
     */
    private static @NotNull FurnitureDisplay parseDisplayTransform(@Nullable ConfigurationSection config) {
        if (config == null) {
            return FurnitureDisplay.defaults();
        }

        String displayTransform = config.getString("display_transform");
        String trackingRotation = config.getString("tracking_rotation");

        double[] translation = null;
        List<Double> transList = config.getDoubleList("translation");
        if (transList.size() >= 3) {
            translation = new double[]{transList.get(0), transList.get(1), transList.get(2)};
        }

        double[] scale = null;
        List<Double> scaleList = config.getDoubleList("scale");
        if (scaleList.size() >= 3) {
            scale = new double[]{scaleList.get(0), scaleList.get(1), scaleList.get(2)};
        }

        FurnitureDisplay.Brightness brightness = null;
        ConfigurationSection brightnessConfig = config.getConfigurationSection("brightness");
        if (brightnessConfig != null) {
            int blockLight = brightnessConfig.getInt("block_light", 0);
            int skyLight = brightnessConfig.getInt("sky_light", 15);
            brightness = new FurnitureDisplay.Brightness(blockLight, skyLight);
        }

        Float shadowRadius = config.contains("shadow_radius") ? (float) config.getDouble("shadow_radius") : null;
        Float shadowStrength = config.contains("shadow_strength") ? (float) config.getDouble("shadow_strength") : null;
        Float viewRange = config.contains("view_range") ? (float) config.getDouble("view_range") : null;
        Float displayWidth = config.contains("display_width") ? (float) config.getDouble("display_width") : null;
        Float displayHeight = config.contains("display_height") ? (float) config.getDouble("display_height") : null;

        return new FurnitureDisplay(
                displayTransform, trackingRotation, translation, scale,
                brightness, shadowRadius, shadowStrength, viewRange,
                displayWidth, displayHeight);
    }

    /**
     * Parses display properties from YAML (same as blocks).
     */
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

    /**
     * Parses model definition from YAML (same as blocks).
     */
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

    /**
     * Parses feature definitions from YAML.
     */
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

    private static @NotNull Key resolveKey(@NotNull String fallbackNamespace, @NotNull String value) {
        int separator = value.indexOf(':');
        if (separator < 0) {
            return Key.key(fallbackNamespace, value);
        }
        return Key.key(value.substring(0, separator), value.substring(separator + 1));
    }
}
