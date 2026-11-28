package io.kalo.platform.bedrock;

import net.kyori.adventure.key.Key;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Bedrock client-entity identifier for each VFX effect/layer and model layer. */
public final class BedrockVfxRegistry {

    private static final Map<Key, Map<Integer, String>> ENTITIES = new ConcurrentHashMap<>();
    private static final Map<Key, Map<Integer, String>> MODELS = new ConcurrentHashMap<>();

    private BedrockVfxRegistry() {
    }

    public static void clear() {
        ENTITIES.clear();
        MODELS.clear();
    }

    public static void put(@NotNull Key content, int layer, @NotNull String entityIdentifier) {
        ENTITIES.computeIfAbsent(content, ignored -> new ConcurrentHashMap<>())
                .put(layer, entityIdentifier);
    }

    public static @Nullable String entityFor(@NotNull Key content, int layer) {
        Map<Integer, String> layers = ENTITIES.get(content);
        return layers == null ? null : layers.get(layer);
    }

    public static void putModel(@NotNull Key content, int model, @NotNull String entityIdentifier) {
        MODELS.computeIfAbsent(content, ignored -> new ConcurrentHashMap<>())
                .put(model, entityIdentifier);
    }

    public static @Nullable String modelFor(@NotNull Key content, int model) {
        Map<Integer, String> layers = MODELS.get(content);
        return layers == null ? null : layers.get(model);
    }

    public static @NotNull Map<Key, Map<Integer, String>> all() {
        return Map.copyOf(ENTITIES);
    }

    public static @NotNull Map<Key, Map<Integer, String>> models() {
        return Map.copyOf(MODELS);
    }
}
