package io.kalo.content.liquid;

import io.kalo.content.ContentType;
import io.kalo.content.PackContext;
import io.kalo.content.item.Item;
import io.kalo.content.liquid.definition.LiquidDefinition;
import io.kalo.pack.ResourcePack;
import io.kalo.registry.Registries;
import io.kalo.utils.Constants;
import net.kyori.adventure.key.Key;
import org.bukkit.configuration.ConfigurationSection;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Loads marked-liquid definitions from pack YAML.
 *
 * <pre>
 * acid:
 *   type: liquid
 *   base: water
 *   color: "#3FBF4A"
 *   effect: POISON
 *   amplifier: 1
 *   damage: 1.0
 *   fireproof: false
 * </pre>
 */
public final class LiquidType implements ContentType<Item> {
    public static final Key KEY = Key.key(Constants.PLUGIN_ID, "liquid");

    private static final Logger LOGGER = Logger.getLogger(LiquidType.class.getName());
    private final Map<Key, LiquidDefinition> liquids = new ConcurrentHashMap<>();

    @Override
    public @NotNull String id() {
        return "liquid";
    }

    @Override
    public @NotNull Class<Item> clazz() {
        return Item.class;
    }

    @Override
    public @NotNull Iterable<Item> contents(@NotNull Registries registries) {
        return List.of();
    }

    @Override
    public boolean load(@NotNull PackContext pack, @NotNull Registries registries,
                        @NotNull ConfigurationSection config) {
        Key key = pack.key(config.getName());
        try {
            LiquidDefinition definition = parse(key, config);
            if (liquids.putIfAbsent(key, definition) != null) {
                LOGGER.warning("Duplicate liquid '" + key.asString()
                        + "'; keeping the first definition");
                return false;
            }
            return true;
        } catch (Exception error) {
            LOGGER.log(Level.WARNING,
                    "Failed to load liquid '" + key.asString() + "': " + error.getMessage(),
                    error);
            return false;
        }
    }

    public static @NotNull LiquidDefinition parse(@NotNull Key key,
                                                  @NotNull ConfigurationSection config) {
        String name = config.getString("name", key.value());
        LiquidDefinition.Base base = LiquidDefinition.Base.parse(
                config.getString("base", "water"));
        Integer color = null;
        String rawColor = config.getString("color");
        if (rawColor != null && !rawColor.isBlank()) {
            color = LiquidDefinition.parseColor(rawColor);
        }
        String effect = config.getString("effect");
        int amplifier = Math.max(0, Math.min(255, config.getInt("amplifier", 0)));
        double damage = Math.max(0, Math.min(100, config.getDouble("damage", 0)));
        boolean fireproof = config.getBoolean("fireproof", false);
        return new LiquidDefinition(key, name, base, color, effect, amplifier, damage,
                fireproof);
    }

    /** Exposed for tests and programmatic registration. */
    public void put(@NotNull LiquidDefinition definition) {
        liquids.put(definition.key(), definition);
    }

    public @NotNull Map<Key, LiquidDefinition> liquids() {
        return Map.copyOf(liquids);
    }

    public @NotNull List<LiquidDefinition> ordered() {
        List<LiquidDefinition> ordered = new ArrayList<>(liquids.values());
        ordered.sort(Comparator.comparing(definition -> definition.key().asString()));
        return List.copyOf(ordered);
    }

    public int size() {
        return liquids.size();
    }

    public void clear() {
        liquids.clear();
    }

    @Override
    public void compilePack(@NotNull ResourcePack resourcePack,
                            @NotNull Iterable<Item> contents) {
        // Liquid rules are runtime state; the optional color is a world data pack.
    }
}
