package io.kalo.platform.java;

import com.google.gson.JsonObject;
import io.kalo.content.item.Item;
import io.kalo.content.item.definition.ItemDefinition;
import io.kalo.content.item.definition.ModelDefinition;
import io.kalo.pack.Json;
import io.kalo.pack.ResourcePack;
import io.kalo.pack.Writable;
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

    /**
     * @return Java content keys that could not be compiled, with the reason already logged
     */
    public static java.util.Set<String> compileItems(@NotNull ResourcePack pack,
                                                     @NotNull Iterable<Item> items) {
        // Sorted so the emitted lang file is stable between runs, which keeps the pack
        // hash stable and stops clients re-downloading an unchanged pack.
        Map<String, Map<String, String>> translations = new TreeMap<>();
        java.util.Set<String> failed = new java.util.TreeSet<>();

        // Placed models and the state models they can switch to are the only items whose
        // bones need a Java rig; held items keep the single static model.
        java.util.List<Item> ordered = new java.util.ArrayList<>();
        items.forEach(ordered::add);
        java.util.Set<Key> rigged = new java.util.HashSet<>();
        for (Item item : ordered) {
            try {
                io.kalo.content.item.definition.PlacementDefinition placement =
                        item.definition().placement();
                if (placement != null) {
                    rigged.add(item.key());
                    for (var state : placement.states()) {
                        if (state.model() != null) {
                            rigged.add(state.model());
                        }
                    }
                }
            } catch (RuntimeException unreadable) {
                // The per-item compile below logs this item and keeps the rest.
            }
        }

        for (Item item : ordered) {
            // Everything including definition() is inside the try: one broken item must
            // not cost every other item in the pack its assets.
            try {
                ItemDefinition definition = item.definition();
                compileItem(pack, definition, rigged.contains(definition.key()));
                collectTranslation(translations, definition);
            } catch (Exception e) {
                failed.add(itemKey(item));
                LOGGER.log(Level.WARNING, "Failed to compile pack assets for item "
                        + itemKey(item), e);
            }
        }

        if (!translations.isEmpty()) {
            writeTranslations(pack, translations);
        }
        return java.util.Collections.unmodifiableSet(failed);
    }

    private static @NotNull String itemKey(@NotNull Item item) {
        try {
            return item.key().asString();
        } catch (RuntimeException unreadable) {
            return "<unreadable-item>";
        }
    }

    private static void compileItem(@NotNull ResourcePack pack,
                                    @NotNull ItemDefinition definition,
                                    boolean rigged)
            throws java.io.IOException, io.kalo.model.ModelImportException {
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
            case ModelDefinition.Blockbench blockbench ->
                    compileBlockbench(pack, definition, blockbench, rigged);
        }
    }

    /**
     * Imports a Blockbench source through the shared IR and emits the Java model. The
     * source {@code .bbmodel} itself is not shipped as a Java model; only the compiled
     * JSON is.
     */
    private static void compileBlockbench(@NotNull ResourcePack pack,
                                          @NotNull ItemDefinition definition,
                                          @NotNull ModelDefinition.Blockbench blockbench,
                                          boolean rigged)
            throws java.io.IOException, io.kalo.model.ModelImportException {
        Key key = definition.key();
        String stem = key.namespace() + "_" + key.value();
        io.kalo.model.ModelIR imported = io.kalo.model.ModelRegistry.load(pack,
                blockbench.source());
        io.kalo.model.java.JavaModelCompiler.Result compiled =
                io.kalo.model.java.JavaModelCompiler.compile(imported, blockbench.textures(),
                        key.namespace(), stem);
        io.kalo.model.ModelDiagnostics.log(LOGGER,
                "Blockbench model for " + key.asString(), compiled.diagnostics());
        if (!compiled.compiled()) {
            throw new IllegalStateException(compiled.errorSummary());
        }

        for (io.kalo.model.ModelTextureResolver.ResolvedTexture texture : compiled.textures()) {
            if (texture.embeddedPng() != null) {
                pack.file(texturePath(texture.key()),
                        Writable.bytes(texture.embeddedPng()));
            } else if (pack.file(texturePath(texture.key())) == null
                    && pack.file(texturePath(texture.key()) + ".mcmeta") == null) {
                throw new IllegalStateException("Blockbench texture " + texture.key().asString()
                        + " is missing from the pack's assets");
            }
        }

        Key modelKey = Key.key(key.namespace(), "item/" + key.value());
        pack.file(modelPath(modelKey), Json.writable(compiled.model()));
        pack.file(itemDefinitionPath(key), Json.writable(itemDefinition(modelKey)));

        if (rigged) {
            BlockbenchRigRegistry.Rig rig = BlockbenchBoneRigCompiler.compile(pack, key,
                    imported, blockbench.textures(), stem, "item", LOGGER,
                    "placed item " + key.asString());
            if (rig != null) {
                BlockbenchRigRegistry.putPlaced(rig);
            }
        }
    }

    /** {@code assets/<ns>/textures/<value>.png} for a resolved texture key. */
    private static @NotNull String texturePath(@NotNull Key key) {
        return "assets/" + key.namespace() + "/textures/" + key.value() + ".png";
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
