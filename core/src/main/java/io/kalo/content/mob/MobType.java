package io.kalo.content.mob;

import io.kalo.content.ContentType;
import io.kalo.content.PackContext;
import io.kalo.content.feature.FeatureBuilder;
import io.kalo.content.item.definition.BedrockOptions;
import io.kalo.content.item.definition.ModelDefinition;
import io.kalo.pack.ResourcePack;
import io.kalo.platform.java.JavaMobCompiler;
import io.kalo.registry.Registries;
import io.kalo.utils.Constants;
import io.kalo.utils.Plugins;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Loads {@code type: mob} content from pack YAML.
 *
 * <pre>
 * ruby_golem:
 *   type: mob
 *   base: zombie
 *   name: "&lt;red&gt;Ruby Golem"
 *   health: 40
 *   width: 0.6
 *   height: 1.9
 *   model:
 *     blockbench: models/mob/ruby_golem.bbmodel
 *     textures:
 *       "0": entity/ruby_golem
 * </pre>
 *
 * <p>The spawned entity is the real base mob; its vanilla model is hidden while the
 * custom model is drawn by a display rig on Java and a client entity on Bedrock.</p>
 */
public final class MobType implements ContentType<Mob> {
    public static final Key KEY = Key.key(Constants.PLUGIN_ID, "mob");

    @Override
    public @NotNull String id() {
        return "mob";
    }

    @Override
    public @NotNull Class<Mob> clazz() {
        return Mob.class;
    }

    @Override
    public @NotNull Iterable<Mob> contents(@NotNull Registries registries) {
        return registries.mob();
    }

    @Override
    public boolean load(@NotNull PackContext pack, @NotNull Registries registries,
                        @NotNull ConfigurationSection config) {
        Key key = pack.key(config.getName());
        try {
            MobDefinition definition = parseDefinition(key, config);
            if (definition.model() instanceof ModelDefinition.Blockbench blockbench) {
                io.kalo.model.ModelIR imported = io.kalo.model.ModelRegistry.load(
                        pack.folder().toPath(), blockbench.source());
                io.kalo.model.ModelDiagnostics.log(Plugins.logger(),
                        "Blockbench mob model " + blockbench.source().asString(),
                        imported.diagnostics());
            }
            List<FeatureBuilder> features = io.kalo.content.item.ItemType.parseFeatures(
                    key, config.getConfigurationSection("features"));
            registries.mob().register(key, entry -> entry.key(key)
                    .definition(definition).features(features));
            return true;
        } catch (Exception error) {
            Plugins.logger().warning("Failed to load mob '" + key.asString() + "': "
                    + error.getMessage());
            return false;
        }
    }

    @Override
    public void compilePack(@NotNull ResourcePack pack, @NotNull Iterable<Mob> contents) {
        JavaMobCompiler.compile(pack, contents);
    }

    static @NotNull MobDefinition parseDefinition(@NotNull Key key,
                                                  @NotNull ConfigurationSection config) {
        EntityType base = parseBase(config.getString("base", "zombie"));
        Component name = config.getString("name") != null
                ? MiniMessage.miniMessage().deserialize(config.getString("name"))
                : Component.text(humanize(key.value()));
        BedrockOptions bedrock = new BedrockOptions(
                config.getBoolean("bedrock.enabled", true),
                config.getString("bedrock.icon"));
        ModelDefinition model = parseModel(key, config.getConfigurationSection("model"));
        ConfigurationSection animation = config.getConfigurationSection("animation");
        io.kalo.content.animation.RigAnimation rigAnimation = animation == null
                ? io.kalo.content.animation.RigAnimation.none()
                : new io.kalo.content.animation.RigAnimation(
                        animation.getString("idle"), animation.getString("interact"));
        return MobDefinition.builder(key)
                .displayName(name)
                .base(base)
                .health(config.getDouble("health", 20))
                .size(config.getDouble("width", 0.6), config.getDouble("height", 1.8))
                .model(model)
                .animation(rigAnimation)
                .bedrock(bedrock)
                .build();
    }

    private static @NotNull EntityType parseBase(@Nullable String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("mob 'base' is required");
        }
        String name = value.trim().toLowerCase(Locale.ROOT);
        int colon = name.indexOf(':');
        if (colon >= 0) {
            name = name.substring(colon + 1);
        }
        EntityType type;
        try {
            type = EntityType.valueOf(name.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException unknown) {
            throw new IllegalArgumentException("unknown mob base '" + value + "'");
        }
        Class<?> clazz = type.getEntityClass();
        if (clazz == null || !LivingEntity.class.isAssignableFrom(clazz)
                || !type.isSpawnable() || type == EntityType.PLAYER) {
            throw new IllegalArgumentException("mob base '" + value
                    + "' is not a spawnable living entity");
        }
        return type;
    }

    private static @Nullable ModelDefinition parseModel(@NotNull Key key,
                                                        @Nullable ConfigurationSection model) {
        if (model == null) {
            return null;
        }
        String source = model.getString("blockbench");
        if (source == null || source.isBlank()) {
            throw new IllegalArgumentException(
                    "mob model must declare a 'blockbench' .bbmodel path");
        }
        if (!source.toLowerCase(Locale.ROOT).endsWith(".bbmodel")) {
            throw new IllegalArgumentException(
                    "mob model.blockbench must end in .bbmodel, got '" + source + "'");
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

    private static @NotNull Key resolveKey(@NotNull String fallbackNamespace,
                                           @NotNull String value) {
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
