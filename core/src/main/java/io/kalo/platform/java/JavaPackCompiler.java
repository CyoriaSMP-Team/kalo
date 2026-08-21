package io.kalo.platform.java;

import com.google.gson.JsonObject;
import io.kalo.content.item.Item;
import io.kalo.content.item.definition.ItemDefinition;
import io.kalo.content.item.definition.ModelDefinition;
import io.kalo.pack.Json;
import io.kalo.pack.ResourcePack;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.jetbrains.annotations.NotNull;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.TreeMap;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Turns item definitions into Java resource pack assets.
 *
 * <p>Targets the item definition system introduced in 1.21.4 — {@code assets/<ns>/items/}
 * plus the {@code minecraft:item_model} component — not the legacy CustomModelData
 * override system, which has been superseded for two major generations.</p>
 *
 * <p>Output for one sprite item {@code mypack:ruby_sword}:</p>
 * <pre>
 * assets/mypack/items/ruby_sword.json          {"model":{"type":"minecraft:model","model":"mypack:item/ruby_sword"}}
 * assets/mypack/models/item/ruby_sword.json    {"parent":"minecraft:item/generated","textures":{"layer0":"mypack:item/ruby_sword"}}
 * assets/mypack/lang/en_us.json                {"item.mypack.ruby_sword":"Ruby Sword"}
 * </pre>
 */
public final class JavaPackCompiler {
    // Deliberately not Plugins.logger(): the compiler is pure enough to run and be
    // tested without a live plugin instance, and should stay that way.
    private static final Logger LOGGER = Logger.getLogger(JavaPackCompiler.class.getName());

    private static final String DEFAULT_LANGUAGE = "en_us";
    private static final Key GENERATED_PARENT = Key.key("minecraft", "item/generated");

    private JavaPackCompiler() {
    }

    public static void compileItems(@NotNull ResourcePack pack, @NotNull Iterable<Item> items) {
        // Sorted so the emitted lang file is stable between runs, which keeps the pack
        // hash stable and stops clients re-downloading an unchanged pack.
        Map<String, Map<String, String>> translations = new TreeMap<>();

        for (Item item : items) {
            // Everything including definition() is inside the try: one broken item must
            // not cost every other item in the pack its assets.
            try {
                ItemDefinition definition = item.definition();
                compileItem(pack, definition);
                collectTranslation(translations, definition);
            } catch (Exception e) {
                LOGGER.log(Level.WARNING, "Failed to compile pack assets for an item", e);
            }
        }

        if (!translations.isEmpty()) {
            writeTranslations(pack, translations);
        }
    }

    private static void compileItem(@NotNull ResourcePack pack, @NotNull ItemDefinition definition) {
        Key key = definition.key();

        switch (definition.model()) {
            case ModelDefinition.Vanilla ignored -> {
                // Reuses the vanilla item definition already present in the client; the
                // item_model component points straight at it, so nothing to emit.
            }
            case ModelDefinition.Sprite sprite -> {
                Key modelKey = Key.key(key.namespace(), "item/" + key.value());
                pack.file(modelPath(modelKey), Json.writable(generatedModel(sprite.texture())));
                pack.file(itemDefinitionPath(key), Json.writable(itemDefinition(modelKey)));
            }
            case ModelDefinition.Custom custom -> {
                // The model file itself ships with the pack and is copied verbatim by the
                // asset stage; only the definition pointing at it is generated here.
                pack.file(itemDefinitionPath(key), Json.writable(itemDefinition(custom.model())));
            }
        }
    }

    /** {@code assets/<ns>/items/<name>.json} — what {@code item_model} resolves against. */
    private static @NotNull String itemDefinitionPath(@NotNull Key key) {
        return "assets/" + key.namespace() + "/items/" + key.value() + ".json";
    }

    /** {@code assets/<ns>/models/<path>.json} */
    private static @NotNull String modelPath(@NotNull Key key) {
        return "assets/" + key.namespace() + "/models/" + key.value() + ".json";
    }

    private static @NotNull JsonObject itemDefinition(@NotNull Key model) {
        JsonObject modelSection = new JsonObject();
        modelSection.addProperty("type", "minecraft:model");
        modelSection.addProperty("model", model.asString());

        JsonObject root = new JsonObject();
        root.add("model", modelSection);
        return root;
    }

    private static @NotNull JsonObject generatedModel(@NotNull Key texture) {
        JsonObject textures = new JsonObject();
        textures.addProperty("layer0", texture.asString());

        JsonObject root = new JsonObject();
        root.addProperty("parent", GENERATED_PARENT.asString());
        root.add("textures", textures);
        return root;
    }

    private static void collectTranslation(@NotNull Map<String, Map<String, String>> translations,
                                           @NotNull ItemDefinition definition) {
        Component name = definition.display().name();
        // An item with an explicit name carries it on the stack itself and needs no
        // translation entry; one without would otherwise render as a raw key.
        String value = name != null
                ? PlainTextComponentSerializer.plainText().serialize(name)
                : humanize(definition.key().value());
        translations.computeIfAbsent(definition.key().namespace(), ignored -> new TreeMap<>())
                .put(definition.translationKey(), value);
    }

    private static void writeTranslations(@NotNull ResourcePack pack,
                                          @NotNull Map<String, Map<String, String>> translations) {
        translations.forEach((namespace, entries) -> {
            JsonObject json = new JsonObject();
            entries.forEach(json::addProperty);

            String path = "assets/" + namespace + "/lang/" + DEFAULT_LANGUAGE + ".json";
            io.kalo.pack.Writable existing = pack.file(path);
            if (existing != null) {
                // Items and armor compile in separate passes, and blocks may have written
                // this namespace first. Language files are additive: replacing one here
                // makes whichever content type ran last erase every earlier name.
                try {
                    JsonObject merged = com.google.gson.JsonParser.parseString(
                            new String(existing.toByteArray(), StandardCharsets.UTF_8)).getAsJsonObject();
                    json.entrySet().forEach(entry -> merged.add(entry.getKey(), entry.getValue()));
                    pack.file(path, Json.writable(merged));
                    return;
                } catch (Exception e) {
                    LOGGER.log(Level.WARNING, "Could not merge into existing lang file " + path, e);
                }
            }
            pack.file(path, Json.writable(json));
        });
    }

    /** {@code ruby_sword} to {@code Ruby Sword}, so an unnamed item still reads sensibly. */
    private static @NotNull String humanize(@NotNull String value) {
        String[] words = value.split("[_/]");
        StringBuilder result = new StringBuilder(value.length());
        for (String word : words) {
            if (word.isEmpty()) {
                continue;
            }
            if (!result.isEmpty()) {
                result.append(' ');
            }
            result.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        return result.isEmpty() ? value : result.toString();
    }
}
