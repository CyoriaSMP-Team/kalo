package io.kalo.content.vfx;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.kalo.content.ContentType;
import io.kalo.content.PackContext;
import io.kalo.content.item.Item;
import io.kalo.content.vfx.definition.VfxDefinition;
import io.kalo.content.vfx.definition.VfxPresets;
import io.kalo.content.vfx.definition.VfxRecipe;
import io.kalo.pack.ResourcePack;
import io.kalo.platform.java.VfxSpritePainter;
import io.kalo.registry.Registries;
import io.kalo.utils.Constants;
import net.kyori.adventure.key.Key;
import org.bukkit.configuration.ConfigurationSection;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Loads VFX definitions from pack YAML and paints their Java item-display sprites.
 *
 * <pre>
 * frost:
 *   type: vfx
 *   preset: frost_nova
 *
 * burst:
 *   type: vfx
 *   file: vfx/burst.json
 *
 * spark:
 *   type: vfx
 *   recipe:
 *     duration: 30
 *     layers:
 *       - { type: motes, kind: streak, start: 0, end: 20, burst: 40,
 *           at: [0, 1, 0], shape: sphere, speed: [0.3, 0.6] }
 * </pre>
 */
public final class VfxType implements ContentType<Item> {
    public static final Key KEY = Key.key(Constants.PLUGIN_ID, "vfx");

    private static final Logger LOGGER = Logger.getLogger(VfxType.class.getName());
    private static final long MAX_JSON_BYTES = 256L * 1024L;
    private static final int MAX_EFFECTS_PER_NAMESPACE = 48;

    private final Map<Key, VfxDefinition> effects = new ConcurrentHashMap<>();

    @Override
    public @NotNull String id() {
        return "vfx";
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
            VfxDefinition definition = parse(pack, key, config);
            if (effects.putIfAbsent(key, definition) != null) {
                LOGGER.warning("Duplicate VFX '" + key.asString()
                        + "'; keeping the first definition");
                return false;
            }
            long namespaced = effects.keySet().stream()
                    .filter(existing -> existing.namespace().equals(key.namespace()))
                    .count();
            if (namespaced > MAX_EFFECTS_PER_NAMESPACE) {
                effects.remove(key);
                throw new IllegalArgumentException("a namespace may define at most "
                        + MAX_EFFECTS_PER_NAMESPACE + " VFX effects");
            }
            for (String diagnostic : definition.recipe().diagnostics()) {
                LOGGER.warning("VFX '" + key.asString() + "': " + diagnostic);
            }
            return true;
        } catch (Exception error) {
            LOGGER.log(Level.WARNING,
                    "Failed to load VFX '" + key.asString() + "': " + error.getMessage(),
                    error);
            return false;
        }
    }

    public static @NotNull VfxDefinition parse(@NotNull PackContext pack,
                                               @NotNull Key key,
                                               @NotNull ConfigurationSection config) {
        String preset = config.getString("preset");
        String file = config.getString("file");
        ConfigurationSection recipeSection = config.getConfigurationSection("recipe");
        int sources = (preset == null || preset.isBlank() ? 0 : 1)
                + (file == null || file.isBlank() ? 0 : 1)
                + (recipeSection == null ? 0 : 1);
        if (sources != 1) {
            throw new IllegalArgumentException(
                    "VFX needs exactly one of preset, file or recipe");
        }

        VfxRecipe recipe;
        String presetName = null;
        if (preset != null && !preset.isBlank()) {
            String normalizedPreset = preset.trim().toLowerCase(java.util.Locale.ROOT);
            presetName = normalizedPreset;
            recipe = VfxPresets.recipe(normalizedPreset)
                    .orElseThrow(() -> new IllegalArgumentException(
                            "unknown VFX preset '" + normalizedPreset + "'"));
        } else if (recipeSection != null) {
            recipe = VfxRecipe.parse(recipeSection);
        } else {
            recipe = VfxRecipe.parse(readRecipe(pack, file));
        }

        String name = config.getString("name");
        if (name == null || name.isBlank()) {
            name = presetName == null ? key.value() : VfxPresets.displayName(presetName);
        }
        return new VfxDefinition(key, name, recipe, presetName);
    }

    private static @NotNull JsonObject readRecipe(@NotNull PackContext pack,
                                                  @NotNull String relativePath) {
        Path root = pack.folder().toPath().toAbsolutePath().normalize();
        String path = relativePath;
        if (path.startsWith(pack.namespace() + "/")) {
            path = path.substring(pack.namespace().length() + 1);
        }
        Path file = root.resolve(path).normalize();
        if (!file.startsWith(root)) {
            throw new IllegalArgumentException(
                    "VFX JSON must stay inside the content pack: " + relativePath);
        }
        try {
            if (!Files.isRegularFile(file)) {
                throw new IllegalArgumentException("VFX JSON not found: " + relativePath);
            }
            if (!file.toRealPath().startsWith(root.toRealPath())) {
                throw new IllegalArgumentException(
                        "VFX JSON resolves outside the content pack: " + relativePath);
            }
            if (Files.size(file) > MAX_JSON_BYTES) {
                throw new IllegalArgumentException(
                        "VFX JSON is larger than " + MAX_JSON_BYTES + " bytes");
            }
            JsonElement parsed = JsonParser.parseString(
                    Files.readString(file, StandardCharsets.UTF_8));
            if (!parsed.isJsonObject()) {
                throw new IllegalArgumentException("VFX JSON must be one JSON object");
            }
            JsonObject rootObject = parsed.getAsJsonObject();
            if (rootObject.has("recipe") && rootObject.get("recipe").isJsonObject()) {
                return rootObject.getAsJsonObject("recipe");
            }
            return rootObject;
        } catch (IOException error) {
            throw new IllegalArgumentException(
                    "could not read VFX JSON " + relativePath, error);
        } catch (RuntimeException malformed) {
            throw new IllegalArgumentException(
                    "invalid VFX JSON " + relativePath + ": " + malformed.getMessage(),
                    malformed);
        }
    }

    public @NotNull Map<Key, VfxDefinition> effects() {
        return Map.copyOf(effects);
    }

    /** Exposed for tests and programmatic registration. */
    public void put(@NotNull VfxDefinition definition) {
        effects.put(definition.key(), definition);
    }

    public @NotNull List<VfxDefinition> ordered() {
        List<VfxDefinition> ordered = new ArrayList<>(effects.values());
        ordered.sort(Comparator.comparing(definition -> definition.key().asString()));
        return List.copyOf(ordered);
    }

    public int size() {
        return effects.size();
    }

    public void clear() {
        effects.clear();
    }

    @Override
    public void compilePack(@NotNull ResourcePack resourcePack,
                            @NotNull Iterable<Item> contents) {
        compile(resourcePack);
    }

    /** Paints sprites for every loaded effect; registryless types call this directly. */
    public int compile(@NotNull ResourcePack resourcePack) {
        int sprites = VfxSpritePainter.compile(resourcePack, ordered());
        if (sprites > 0) {
            LOGGER.fine("Generated " + sprites + " VFX sprite(s)");
        }
        return sprites;
    }
}
