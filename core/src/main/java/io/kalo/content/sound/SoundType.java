package io.kalo.content.sound;

import io.kalo.content.ContentType;
import io.kalo.content.PackContext;
import io.kalo.content.item.Item;
import io.kalo.content.sound.definition.SoundCategory;
import io.kalo.content.sound.definition.SoundDefinition;
import io.kalo.pack.ResourcePack;
import io.kalo.platform.java.JavaSoundCompiler;
import io.kalo.registry.Registries;
import io.kalo.utils.Constants;
import net.kyori.adventure.key.Key;
import org.bukkit.configuration.ConfigurationSection;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * Loads custom sound events from pack YAML.
 *
 * <pre>
 * cave_wind:
 *   type: sound
 *   category: ambient
 *   subtitle: "subtitles.mypack.cave_wind"
 *   sounds:
 *     - "ambient/cave_wind"
 *     - file: "ambient/cave_wind_alt"
 *       volume: 0.8
 * </pre>
 *
 * <p>Like recipes, sounds are not {@code Content}: there is no key to give a player and no
 * item form, only an entry in the pack. They are held here and compiled with the rest.</p>
 */
public final class SoundType implements ContentType<Item> {
    public static final Key KEY = Key.key(Constants.PLUGIN_ID, "sound");
    private static final java.util.logging.Logger LOGGER =
            java.util.logging.Logger.getLogger(SoundType.class.getName());

    private final Map<Key, SoundDefinition> sounds = new ConcurrentHashMap<>();

    @Override
    public @NotNull String id() {
        return "sound";
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
            SoundDefinition definition = parse(key, config);
            if (sounds.putIfAbsent(key, definition) != null) {
                LOGGER.warning("Duplicate sound '" + key.asString() + "'; keeping the first definition");
                return false;
            }
            return true;
        } catch (Exception e) {
            LOGGER.log(Level.WARNING,
                    "Failed to load sound '" + key.asString() + "': " + e.getMessage(), e);
            return false;
        }
    }

    static @NotNull SoundDefinition parse(@NotNull Key key, @NotNull ConfigurationSection config) {
        List<SoundDefinition.SoundFile> files = new ArrayList<>();

        for (Object entry : config.getList("sounds", List.of())) {
            if (entry instanceof String path) {
                if (path.isBlank()) {
                    throw new IllegalArgumentException("a sound file path cannot be blank");
                }
                files.add(SoundDefinition.SoundFile.of(resolve(key.namespace(), path)));
            } else if (entry instanceof Map<?, ?> map) {
                Object file = map.get("file");
                if (file == null) {
                    throw new IllegalArgumentException("a sound entry has no file");
                }
                files.add(new SoundDefinition.SoundFile(
                        resolve(key.namespace(), file.toString()),
                        number(map, "volume", 1.0f),
                        number(map, "pitch", 1.0f),
                        integer(map, "weight", 1)));
            } else {
                throw new IllegalArgumentException(
                        "a sound entry must be a file path or a section, got "
                                + (entry == null ? "null" : entry.getClass().getSimpleName()));
            }
        }

        if (files.isEmpty()) {
            throw new IllegalArgumentException("a sound needs at least one entry under 'sounds'");
        }

        String category = config.getString("category", "master");
        return new SoundDefinition(key, files, config.getString("subtitle"),
                SoundCategory.fromId(category));
    }

    private static float number(@NotNull Map<?, ?> values, @NotNull String key, float fallback) {
        Object value = values.get(key);
        if (value == null) {
            return fallback;
        }
        if (!(value instanceof Number number)) {
            throw new IllegalArgumentException("sound " + key + " must be a number");
        }
        return number.floatValue();
    }

    private static int integer(@NotNull Map<?, ?> values, @NotNull String key, int fallback) {
        Object value = values.get(key);
        if (value == null) {
            return fallback;
        }
        if (!(value instanceof Number number)) {
            throw new IllegalArgumentException("sound " + key + " must be an integer");
        }
        double numeric = number.doubleValue();
        if (!Double.isFinite(numeric) || numeric != Math.rint(numeric)
                || numeric < Integer.MIN_VALUE || numeric > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("sound " + key + " must be an integer");
        }
        return (int) numeric;
    }

    private static @NotNull Key resolve(@NotNull String fallbackNamespace, @NotNull String value) {
        int separator = value.indexOf(':');
        return separator < 0
                ? Key.key(fallbackNamespace, value)
                : Key.key(value.substring(0, separator), value.substring(separator + 1));
    }

    @Override
    public void compilePack(@NotNull ResourcePack resourcePack, @NotNull Iterable<Item> contents) {
        JavaSoundCompiler.compileSounds(resourcePack, new ArrayList<>(sounds.values()));
    }

    /** Compiles even with no registered content, since sounds live outside the registries. */
    public void compile(@NotNull ResourcePack resourcePack) {
        JavaSoundCompiler.compileSounds(resourcePack, new ArrayList<>(sounds.values()));
    }

    public void clear() {
        sounds.clear();
    }

    public int size() {
        return sounds.size();
    }
}
