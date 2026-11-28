package io.kalo.content.emote;

import io.kalo.content.ContentType;
import io.kalo.content.PackContext;
import io.kalo.content.feature.FeatureBuilder;
import io.kalo.content.item.definition.BedrockOptions;
import io.kalo.content.item.definition.ModelDefinition;
import io.kalo.content.item.ItemType;
import io.kalo.pack.ResourcePack;
import io.kalo.platform.java.JavaRigCompiler;
import io.kalo.registry.Registries;
import io.kalo.utils.Constants;
import io.kalo.utils.Plugins;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.configuration.ConfigurationSection;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Loads {@code type: emote} content.
 *
 * <pre>
 * wave:
 *   type: emote
 *   name: "&lt;yellow&gt;Wave"
 *   duration: 2.0
 *   loop: true
 *   model:
 *     blockbench: models/emote/wave.bbmodel
 * </pre>
 *
 * <p>Kalo's emote slice renders an authored prop around the player; it does not
 * re-skin the player body.</p>
 */
public final class EmoteType implements ContentType<Emote> {
    public static final Key KEY = Key.key(Constants.PLUGIN_ID, "emote");

    @Override public @NotNull String id() { return "emote"; }
    @Override public @NotNull Class<Emote> clazz() { return Emote.class; }
    @Override public @NotNull Iterable<Emote> contents(@NotNull Registries registries) {
        return registries.emote();
    }

    @Override
    public boolean load(@NotNull PackContext pack, @NotNull Registries registries,
                        @NotNull ConfigurationSection config) {
        Key key = pack.key(config.getName());
        try {
            EmoteDefinition definition = parseDefinition(key, config);
            ModelDefinition.Blockbench blockbench =
                    (ModelDefinition.Blockbench) definition.model();
            io.kalo.model.ModelIR imported = io.kalo.model.ModelRegistry.load(
                    pack.folder().toPath(), blockbench.source());
            io.kalo.model.ModelDiagnostics.log(Plugins.logger(),
                    "Blockbench emote model " + blockbench.source().asString(),
                    imported.diagnostics());
            List<FeatureBuilder> features = ItemType.parseFeatures(
                    key, config.getConfigurationSection("features"));
            registries.emote().register(key, entry -> entry.key(key)
                    .definition(definition).features(features));
            return true;
        } catch (Exception error) {
            Plugins.logger().warning("Failed to load emote '" + key.asString() + "': "
                    + error.getMessage());
            return false;
        }
    }

    @Override
    public void compilePack(@NotNull ResourcePack pack, @NotNull Iterable<Emote> contents) {
        JavaRigCompiler.compileEmotes(pack, contents);
    }

    static @NotNull EmoteDefinition parseDefinition(@NotNull Key key,
                                                    @NotNull ConfigurationSection config) {
        Component name = config.getString("name") != null
                ? MiniMessage.miniMessage().deserialize(config.getString("name"))
                : Component.text(humanize(key.value()));
        return new EmoteDefinition(key, name,
                parseModel(key, config.getConfigurationSection("model")),
                config.getDouble("duration", 3.0),
                config.getBoolean("loop", true),
                config.getDouble("scale", 1.0),
                config.getDouble("y-offset", 0.0),
                config.getString("permission"),
                new BedrockOptions(config.getBoolean("bedrock.enabled", true),
                        config.getString("bedrock.icon")),
                EmoteDefinition.RigMode.parse(config.getString("rig", "prop")),
                config.getString("skin") == null ? null
                        : resolveKey(key.namespace(), config.getString("skin")),
                parsePerformers(config));
    }

    private static @NotNull java.util.List<EmoteDefinition.Performer> parsePerformers(
            @NotNull ConfigurationSection config) {
        if (!config.isList("performers")) {
            return EmoteDefinition.defaultPerformers();
        }
        java.util.List<EmoteDefinition.Performer> performers = new java.util.ArrayList<>();
        for (java.util.Map<?, ?> raw : config.getMapList("performers")) {
            String name = raw.get("name") == null ? "performer" : raw.get("name").toString();
            double x = 0;
            double y = 0;
            double z = 0;
            Object offset = raw.get("offset");
            if (offset instanceof java.util.Map<?, ?> map) {
                x = number(map.get("x"));
                y = number(map.get("y"));
                z = number(map.get("z"));
            }
            performers.add(new EmoteDefinition.Performer(name, x, y, z));
        }
        if (performers.isEmpty()) {
            throw new IllegalArgumentException("emote performers list must not be empty");
        }
        return java.util.List.copyOf(performers);
    }

    private static double number(@Nullable Object value) {
        return value instanceof Number number ? number.doubleValue() : 0.0;
    }

    private static @NotNull ModelDefinition parseModel(@NotNull Key key,
                                                       @Nullable ConfigurationSection model) {
        if (model == null) {
            throw new IllegalArgumentException("emote requires a 'model' section");
        }
        String source = model.getString("blockbench");
        if (source == null || !source.toLowerCase(Locale.ROOT).endsWith(".bbmodel")) {
            throw new IllegalArgumentException(
                    "emote model must declare a 'blockbench' .bbmodel path");
        }
        Map<String, Key> textures = new LinkedHashMap<>();
        ConfigurationSection overrides = model.getConfigurationSection("textures");
        if (overrides != null) {
            for (String id : overrides.getKeys(false)) {
                textures.put(id, resolveKey(key.namespace(), overrides.getString(id, id)));
            }
        }
        return new ModelDefinition.Blockbench(resolveKey(key.namespace(), source), textures);
    }

    static @NotNull Key resolveKey(@NotNull String fallbackNamespace, @NotNull String value) {
        int separator = value.indexOf(':');
        return separator < 0 ? Key.key(fallbackNamespace, value)
                : Key.key(value.substring(0, separator), value.substring(separator + 1));
    }

    private static @NotNull String humanize(@NotNull String value) {
        String[] parts = value.split("[_/]");
        StringBuilder result = new StringBuilder();
        for (String part : parts) {
            if (part.isEmpty()) {
                continue;
            }
            if (!result.isEmpty()) {
                result.append(' ');
            }
            result.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        }
        return result.isEmpty() ? value : result.toString();
    }
}
