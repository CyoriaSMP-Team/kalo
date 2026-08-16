package io.kalo.platform.bedrock;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.kalo.content.block.Block;
import io.kalo.content.block.definition.BlockDefinition;
import io.kalo.content.block.definition.BlockModelDefinition;
import io.kalo.content.item.Item;
import io.kalo.content.item.definition.ItemDefinition;
import io.kalo.content.item.definition.ModelDefinition;
import io.kalo.pack.Json;
import io.kalo.pack.ResourcePack;
import io.kalo.pack.Writable;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.UUID;

/**
 * Compiles the shared IR into a Bedrock resource pack and a Geyser v2 item mapping.
 *
 * <p>A Bedrock pack is <em>not</em> a Java pack with extra files. It has its own layout —
 * {@code manifest.json} at the root, textures under {@code textures/}, no
 * {@code assets/} tree at all — so this builds its own container and pulls only the
 * source images it needs out of the Java pack. Copying the Java pack wholesale shipped
 * tens of kilobytes of {@code assets/minecraft/...} that Bedrock silently ignores.</p>
 *
 * <p>Accumulate with {@link #add} for each content type, then call {@link #finish} once.
 * The manifest, texture atlas and mapping file are written by {@code finish} rather than
 * by each {@code add}, because writing them per call meant the last content type to
 * compile erased every earlier type's mappings.</p>
 */
public final class BedrockPackCompiler {

    private final ResourcePack javaSource;
    private final ResourcePack pack;

    private final JsonObject textureData = new JsonObject();
    private final JsonObject mappedItems = new JsonObject();

    private final JsonObject blockDefinitions = new JsonObject();
    private final JsonObject terrainTextureData = new JsonObject();
    private final JsonArray mappedBlocks = new JsonArray();
    /** java content key -> Bedrock geometry identifier, for the extension to apply. */
    private final JsonObject geometries = new JsonObject();
    private int attachables;

    private int skipped;

    public BedrockPackCompiler(@NotNull ResourcePack javaSource, @NotNull ResourcePack pack) {
        this.javaSource = javaSource;
        this.pack = pack;
    }

    /** Adds one content type's items to the mapping. Safe to call repeatedly. */
    public void add(@NotNull Iterable<? extends Item> items) {
        for (Item item : items) {
            ItemDefinition definition = item.definition();

            if (!definition.bedrock().enabled()) {
                continue;
            }
            if (definition.model() instanceof ModelDefinition.Vanilla) {
                // Looks like a vanilla item on Java, so Bedrock already renders it
                // correctly with no custom item at all.
                continue;
            }
            if (!(definition.model() instanceof ModelDefinition.Sprite sprite)) {
                // Bedrock needs a geometry for anything that is not a flat sprite, and
                // converting a Java model to Bedrock geometry is not implemented.
                skipped++;
                continue;
            }

            addSpriteItem(definition, sprite);
        }
    }

    private void addSpriteItem(@NotNull ItemDefinition definition, @NotNull ModelDefinition.Sprite sprite) {
        Key key = definition.key();
        String bedrockId = key.namespace() + ":" + key.value();
        String javaItem = "minecraft:" + definition.java().baseMaterial().name().toLowerCase(Locale.ROOT);
        String icon = definition.bedrock().iconOverride() != null
                ? definition.bedrock().iconOverride()
                : key.namespace() + "_" + key.value();

        JsonObject mapping = new JsonObject();
        mapping.addProperty("type", "definition");
        mapping.addProperty("model", key.asString());
        mapping.addProperty("bedrock_identifier", bedrockId);
        mapping.addProperty("icon", icon);

        String displayName = plainName(definition);
        if (displayName != null) {
            // Geyser shows this verbatim, so it has to be the rendered name — passing the
            // key would literally display "testpack:ruby_sword" in the Bedrock inventory.
            mapping.addProperty("display_name", displayName);
        }

        JsonObject components = new JsonObject();
        components.addProperty("minecraft:max_stack_size", definition.behaviour().maxStackSize());
        if (definition.behaviour().maxDurability() != null) {
            components.addProperty("minecraft:durability", definition.behaviour().maxDurability());
        }
        mapping.add("components", components);

        JsonArray definitions = mappedItems.has(javaItem)
                ? mappedItems.getAsJsonArray(javaItem)
                : new JsonArray();
        definitions.add(mapping);
        mappedItems.add(javaItem, definitions);

        // Bedrock resolves item icons through a flat atlas keyed by shorthand, not by
        // path, so the texture is copied to a flat location and registered here.
        JsonArray paths = new JsonArray();
        paths.add("textures/items/" + icon);
        JsonObject texture = new JsonObject();
        texture.add("textures", paths);
        textureData.add(icon, texture);

        copyTexture(sprite.texture(), "textures/items/" + icon + ".png");
    }

    /**
     * Adds armor: the attachable that puts it on the Bedrock player model.
     *
     * <p>Separate from {@link #add} because armor needs more than an icon. Java paints an
     * equipment texture onto the player; Bedrock attaches a model and hides the vanilla
     * layer beneath it. Same result, different mechanism — the icon still goes through
     * {@code add} like any other item.</p>
     */
    public void addArmor(@NotNull Iterable<? extends io.kalo.content.armor.Armor> armors) {
        for (io.kalo.content.armor.Armor armor : armors) {
            io.kalo.content.armor.ArmorDefinition definition = armor.armorDefinition();

            if (!definition.item().bedrock().enabled()) {
                continue;
            }
            io.kalo.content.armor.ArmorDefinition.EquipmentTexture equipment = definition.equipment();
            if (equipment == null) {
                // Opted out of a custom worn appearance, so the base material's own armor
                // is correct on Bedrock too.
                continue;
            }

            Key key = definition.key();
            Key source = definition.slot().usesLeggingsLayer() && equipment.leggings() != null
                    ? equipment.leggings()
                    : equipment.humanoid();
            String textureName = source.namespace() + "_" + source.value();

            pack.file(BedrockAttachable.attachablePath(key),
                    Json.writable(BedrockAttachable.attachable(definition, textureName)));

            // Java keeps armor sheets under textures/entity/equipment/<layer>/; Bedrock
            // wants one flat textures/models/armor/ with a numbered suffix.
            int layer = BedrockAttachable.textureLayer(definition.slot());
            copyEquipmentTexture(source, definition.slot(),
                    BedrockAttachable.texturePath(textureName, layer) + ".png");

            attachables++;
        }
    }

    /** Armor sheets live under a different root than item textures on the Java side. */
    private void copyEquipmentTexture(@NotNull Key texture,
                                      @NotNull io.kalo.content.armor.ArmorSlot slot,
                                      @NotNull String destination) {
        String layerDir = slot.usesLeggingsLayer() ? "humanoid_leggings" : "humanoid";
        String source = "assets/" + texture.namespace() + "/textures/entity/equipment/"
                + layerDir + "/" + texture.value() + ".png";

        Writable content = javaSource.file(source);
        if (content != null) {
            pack.file(destination, content);
        }
    }

    /**
     * Adds custom blocks: their Bedrock appearance, and the record a Geyser extension
     * needs to register them.
     *
     * <p>Bedrock has real custom blocks rather than borrowed states, so nothing here
     * mirrors the note block carrier the Java side uses. What does have to cross over is
     * the pairing — which Java block key corresponds to which Bedrock identifier — so
     * that is written out for the extension to consume at runtime. The resource pack can
     * only supply the look; registration itself happens inside Geyser.</p>
     *
     * @param allocator supplies the Java carrier state each block occupies, so the
     *                  extension can translate a placed block without re-deriving it
     */
    public void addBlocks(@NotNull Iterable<? extends Block> blocks,
                          @NotNull java.util.function.Function<Key, Integer> allocator,
                          @NotNull java.util.Set<String> skip) {
        for (Block block : blocks) {
            BlockDefinition definition = block.definition();
            if (!definition.bedrock().enabled()) {
                continue;
            }
            if (skip.contains(definition.key().asString())) {
                // Java could not place this block, so Bedrock must not either. Registering
                // on one platform and not the other means a Bedrock player sees something
                // the Java player beside them does not.
                skipped++;
                continue;
            }

            Key key = definition.key();
            String bedrockId = key.namespace() + ":" + key.value();
            String shorthand = key.namespace() + "_" + key.value();

            JsonElement textures = blockTextures(definition, shorthand);
            if (textures == null) {
                // Custom geometry, which Bedrock needs authored separately.
                skipped++;
                continue;
            }

            JsonObject blockDefinition = new JsonObject();
            blockDefinition.add("textures", textures);
            blockDefinition.addProperty("sound", "stone");
            blockDefinitions.add(bedrockId, blockDefinition);

            JsonObject record = new JsonObject();
            record.addProperty("java_key", key.asString());
            record.addProperty("bedrock_identifier", bedrockId);
            Integer state = allocator.apply(key);
            if (state != null) {
                record.addProperty("java_carrier_state", state);
            }
            mappedBlocks.add(record);
        }
    }

    /**
     * Registers each face texture in the terrain atlas and returns what {@code blocks.json}
     * should point at — a single shorthand for a uniform cube, or a per-face object.
     */
    private @Nullable JsonElement blockTextures(@NotNull BlockDefinition definition, @NotNull String shorthand) {
        switch (definition.model()) {
            case BlockModelDefinition.CubeAll cubeAll -> {
                registerTerrainTexture(shorthand, cubeAll.texture());
                return new com.google.gson.JsonPrimitive(shorthand);
            }
            case BlockModelDefinition.Cube cube -> {
                JsonObject faces = new JsonObject();
                cube.faces().forEach((face, texture) -> {
                    String faceShorthand = shorthand + "_" + face;
                    registerTerrainTexture(faceShorthand, texture);
                    faces.addProperty(bedrockFace(face), faceShorthand);
                });
                return faces;
            }
            case BlockModelDefinition.Custom custom -> {
                // A hand-authored Java model: convert its shape to Bedrock geometry and
                // register whatever textures it declares.
                JsonObject geometry = convertGeometry(definition, custom);
                if (geometry == null) {
                    return null;
                }
                custom.textures().forEach((slot, texture) ->
                        registerTerrainTexture(shorthand + "_" + slot, texture));
                return new com.google.gson.JsonPrimitive(shorthand);
            }
        }
    }

    /**
     * Converts the block's Java model into Bedrock geometry and writes it into the pack.
     *
     * <p>Reads the model out of the Java pack rather than off disk, because that pack has
     * already gathered every content pack's assets in one place.</p>
     *
     * @return the geometry, or {@code null} if the model has no shape of its own — a
     *         model that only sets a parent inherits one, which cannot be resolved here
     */
    private @Nullable JsonObject convertGeometry(@NotNull BlockDefinition definition,
                                                 @NotNull BlockModelDefinition.Custom custom) {
        Key model = custom.model();
        String source = "assets/" + model.namespace() + "/models/" + model.value() + ".json";
        Writable content = javaSource.file(source);
        if (content == null) {
            return null;
        }

        try {
            JsonObject javaModel = com.google.gson.JsonParser
                    .parseString(new String(content.toByteArray(), StandardCharsets.UTF_8))
                    .getAsJsonObject();

            Key key = definition.key();
            String identifier = BedrockGeometry.identifierFor(key.namespace(), key.value());
            JsonObject geometry = BedrockGeometry.convert(identifier, javaModel);
            if (geometry == null) {
                return null;
            }

            pack.file("models/blocks/" + key.namespace() + "_" + key.value() + ".geo.json",
                    Json.writable(geometry));
            geometries.addProperty(key.asString(), identifier);
            return geometry;
        } catch (Exception e) {
            return null;
        }
    }

    private void registerTerrainTexture(@NotNull String shorthand, @NotNull Key texture) {
        JsonObject entry = new JsonObject();
        entry.addProperty("textures", "textures/blocks/" + shorthand);
        terrainTextureData.add(shorthand, entry);

        copyTexture(texture, "textures/blocks/" + shorthand + ".png");
    }

    /** Java names cube faces by direction; Bedrock uses up/down/side plus directions. */
    private static @NotNull String bedrockFace(@NotNull String javaFace) {
        return switch (javaFace) {
            case "top" -> "up";
            case "bottom" -> "down";
            case "all" -> "side";
            default -> javaFace;
        };
    }

    /** Pulls a texture out of the Java pack, which already gathered every pack's assets. */
    private void copyTexture(@NotNull Key texture, @NotNull String destination) {
        String source = "assets/" + texture.namespace() + "/textures/" + texture.value() + ".png";
        Writable content = javaSource.file(source);
        if (content != null) {
            pack.file(destination, content);
        }
    }

    /** Writes the files that describe the pack as a whole. Call once, after all adds. */
    public @NotNull Result finish() {
        JsonObject itemTexture = new JsonObject();
        itemTexture.addProperty("resource_pack_name", "kalo");
        itemTexture.addProperty("texture_name", "atlas.items");
        itemTexture.add("texture_data", textureData);

        pack.file("textures/item_texture.json", Json.writable(itemTexture));
        pack.file("manifest.json", Json.writable(manifest()));

        if (!blockDefinitions.isEmpty()) {
            JsonObject blocks = new JsonObject();
            blocks.addProperty("format_version", "1.21.0");
            blockDefinitions.entrySet().forEach(entry -> blocks.add(entry.getKey(), entry.getValue()));
            pack.file("blocks.json", Json.writable(blocks));

            JsonObject terrain = new JsonObject();
            terrain.addProperty("resource_pack_name", "kalo");
            terrain.addProperty("texture_name", "atlas.terrain");
            terrain.addProperty("padding", 8);
            terrain.addProperty("num_mip_levels", 4);
            terrain.add("texture_data", terrainTextureData);
            pack.file("textures/terrain_texture.json", Json.writable(terrain));
        }

        JsonObject mappings = new JsonObject();
        mappings.addProperty("format_version", 2);
        mappings.add("items", mappedItems);
        // Blocks are registered by a Geyser extension at runtime rather than through the
        // item mapping file, so they are recorded separately for it to read.
        mappings.add("kalo:blocks", mappedBlocks);
        if (!geometries.isEmpty()) {
            mappings.add("kalo:geometries", geometries);
        }

        int itemDefinitions = mappedItems.entrySet().stream()
                .mapToInt(entry -> entry.getValue().getAsJsonArray().size())
                .sum();
        return new Result(pack, Json.writable(mappings), mappedItems.size(), itemDefinitions,
                mappedBlocks.size(), attachables, skipped);
    }

    /**
     * @param pack        the Bedrock pack, ready to be written as {@code .mcpack}
     * @param mappings    the Geyser mapping file, which lives beside the pack rather
     *                    than inside it
     * @param mappedCount  how many vanilla items carry at least one custom definition
     * @param itemCount    how many custom items exist across them — the number a reader
     *                     expects, since a thousand custom items may all sit on PAPER
     * @param blockCount   how many custom blocks got a Bedrock appearance
     * @param armorCount   how many armor pieces render on the Bedrock player model
     * @param skippedCount content Bedrock did not get, whether because it needs geometry
     *                     Kalo cannot produce or because Java could not place it either
     */
    public record Result(@NotNull ResourcePack pack, @NotNull Writable mappings,
                         int mappedCount, int itemCount, int blockCount, int armorCount,
                         int skippedCount) {
    }

    private static @Nullable String plainName(@NotNull ItemDefinition definition) {
        if (definition.display().name() == null) {
            return null;
        }
        return PlainTextComponentSerializer.plainText().serialize(definition.display().name());
    }

    private static @NotNull JsonObject manifest() {
        JsonObject header = new JsonObject();
        header.addProperty("name", "Kalo Bedrock Content");
        header.addProperty("description", "Generated by Kalo");
        header.addProperty("uuid", stableUuid("kalo-bedrock-header"));
        header.add("version", version());
        // Oldest Bedrock version this pack's format is valid for.
        header.add("min_engine_version", engineVersion());

        JsonObject module = new JsonObject();
        module.addProperty("type", "resources");
        module.addProperty("description", "Kalo generated resources");
        module.addProperty("uuid", stableUuid("kalo-bedrock-module"));
        module.add("version", version());

        JsonArray modules = new JsonArray();
        modules.add(module);

        JsonObject manifest = new JsonObject();
        manifest.addProperty("format_version", 2);
        manifest.add("header", header);
        manifest.add("modules", modules);
        return manifest;
    }

    /** Bedrock manifests take versions as [major, minor, patch], not as a string. */
    private static @NotNull JsonArray version() {
        JsonArray version = new JsonArray();
        version.add(1);
        version.add(0);
        version.add(0);
        return version;
    }

    private static @NotNull JsonArray engineVersion() {
        JsonArray version = new JsonArray();
        version.add(1);
        version.add(21);
        version.add(0);
        return version;
    }

    /**
     * Derived from a fixed name so the pack keeps the same identity across regenerations
     * — a changing uuid makes Bedrock treat it as a different pack every time.
     */
    private static @NotNull String stableUuid(@NotNull String name) {
        return UUID.nameUUIDFromBytes(name.getBytes(StandardCharsets.UTF_8)).toString();
    }
}
