package io.kalo.platform.bedrock;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
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

        JsonObject mappings = new JsonObject();
        mappings.addProperty("format_version", 2);
        mappings.add("items", mappedItems);

        return new Result(pack, Json.writable(mappings), mappedItems.size(), skipped);
    }

    /**
     * @param pack        the Bedrock pack, ready to be written as {@code .mcpack}
     * @param mappings    the Geyser mapping file, which lives beside the pack rather
     *                    than inside it
     * @param mappedCount how many vanilla items carry at least one custom definition
     * @param skippedCount content that needs Bedrock geometry Kalo cannot yet produce
     */
    public record Result(@NotNull ResourcePack pack, @NotNull Writable mappings, int mappedCount, int skippedCount) {
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
