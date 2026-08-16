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
import io.kalo.utils.Plugins;
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
            sounds.put(key, parse(key, config));
            return true;
        } catch (Exception e) {
            Plugins.logger().log(Level.WARNING,
                    "Failed to load sound '" + key.asString() + "': " + e.getMessage(), e);
            return false;
        }
    }

    static @NotNull SoundDefinition parse(@NotNull Key key, @NotNull ConfigurationSection config) {
        List<SoundDefinition.SoundFile> files = new ArrayList<>();

        for (Object entry : config.getList("sounds", List.of())) {
            if (entry instanceof String path) {
                files.add(SoundDefinition.SoundFile.of(resolve(key.namespace(), path)));
            } else if (entry instanceof Map<?, ?> map) {
                Object file = map.get("file");
                if (file == null) {
                    throw new IllegalArgumentException("a sound entry has no file");
                }
                files.add(new SoundDefinition.SoundFile(
                        resolve(key.namespace(), file.toString()),
                        number(map.get("volume"), 1.0f),
                        number(map.get("pitch"), 1.0f),
                        (int) number(map.get("weight"), 1f)));
            }
        }

        if (files.isEmpty()) {
            throw new IllegalArgumentException("a sound needs at least one entry under 'sounds'");
        }

        String category = config.getString("category", "master");
        return new SoundDefinition(key, files, config.getString("subtitle"),
                SoundCategory.fromId(category));
    }

    private static float number(Object value, float fallback) {
        return value instanceof Number n ? n.floatValue() : fallback;
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
