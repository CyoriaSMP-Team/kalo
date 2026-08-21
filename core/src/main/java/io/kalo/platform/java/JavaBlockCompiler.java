package io.kalo.platform.java;

import com.google.gson.JsonObject;
import io.kalo.content.block.Block;
import io.kalo.content.block.definition.BlockDefinition;
import io.kalo.content.block.definition.BlockModelDefinition;
import io.kalo.pack.Json;
import io.kalo.pack.ResourcePack;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Turns block definitions into Java resource pack assets.
 *
 * <p>Java cannot add a block without a client mod, so a custom block is a note block in
 * a state the pack tells the client to render differently. That means one shared file,
 * {@code assets/minecraft/blockstates/note_block.json}, has to enumerate <em>every</em>
 * state — the borrowed ones pointing at custom models and the rest at the vanilla model.
 * Omitting a state makes the client render it as missing texture, so this file is
 * generated exhaustively rather than only for the states in use.</p>
 */
public final class JavaBlockCompiler {
    private static final Logger LOGGER = Logger.getLogger(JavaBlockCompiler.class.getName());

    /**
     * The note block instruments whose state can be relied upon, under vanilla's own
     * names as they appear in the block state.
     *
     * <p>Order is load-bearing: {@link BlockStateAllocator} indexes into this list, so
     * reordering or inserting would reassign every already-placed custom block. Append
     * only, and only if vanilla adds an instrument that behaves like these.</p>
     *
     * <p>The mob-head instruments (zombie, skeleton, creeper, …) and the trumpet variants
     * are excluded: vanilla derives them from surrounding blocks rather than from the
     * block state alone, so they cannot be held.</p>
     *
     * <p>Deliberately strings rather than {@code org.bukkit.Instrument}: that enum is
     * registry-backed on modern Paper and throws {@code No RegistryAccess implementation
     * found} outside a running server, which would make this compiler impossible to test.
     * The Bukkit mapping lives in {@link JavaBlockListener}, which genuinely needs a
     * server anyway.</p>
     */
    static final List<String> INSTRUMENT_IDS = List.of(
            "harp", "basedrum", "snare", "hat", "bass", "flute", "bell", "guitar",
            "chime", "xylophone", "iron_xylophone", "cow_bell", "didgeridoo", "bit",
            "banjo", "pling"
    );

    static final String NOTE_BLOCK_STATES_PATH = "assets/minecraft/blockstates/note_block.json";
    private static final String VANILLA_NOTE_BLOCK_MODEL = "minecraft:block/note_block";
    private static final Key CUBE_ALL_PARENT = Key.key("minecraft", "block/cube_all");
    private static final Key CUBE_PARENT = Key.key("minecraft", "block/cube");

    private JavaBlockCompiler() {
    }

    /** @return the blocks that could not be compiled, by key, with the reason */
    public static Map<String, String> compileBlocks(@NotNull ResourcePack pack,
                                                    @NotNull Iterable<Block> blocks,
                                                    @NotNull BlockStateAllocator allocator) {
        // index -> model key of the custom block occupying it
        Map<Integer, Key> occupied = new TreeMap<>();
        Map<String, Map<String, String>> translations = new TreeMap<>();
        Map<String, String> failed = new TreeMap<>();

        for (Block block : blocks) {
            try {
                BlockDefinition definition = block.definition();
                int index = allocator.allocate(definition.key());

                Key modelKey = compileModel(pack, definition);
                occupied.put(index, modelKey);

                // The item players hold and place should look like the block, so it gets
                // its own item definition pointing at the same model.
                pack.file(itemDefinitionPath(definition.key()), Json.writable(itemDefinition(modelKey)));

                String translation = definition.display().name() != null
                        ? PlainTextComponentSerializer.plainText().serialize(definition.display().name())
                        : humanize(definition.key().value());
                translations.computeIfAbsent(definition.key().namespace(), ignored -> new TreeMap<>())
                        .put(definition.translationKey(), translation);
            } catch (Exception e) {
                // Named, with the reason. 104 identical "failed to compile a block" lines
                // tell a server owner nothing about which block or why.
                String key = keyOf(block);
                failed.put(key, e.getMessage() != null ? e.getMessage() : e.toString());
                LOGGER.warning("Could not compile block " + key + ": " + e.getMessage());
            }
        }

        writeNoteBlockStates(pack, occupied);

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
                JsonObject textures = cubeTextures(cube.faces());
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
     * Writes the shared note block state file, preserving what earlier passes put there.
     *
     * <p>Blocks and furniture are separate content types that share one carrier, so they
     * compile in separate passes but write this same file. Replacing it outright meant
     * whichever type compiled second erased the other's states, and every one of those
     * blocks rendered as a plain note block.</p>
     */
    private static void writeNoteBlockStates(@NotNull ResourcePack pack, @NotNull Map<Integer, Key> occupied) {
        JsonObject generated = noteBlockStates(occupied);

        io.kalo.pack.Writable existing = pack.file(NOTE_BLOCK_STATES_PATH);
        if (existing != null) {
            try {
                JsonObject previous = com.google.gson.JsonParser
                        .parseString(new String(existing.toByteArray(), java.nio.charset.StandardCharsets.UTF_8))
                        .getAsJsonObject();
                JsonObject previousVariants = previous.getAsJsonObject("variants");
                JsonObject generatedVariants = generated.getAsJsonObject("variants");

                // Keep any variant an earlier pass already pointed at a custom model;
                // this pass only knows about its own blocks and defaults the rest.
                previousVariants.entrySet().forEach(entry -> {
                    String model = entry.getValue().getAsJsonObject().get("model").getAsString();
                    if (!VANILLA_NOTE_BLOCK_MODEL.equals(model)) {
                        generatedVariants.add(entry.getKey(), entry.getValue());
                    }
                });
            } catch (Exception e) {
                LOGGER.log(Level.WARNING, "Could not merge into the existing note block state file", e);
            }
        }

        pack.file(NOTE_BLOCK_STATES_PATH, Json.writable(generated));
    }

    /**
     * Builds the exhaustive note block state map: every state the carrier provides, with
     * the borrowed ones pointing at custom models.
     */
    static @NotNull JsonObject noteBlockStates(@NotNull Map<Integer, Key> occupied) {
        JsonObject variants = new JsonObject();

        for (int instrument = 0; instrument < INSTRUMENT_IDS.size(); instrument++) {
            for (int note = 0; note < 25; note++) {
                for (int powered = 0; powered < 2; powered++) {
                    int index = BlockStateAllocator.encode(
                            new BlockStateAllocator.NoteBlockState(instrument, note, powered == 1));

                    Key custom = occupied.get(index);
                    JsonObject variant = new JsonObject();
                    variant.addProperty("model", custom != null ? custom.asString() : VANILLA_NOTE_BLOCK_MODEL);

                    variants.add("instrument=" + INSTRUMENT_IDS.get(instrument)
                            + ",note=" + note
                            + ",powered=" + (powered == 1), variant);
                }
            }
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

    /** Expands the author-friendly {@code all/top/bottom} aliases to cube's texture slots. */
    private static @NotNull JsonObject cubeTextures(@NotNull Map<String, Key> faces) {
        Map<String, Key> sorted = new TreeMap<>(faces);
        Key all = sorted.get("all");

        JsonObject textures = new JsonObject();
        for (String face : List.of("down", "up", "north", "south", "west", "east")) {
            Key texture = switch (face) {
                case "down" -> sorted.getOrDefault("down", sorted.get("bottom"));
                case "up" -> sorted.getOrDefault("up", sorted.get("top"));
                default -> sorted.get(face);
            };
            if (texture == null) {
                texture = all;
            }
            // BlockModelDefinition.Cube verifies that every slot has either an explicit
            // texture or an `all` fallback, so null here would mean a broken caller.
            textures.addProperty(face, texture.asString());
        }
        Key particle = sorted.getOrDefault("particle", all != null ? all : texturesKey(sorted));
        textures.addProperty("particle", particle.asString());
        return textures;
    }

    private static @NotNull Key texturesKey(@NotNull Map<String, Key> faces) {
        for (String face : List.of("north", "south", "west", "east", "up", "top", "down", "bottom")) {
            Key texture = faces.get(face);
            if (texture != null) {
                return texture;
            }
        }
        throw new IllegalArgumentException("cube declares no usable texture");
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

    private static void writeTranslations(@NotNull ResourcePack pack,
                                          @NotNull Map<String, Map<String, String>> translations) {
        translations.forEach((namespace, entries) -> {
            JsonObject json = new JsonObject();
            entries.forEach(json::addProperty);
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
