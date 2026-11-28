package io.kalo.content.vehicle;

import io.kalo.content.ContentType;
import io.kalo.content.PackContext;
import io.kalo.content.feature.FeatureBuilder;
import io.kalo.content.item.ItemType;
import io.kalo.content.item.definition.BedrockOptions;
import io.kalo.content.item.definition.ModelDefinition;
import io.kalo.pack.ResourcePack;
import io.kalo.content.vehicle.VehicleMedium;
import io.kalo.platform.java.JavaRigCompiler;
import io.kalo.registry.Registries;
import io.kalo.utils.Constants;
import io.kalo.utils.Plugins;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.EntityType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Loads {@code type: vehicle} content.
 *
 * <pre>
 * speeder:
 *   type: vehicle
 *   name: "&lt;aqua&gt;Speeder"
 *   base: armor_stand
 *   speed: 0.4
 *   turn-speed: 9
 *   model:
 *     blockbench: models/vehicle/speeder.bbmodel
 * </pre>
 */
public final class VehicleType implements ContentType<Vehicle> {
    public static final Key KEY = Key.key(Constants.PLUGIN_ID, "vehicle");

    @Override public @NotNull String id() { return "vehicle"; }
    @Override public @NotNull Class<Vehicle> clazz() { return Vehicle.class; }
    @Override public @NotNull Iterable<Vehicle> contents(@NotNull Registries registries) {
        return registries.vehicle();
    }

    @Override
    public boolean load(@NotNull PackContext pack, @NotNull Registries registries,
                        @NotNull ConfigurationSection config) {
        Key key = pack.key(config.getName());
        try {
            VehicleDefinition definition = parseDefinition(key, config);
            ModelDefinition.Blockbench blockbench =
                    (ModelDefinition.Blockbench) definition.model();
            io.kalo.model.ModelIR imported = io.kalo.model.ModelRegistry.load(
                    pack.folder().toPath(), blockbench.source());
            io.kalo.model.ModelDiagnostics.log(Plugins.logger(),
                    "Blockbench vehicle model " + blockbench.source().asString(),
                    imported.diagnostics());
            List<FeatureBuilder> features = ItemType.parseFeatures(
                    key, config.getConfigurationSection("features"));
            registries.vehicle().register(key, entry -> entry.key(key)
                    .definition(definition).features(features));
            return true;
        } catch (Exception error) {
            Plugins.logger().warning("Failed to load vehicle '" + key.asString() + "': "
                    + error.getMessage());
            return false;
        }
    }

    @Override
    public void compilePack(@NotNull ResourcePack pack, @NotNull Iterable<Vehicle> contents) {
        JavaRigCompiler.compileVehicles(pack, contents);
    }

    static @NotNull VehicleDefinition parseDefinition(@NotNull Key key,
                                                      @NotNull ConfigurationSection config) {
        Component name = config.getString("name") != null
                ? MiniMessage.miniMessage().deserialize(config.getString("name"))
                : Component.text(humanize(key.value()));
        return new VehicleDefinition(key, name,
                parseBase(config.getString("base", "armor_stand")),
                parseModel(key, config.getConfigurationSection("model")),
                config.getDouble("speed", 0.35),
                config.getDouble("turn-speed", 8.0),
                config.getDouble("scale", 1.0),
                config.getDouble("y-offset", 0.0),
                parseSeats(config),
                parsePhysics(config),
                new BedrockOptions(config.getBoolean("bedrock.enabled", true),
                        config.getString("bedrock.icon")));
    }

    private static @NotNull VehicleDefinition.Physics parsePhysics(
            @NotNull ConfigurationSection config) {
        VehicleDefinition.Physics defaults = VehicleDefinition.Physics.defaults();
        String medium = config.getString("medium");
        return new VehicleDefinition.Physics(
                medium == null ? defaults.medium() : VehicleMedium.parse(medium),
                config.getDouble("buoyancy", defaults.buoyancy()),
                config.getDouble("gravity", defaults.gravity()),
                config.getDouble("climb-speed", defaults.climbSpeed()),
                config.getBoolean("collision", defaults.collision()));
    }

    private static @NotNull java.util.List<VehicleDefinition.Seat> parseSeats(
            @NotNull ConfigurationSection config) {
        if (config.isList("seats")) {
            java.util.List<VehicleDefinition.Seat> seats = new java.util.ArrayList<>();
            for (java.util.Map<?, ?> raw : config.getMapList("seats")) {
                seats.add(new VehicleDefinition.Seat(
                        number(raw.get("x")), number(raw.get("y")), number(raw.get("z"))));
            }
            if (seats.isEmpty()) {
                throw new IllegalArgumentException("vehicle 'seats' list must not be empty");
            }
            return java.util.List.copyOf(seats);
        }
        return VehicleDefinition.defaultSeats(config.getInt("seats", 1));
    }

    private static double number(@Nullable Object value) {
        return value instanceof Number number ? number.doubleValue() : 0.0;
    }

    private static @NotNull EntityType parseBase(@NotNull String value) {
        String name = value.toLowerCase(Locale.ROOT);
        int colon = name.indexOf(':');
        if (colon >= 0) {
            name = name.substring(colon + 1);
        }
        try {
            return EntityType.valueOf(name.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException unknown) {
            throw new IllegalArgumentException("unknown vehicle base '" + value + "'");
        }
    }

    private static @NotNull ModelDefinition parseModel(@NotNull Key key,
                                                       @Nullable ConfigurationSection model) {
        if (model == null) {
            throw new IllegalArgumentException("vehicle requires a 'model' section");
        }
        String source = model.getString("blockbench");
        if (source == null || !source.toLowerCase(Locale.ROOT).endsWith(".bbmodel")) {
            throw new IllegalArgumentException(
                    "vehicle model must declare a 'blockbench' .bbmodel path");
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
