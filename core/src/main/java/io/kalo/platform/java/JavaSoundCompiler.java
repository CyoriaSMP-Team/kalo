package io.kalo.platform.java;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import io.kalo.content.sound.definition.SoundDefinition;
import io.kalo.pack.Json;
import io.kalo.pack.ResourcePack;
import net.kyori.adventure.key.Key;
import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.TreeMap;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Writes custom sound events into the pack.
 *
 * <p>Output is one {@code sounds.json} per namespace, which is what vanilla reads:</p>
 *
 * <pre>
 * assets/mypack/sounds.json
 *   {"ambient.cave_wind": {"category":"ambient","subtitle":"...",
 *                          "sounds":[{"name":"mypack:ambient/cave_wind","volume":1.0}]}}
 * </pre>
 *
 * <p>The ogg files themselves ship in the pack's {@code assets/} and are copied like any
 * other asset; this only declares the events that point at them.</p>
 */
public final class JavaSoundCompiler {
    private static final Logger LOGGER = Logger.getLogger(JavaSoundCompiler.class.getName());

    private JavaSoundCompiler() {
    }

    public static void compileSounds(@NotNull ResourcePack pack, @NotNull Iterable<SoundDefinition> sounds) {
        // Grouped by namespace because sounds.json is per namespace, and sorted so an
        // unchanged pack keeps a stable hash.
        Map<String, JsonObject> byNamespace = new TreeMap<>();

        for (SoundDefinition definition : sounds) {
            try {
                byNamespace.computeIfAbsent(definition.key().namespace(), ignored -> new JsonObject())
                        .add(definition.key().value(), soundEvent(definition));
            } catch (Exception e) {
                LOGGER.log(Level.WARNING, "Failed to compile a sound event", e);
            }
        }

        byNamespace.forEach((namespace, json) -> {
            String path = "assets/" + namespace + "/sounds.json";
            io.kalo.pack.Writable existing = pack.file(path);
            if (existing != null) {
                // Same merge rule as every other shared file: a second pass must not erase
                // the first one's events.
                try {
                    JsonObject merged = com.google.gson.JsonParser
                            .parseString(new String(existing.toByteArray(),
                                    java.nio.charset.StandardCharsets.UTF_8))
                            .getAsJsonObject();
                    json.entrySet().forEach(entry -> merged.add(entry.getKey(), entry.getValue()));
                    pack.file(path, Json.writable(merged));
                    return;
                } catch (Exception e) {
                    LOGGER.log(Level.WARNING, "Could not merge into existing " + path, e);
                }
            }
            pack.file(path, Json.writable(json));
        });
    }

    private static @NotNull JsonObject soundEvent(@NotNull SoundDefinition definition) {
        JsonArray files = new JsonArray();
        for (SoundDefinition.SoundFile file : definition.sounds()) {
            JsonObject entry = new JsonObject();
            entry.addProperty("name", file.file().asString());
            entry.addProperty("volume", file.volume());
            entry.addProperty("pitch", file.pitch());
            if (file.weight() > 1) {
                entry.addProperty("weight", file.weight());
            }
            // "stream" is deliberately not set: it is a tradeoff for long music tracks and
            // guessing wrong costs either memory or a playback stutter.
            files.add(entry);
        }

        JsonObject event = new JsonObject();
        event.addProperty("category", definition.category().id());
        if (definition.subtitle() != null) {
            // A subtitle key, not the text: vanilla looks it up in the language file, so
            // the pack's lang entry supplies the wording.
            event.addProperty("subtitle", definition.subtitle());
        }
        event.add("sounds", files);
        return event;
    }

    /** Where a sound file's ogg is expected to live, for validation and asset copying. */
    public static @NotNull String soundPath(@NotNull Key file) {
        return "assets/" + file.namespace() + "/sounds/" + file.value() + ".ogg";
    }
}
