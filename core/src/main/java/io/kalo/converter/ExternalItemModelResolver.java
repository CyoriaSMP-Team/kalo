package io.kalo.converter;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.kalo.content.item.definition.DisplayProperties;
import io.kalo.content.item.definition.ItemBehaviour;
import io.kalo.content.item.definition.ItemDefinition;
import io.kalo.content.item.definition.JavaOptions;
import io.kalo.content.item.definition.ModelDefinition;
import io.kalo.pack.ResourcePack;
import io.kalo.pack.Writable;
import net.kyori.adventure.key.Key;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.HashSet;
import java.util.Set;

/**
 * Resolve a real item_model component to the authored Java pack's plain model.
 * Only a plain model is supported here. Conditions, range dispatch and legacy
 * CustomModelData need a different predicate mapping and must not silently flatten.
 */
public final class ExternalItemModelResolver {
    private static final int MAX_JSON_BYTES = 2 * 1024 * 1024;

    private ExternalItemModelResolver() {}

    public static @Nullable ItemDefinition resolve(@NotNull ResourcePack source,
                                                   @NotNull ExternalItemBindings.Binding binding) {
        try {
            Key itemModel = binding.itemModel();
            JsonObject itemRoot = json(source, "assets/" + itemModel.namespace()
                    + "/items/" + itemModel.value() + ".json");
            if (itemRoot == null) return null;
            JsonObject selector = itemRoot.getAsJsonObject("model");
            if (selector == null) return null;
            String kind = string(selector, "type");
            if (!"minecraft:model".equals(kind) && !"model".equals(kind)) return null;
            String sourceId = string(selector, "model");
            if (sourceId == null) return null;
            Key modelKey = Key.key(sourceId);
            JsonObject model = json(source, "assets/" + modelKey.namespace()
                    + "/models/" + modelKey.value() + ".json");
            if (model == null) return null;

            ModelDefinition converted;
            String parent = string(model, "parent");
            boolean flatParent = "minecraft:item/generated".equals(parent)
                    || "minecraft:item/handheld".equals(parent)
                    || "item/generated".equals(parent)
                    || "item/handheld".equals(parent);
            if (hasElements(model) || (!flatParent && parent != null
                    && hasResolvableGeometry(source, modelKey))) {
                // The existing Bedrock 3D compiler resolves authored model parents
                // and checks for cycles; keep that logic in one place.
                converted = new ModelDefinition.Custom(modelKey, Map.of());
            } else {
                if (!flatParent) return null;
                JsonObject textures = model.getAsJsonObject("textures");
                String layer = textures == null ? null : string(textures, "layer0");
                if (layer == null || layer.startsWith("#")) return null;
                Key texture = Key.key(layer);
                if (source.file("assets/" + texture.namespace()
                        + "/textures/" + texture.value() + ".png") == null) return null;
                converted = new ModelDefinition.Sprite(texture);
            }

            return ItemDefinition.builder(itemModel)
                    .model(converted)
                    .display(DisplayProperties.empty())
                    .behaviour(new ItemBehaviour(binding.maxStackSize(), null, false))
                    .java(new JavaOptions(binding.material()))
                    .build();
        } catch (RuntimeException unsupportedOrMalformed) {
            return null;
        }
    }

    /** Resolve a leaf Java item model using the existing conservative sprite/3D rules. */
    static @Nullable ItemDefinition resolveModel(ResourcePack source,
            ExternalItemBindings.Binding binding, Key outputKey, Key modelKey) {
        try {
            JsonObject model = json(source, "assets/" + modelKey.namespace()
                    + "/models/" + modelKey.value() + ".json");
            if (model == null) return null;
            ModelDefinition converted;
            String parent = string(model, "parent");
            boolean flat = "minecraft:item/generated".equals(parent)
                    || "minecraft:item/handheld".equals(parent)
                    || "item/generated".equals(parent) || "item/handheld".equals(parent);
            if (hasElements(model) || (!flat && parent != null
                    && hasResolvableGeometry(source, modelKey))) {
                converted = new ModelDefinition.Custom(modelKey, Map.of());
            } else {
                if (!flat) return null;
                JsonObject textures = model.getAsJsonObject("textures");
                String layer = textures == null ? null : string(textures, "layer0");
                if (layer == null || layer.startsWith("#")) return null;
                Key texture = Key.key(layer);
                if (source.file("assets/" + texture.namespace() + "/textures/"
                        + texture.value() + ".png") == null) return null;
                converted = new ModelDefinition.Sprite(texture);
            }
            return ItemDefinition.builder(outputKey)
                    .model(converted).display(DisplayProperties.empty())
                    .behaviour(new ItemBehaviour(binding.maxStackSize(), null, false))
                    .java(new JavaOptions(binding.material())).build();
        } catch (RuntimeException bad) {
            return null;
        }
    }

    /**
     * Do not register a Java vanilla fallback as a custom Bedrock 3D item.
     * Packs sometimes include a model such as minecraft:item/fishing_rod with
     * only a built-in parent (item/handheld_rod) and no local geometry. The
     * selector may resolve, but the Bedrock compiler has no cubes to emit.
     *
     * Authored inherited 3D models remain eligible when an actual parent in
     * the pack provides their geometry. A cycle or missing parent fails closed.
     */
    private static boolean hasResolvableGeometry(ResourcePack source, Key modelKey) {
        Set<Key> visited = new HashSet<>();
        Key current = modelKey;
        for (int depth = 0; depth < 16 && visited.add(current); depth++) {
            JsonObject model = json(source, "assets/" + current.namespace()
                    + "/models/" + current.value() + ".json");
            if (model == null) return false;
            if (model.has("elements")) return hasElements(model);
            String parent = string(model, "parent");
            if (parent == null || parent.startsWith("builtin/")
                    || parent.startsWith("minecraft:builtin/")) return false;
            try {
                current = Key.key(parent.contains(":") ? parent
                        : current.namespace() + ":" + parent);
            } catch (IllegalArgumentException invalid) {
                return false;
            }
        }
        return false;
    }

    private static boolean hasElements(JsonObject model) {
        JsonElement elements = model.get("elements");
        return elements != null && elements.isJsonArray() && !elements.getAsJsonArray().isEmpty();
    }

    private static @Nullable JsonObject json(ResourcePack pack, String path) {
        Writable writable = pack.file(path);
        if (writable == null) return null;
        try {
            byte[] bytes = writable.toByteArray();
            if (bytes.length > MAX_JSON_BYTES) return null;
            JsonElement element = JsonParser.parseString(new String(bytes, StandardCharsets.UTF_8));
            return element.isJsonObject() ? element.getAsJsonObject() : null;
        } catch (Exception invalid) {
            return null;
        }
    }

    private static @Nullable String string(JsonObject object, String field) {
        JsonElement value = object.get(field);
        return value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isString()
                ? value.getAsString() : null;
    }
}
