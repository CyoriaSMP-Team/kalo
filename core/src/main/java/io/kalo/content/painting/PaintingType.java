package io.kalo.content.painting;

import com.google.gson.JsonObject;
import io.kalo.content.ContentType;
import io.kalo.content.PackContext;
import io.kalo.content.feature.FeatureBuilder;
import io.kalo.content.item.definition.BedrockOptions;
import io.kalo.content.painting.definition.PaintingDefinition;
import io.kalo.manager.RegistryManager;
import io.kalo.pack.Json;
import io.kalo.pack.ResourcePack;
import io.kalo.registry.Registries;
import io.kalo.utils.Constants;
import io.kalo.utils.Plugins;
import net.kyori.adventure.key.Key;
import org.bukkit.configuration.ConfigurationSection;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.logging.Level;

/**
 * Handles custom paintings: parses YAML, registers in registry, compiles to resource pack.
 *
 * <p>Paintings are vanilla painting variants with custom textures. The resource pack
 * generates a painting variant JSON that Minecraft reads to display the painting.</p>
 */
public final class PaintingType implements ContentType<Painting> {
    public static final Key KEY = Key.key(Constants.PLUGIN_ID, "painting");

    @Override
    public @NotNull String id() {
        return "painting";
    }

    @Override
    public @NotNull Class<Painting> clazz() {
        return Painting.class;
    }

    @Override
    public @NotNull Iterable<Painting> contents(@NotNull Registries registries) {
        return registries.painting();
    }

    @Override
    public boolean load(@NotNull PackContext pack, @NotNull Registries registries, @NotNull ConfigurationSection config) {
        Key key = pack.key(config.getName());
        try {
            PaintingDefinition definition = parseDefinition(key, config);
            List<FeatureBuilder> features = parseFeatures(key, config.getConfigurationSection("features"));

            registries.painting().register(key, entry -> {
                entry.key(key).definition(definition).features(features);
            });
            return true;
        } catch (Exception e) {
            Plugins.logger().log(Level.WARNING, "Failed to load painting '" + key.asString() + "'", e);
            return false;
        }
    }

    @Override
    public void compilePack(@NotNull ResourcePack resourcePack, @NotNull Iterable<Painting> contents) {
        JsonObject paintings = new JsonObject();

        for (Painting painting : contents) {
            PaintingDefinition definition = painting.paintingDefinition();

            JsonObject variant = new JsonObject();
            variant.addProperty("asset_id", definition.key().namespace() + ":" + definition.assetId());
            variant.addProperty("width", definition.width());
            variant.addProperty("height", definition.height());

            if (definition.author() != null) {
                variant.addProperty("author", definition.author());
            }
            if (definition.title() != null) {
                variant.addProperty("title", definition.title());
            }

            paintings.add(definition.key().value(), variant);
        }

        if (!paintings.isEmpty()) {
            JsonObject root = new JsonObject();
            root.add("minecraft:painting_variant", paintings);

            String path = "assets/" + Constants.PLUGIN_ID + "/registries/painting_variant.json";
            resourcePack.file(path, Json.writable(root));
        }
    }

    private static @NotNull PaintingDefinition parseDefinition(@NotNull Key key, @NotNull ConfigurationSection config) {
        PaintingDefinition.Builder builder = PaintingDefinition.builder(key);

        builder.width(config.getInt("width", 1));
        builder.height(config.getInt("height", 1));
        builder.assetId(Objects.requireNonNull(config.getString("asset_id"),
                "painting '" + key.asString() + "' needs an asset_id"));
        builder.author(config.getString("author"));
        builder.title(config.getString("title"));
        builder.animated(config.getBoolean("animated", false));
        builder.frameDuration(config.getInt("frame_duration", 20));

        ConfigurationSection bedrock = config.getConfigurationSection("bedrock");
        if (bedrock != null) {
            builder.bedrock(new BedrockOptions(
                    bedrock.getBoolean("enabled", true),
                    bedrock.getString("icon")));
        }

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
