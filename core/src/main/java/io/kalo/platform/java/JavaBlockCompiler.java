package io.kalo.platform.java;

import com.google.gson.JsonObject;
import io.kalo.content.block.Block;
import io.kalo.content.block.definition.BlockDefinition;
import io.kalo.content.block.definition.BlockCarrier;
import io.kalo.content.block.definition.BlockModelDefinition;
import io.kalo.content.block.definition.JavaBlockMode;
import io.kalo.pack.Json;
import io.kalo.pack.ResourcePack;
import net.kyori.adventure.key.Key;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Turns block definitions into Java resource pack assets.
 *
 * <p>Native blocks borrow spare vanilla states and therefore get an exhaustive generated
 * blockstates file. Virtual blocks do not consume a state at all: their item definition
 * still points at the block model, while the runtime renders it through an
 * {@code ItemDisplay}.</p>
 */
public final class JavaBlockCompiler {
    private static final Logger LOGGER = Logger.getLogger(JavaBlockCompiler.class.getName());
    /** Compatibility alias for the original note-block-only compiler API. */
    public static final String NOTE_BLOCK_STATES_PATH = BlockCarrier.NOTE_BLOCK.blockStatesPath();

    private static final Key CUBE_ALL_PARENT = Key.key("minecraft", "block/cube_all");
    private static final Key CUBE_PARENT = Key.key("minecraft", "block/cube");

    private JavaBlockCompiler() {
    }

    /** @return the blocks that could not be compiled, by key, with the reason */
    public static Map<String, String> compileBlocks(@NotNull ResourcePack pack,
                                                    @NotNull Iterable<Block> blocks,
                                                    @NotNull BlockStateAllocator allocator) {
        // carrier -> state index -> model key of the custom block occupying it
        Map<io.kalo.content.block.definition.BlockCarrier, Map<Integer, Key>> occupied =
                new java.util.EnumMap<>(io.kalo.content.block.definition.BlockCarrier.class);
        Map<String, String> translations = new TreeMap<>();
        Map<String, String> failed = new TreeMap<>();

        for (Block block : blocks) {
            try {
                BlockDefinition definition = block.definition();
                Key modelKey = compileModel(pack, definition);
                BlockStateAllocator.Assignment assignment = null;
                if (definition.java().mode() == JavaBlockMode.NATIVE) {
                    assignment = allocator.allocate(definition.key(), definition.java().carrier());
                } else {
                    // A server may switch an existing content key from native to virtual.
                    // Keep its old state in the pack as a read-only compatibility path so
                    // blocks already in the world do not turn into plain vanilla blocks.
                    assignment = allocator.assignmentOf(definition.key());
                }
                if (assignment != null) {
                    occupied.computeIfAbsent(assignment.carrier(), ignored -> new TreeMap<>())
                            .put(assignment.state(), modelKey);
                }

                // The item players hold and place should look like the block, so it gets
                // its own item definition pointing at the same model. Virtual placement
                // uses this same item as the ItemDisplay payload.
                pack.file(itemDefinitionPath(definition.key()), Json.writable(itemDefinition(modelKey)));

                translations.put(definition.translationKey(), humanize(definition.key().value()));
            } catch (Exception e) {
                // Named, with the reason. 104 identical "failed to compile a block" lines
                // tell a server owner nothing about which block or why.
                String key = keyOf(block);
                failed.put(key, e.getMessage() != null ? e.getMessage() : e.toString());
                LOGGER.warning("Could not compile block " + key + ": " + e.getMessage());
            }
        }

        occupied.forEach((carrier, states) -> writeBlockStates(pack, carrier, states));

        if (!translations.isEmpty()) {
            writeTranslations(pack, translations);
        }

        if (!failed.isEmpty()) {
            LOGGER.warning(failed.size() + " block(s) did not make it into the pack and will not "
                    + "render for any player");
        }
        return failed;
    }

    private static @NotNull String keyOf(@NotNull Block block) {
        try {
            return block.definition().key().asString();
        } catch (Exception e) {
            return "<unreadable>";
        }
    }

    /** Emits the block's model and returns the key the block state should point at. */
    private static @NotNull Key compileModel(@NotNull ResourcePack pack, @NotNull BlockDefinition definition) {
        Key key = definition.key();
        Key modelKey = Key.key(key.namespace(), "block/" + key.value());

        switch (definition.model()) {
            case BlockModelDefinition.CubeAll cubeAll -> {
                JsonObject textures = new JsonObject();
                textures.addProperty("all", cubeAll.texture().asString());
                pack.file(modelPath(modelKey), Json.writable(model(CUBE_ALL_PARENT, textures)));
            }
            case BlockModelDefinition.Cube cube -> {
                JsonObject textures = new JsonObject();
                new TreeMap<>(cube.faces()).forEach((face, texture) ->
                        textures.addProperty(face, texture.asString()));
                pack.file(modelPath(modelKey), Json.writable(model(CUBE_PARENT, textures)));
            }
            case BlockModelDefinition.Custom custom -> {
                // The model file ships with the pack and is copied verbatim; only the
                // block state pointing at it is generated.
                return custom.model();
            }
        }
        return modelKey;
    }

    /**
     * Writes one carrier's blockstates file, preserving what earlier passes put there.
     *
     * <p>Every state the carrier provides is enumerated, not only the ones in use: a state
     * the client is not told about renders as missing texture, and these are blocks
     * players can already place themselves.</p>
     *
     * <p>Merged rather than replaced because blocks and furniture are separate content
     * types compiling in separate passes over the same file. Replacing it meant whichever
     * ran second erased the other's blocks.</p>
     */
    private static void writeBlockStates(@NotNull ResourcePack pack,
                                         @NotNull BlockCarrier carrier,
                                         @NotNull Map<Integer, Key> occupied) {
        JsonObject generated = blockStates(carrier, occupied);

        io.kalo.pack.Writable existing = pack.file(carrier.blockStatesPath());
        if (existing != null) {
            try {
                JsonObject previous = com.google.gson.JsonParser
                        .parseString(new String(existing.toByteArray(), java.nio.charset.StandardCharsets.UTF_8))
                        .getAsJsonObject();
                JsonObject previousVariants = previous.getAsJsonObject("variants");
                JsonObject generatedVariants = generated.getAsJsonObject("variants");

                previousVariants.entrySet().forEach(entry -> {
                    String model = entry.getValue().getAsJsonObject().get("model").getAsString();
                    if (!carrier.vanillaModel().equals(model)) {
                        generatedVariants.add(entry.getKey(), entry.getValue());
                    }
                });
            } catch (Exception e) {
                LOGGER.log(Level.WARNING,
                        "Could not merge into the existing " + carrier.blockStatesPath(), e);
            }
        }

        pack.file(carrier.blockStatesPath(), Json.writable(generated));
    }

    /** Every state of a carrier, the borrowed ones pointing at custom models. */
    static @NotNull JsonObject blockStates(@NotNull BlockCarrier carrier,
                                           @NotNull Map<Integer, Key> occupied) {
        JsonObject variants = new JsonObject();

        for (int index = 0; index < carrier.stateCount(); index++) {
            Key custom = occupied.get(index);
            JsonObject variant = new JsonObject();
            variant.addProperty("model", custom != null ? custom.asString() : carrier.vanillaModel());
            variants.add(carrier.variantKey(index), variant);
        }

        JsonObject root = new JsonObject();
        root.add("variants", variants);
        return root;
    }

    private static @NotNull JsonObject model(@NotNull Key parent, @NotNull JsonObject textures) {
        JsonObject root = new JsonObject();
        root.addProperty("parent", parent.asString());
        root.add("textures", textures);
        return root;
    }

    private static @NotNull String modelPath(@NotNull Key key) {
        return "assets/" + key.namespace() + "/models/" + key.value() + ".json";
    }

    private static @NotNull String itemDefinitionPath(@NotNull Key key) {
        return "assets/" + key.namespace() + "/items/" + key.value() + ".json";
    }

    private static @NotNull JsonObject itemDefinition(@NotNull Key model) {
        JsonObject modelSection = new JsonObject();
        modelSection.addProperty("type", "minecraft:model");
        modelSection.addProperty("model", model.asString());

        JsonObject root = new JsonObject();
        root.add("model", modelSection);
        return root;
    }

    private static void writeTranslations(@NotNull ResourcePack pack, @NotNull Map<String, String> translations) {
        Map<String, JsonObject> byNamespace = new TreeMap<>();
        for (Map.Entry<String, String> entry : translations.entrySet()) {
            // translation keys are "block.<namespace>.<name>"
            String[] parts = entry.getKey().split("\\.", 3);
            String namespace = parts.length >= 2 ? parts[1] : "minecraft";
            byNamespace.computeIfAbsent(namespace, ignored -> new JsonObject())
                    .addProperty(entry.getKey(), entry.getValue());
        }

        byNamespace.forEach((namespace, json) -> {
            String path = "assets/" + namespace + "/lang/en_us.json";
            // Items may already have written this pack's lang file; merge rather than
            // clobber, since the last content type to run would otherwise win.
            io.kalo.pack.Writable existing = pack.file(path);
            if (existing != null) {
                try {
                    JsonObject merged = com.google.gson.JsonParser
                            .parseString(new String(existing.toByteArray(), java.nio.charset.StandardCharsets.UTF_8))
                            .getAsJsonObject();
                    json.entrySet().forEach(e -> merged.add(e.getKey(), e.getValue()));
                    pack.file(path, Json.writable(merged));
                    return;
                } catch (Exception e) {
                    LOGGER.log(Level.WARNING, "Could not merge into existing lang file " + path, e);
                }
            }
            pack.file(path, Json.writable(json));
        });
    }

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
