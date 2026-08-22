package io.kalo.content.musicdisc;

import com.google.gson.JsonObject;
import io.kalo.content.ContentType;
import io.kalo.content.PackContext;
import io.kalo.content.feature.FeatureBuilder;
import io.kalo.content.item.definition.BedrockOptions;
import io.kalo.content.musicdisc.definition.MusicDiscDefinition;
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
 * Handles custom music discs: parses YAML, registers in registry, compiles to resource pack.
 *
 * <p>Music discs are jukebox-playable items with custom sounds. The resource pack
 * generates the item definition and jukebox playable component.</p>
 */
public final class MusicDiscType implements ContentType<MusicDisc> {
    public static final Key KEY = Key.key(Constants.PLUGIN_ID, "music_disc");

    @Override
    public @NotNull String id() {
        return "music_disc";
    }

    @Override
    public @NotNull Class<MusicDisc> clazz() {
        return MusicDisc.class;
    }

    @Override
    public @NotNull Iterable<MusicDisc> contents(@NotNull Registries registries) {
        return registries.musicDisc();
    }

    @Override
    public boolean load(@NotNull PackContext pack, @NotNull Registries registries, @NotNull ConfigurationSection config) {
        Key key = pack.key(config.getName());
        try {
            MusicDiscDefinition definition = parseDefinition(key, config);
            List<FeatureBuilder> features = parseFeatures(key, config.getConfigurationSection("features"));

            registries.musicDisc().register(key, entry -> {
                entry.key(key).definition(definition).features(features);
            });
            return true;
        } catch (Exception e) {
            Plugins.logger().log(Level.WARNING, "Failed to load music disc '" + key.asString() + "'", e);
            return false;
        }
    }

    @Override
    public void compilePack(@NotNull ResourcePack resourcePack, @NotNull Iterable<MusicDisc> contents) {
        // Music disc items are compiled through the item compiler
        // This method is for any disc-specific pack assets (sounds, lang entries)
    }

    private static @NotNull MusicDiscDefinition parseDefinition(@NotNull Key key, @NotNull ConfigurationSection config) {
        MusicDiscDefinition.Builder builder = MusicDiscDefinition.builder(key);

        String soundStr = config.getString("sound");
        if (soundStr != null) {
            int sep = soundStr.indexOf(':');
            if (sep >= 0) {
                builder.sound(Key.key(soundStr.substring(0, sep), soundStr.substring(sep + 1)));
            } else {
                builder.sound(Key.key(key.namespace(), soundStr));
            }
        }

        builder.description(Objects.requireNonNull(config.getString("description"),
                "music disc '" + key.asString() + "' needs a description"));
        builder.duration(config.getInt("duration", 60));
        builder.comparatorOutput(config.getInt("comparator_output", 7));

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
