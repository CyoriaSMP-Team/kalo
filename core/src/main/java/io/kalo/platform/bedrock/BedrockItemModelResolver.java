package io.kalo.platform.bedrock;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.kalo.pack.ResourcePack;
import io.kalo.pack.Writable;
import net.kyori.adventure.key.Key;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Resolve authored Java parent-model inheritance to a flattened, immutable-input
 * model before Bedrock geometry and atlas generation. Child textures and UV scale
 * declarations override parents; the closest model defining elements wins.
 *
 * Bedrock does not inherit Java model files, so an unresolved parent with no
 * local geometry cannot be converted. Parent cycles and excessive depth fail safe.
 */
final class BedrockItemModelResolver {
    private BedrockItemModelResolver() {}

    static @Nullable JsonObject resolve(@NotNull ResourcePack javaSource,
                                        @NotNull Key modelKey) throws IOException {
        List<JsonObject> chain = new ArrayList<>();
        Set<Key> visited = new HashSet<>();
        Key current = modelKey;
        for (int depth = 0; depth < 16; depth++) {
            if (!visited.add(current)) return null;
            Writable model = javaSource.file("assets/" + current.namespace()
                    + "/models/" + current.value() + ".json");
            if (model == null) break;
            JsonObject json = JsonParser.parseString(
                    new String(model.toByteArray(), StandardCharsets.UTF_8)).getAsJsonObject();
            chain.add(json);
            JsonElement parent = json.get("parent");
            if (parent == null || !parent.isJsonPrimitive()) break;
            String value = parent.getAsString();
            if (value.startsWith("builtin/") || value.startsWith("minecraft:builtin/")) break;
            try {
                current = Key.key(value.contains(":") ? value
                        : current.namespace() + ":" + value);
            } catch (IllegalArgumentException invalid) {
                return null;
            }
            if (depth == 15) return null;
        }
        if (chain.isEmpty()) return null;

        JsonObject textures = new JsonObject();
        JsonObject flattened = new JsonObject();
        for (int i = chain.size() - 1; i >= 0; i--) {
            JsonObject level = chain.get(i);
            if (level.has("textures")) {
                JsonObject entries = level.getAsJsonObject("textures");
                if (entries == null) return null;
                for (var entry : entries.entrySet()) {
                    textures.add(entry.getKey(), entry.getValue().deepCopy());
                }
            }
            if (level.has("texture_size")) {
                flattened.add("texture_size", level.get("texture_size").deepCopy());
            }
            if (level.has("elements")) {
                flattened.add("elements", level.get("elements").deepCopy());
            }
        }
        if (!flattened.has("elements")) return null;
        flattened.add("textures", textures);
        return flattened;
    }
}
