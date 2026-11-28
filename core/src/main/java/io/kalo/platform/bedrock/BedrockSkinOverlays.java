package io.kalo.platform.bedrock;

import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Per-XUID texture overrides staged into that session's Bedrock pack. */
public final class BedrockSkinOverlays {

    private static final Map<String, Map<String, byte[]>> BY_XUID =
            new ConcurrentHashMap<>();

    private BedrockSkinOverlays() {
    }

    public static void put(@NotNull String xuid, @NotNull String packPath,
                           byte @NotNull [] png) {
        BY_XUID.computeIfAbsent(BedrockPackStager.key(xuid),
                ignored -> new ConcurrentHashMap<>()).put(packPath, png.clone());
    }

    public static @NotNull Map<String, byte[]> forXuid(@NotNull String xuid) {
        Map<String, byte[]> overlays = BY_XUID.get(BedrockPackStager.key(xuid));
        if (overlays == null) {
            return Map.of();
        }
        Map<String, byte[]> copy = new java.util.TreeMap<>();
        overlays.forEach((path, bytes) -> copy.put(path, bytes.clone()));
        return Map.copyOf(copy);
    }

    public static void forget(@NotNull String xuid) {
        BY_XUID.remove(BedrockPackStager.key(xuid));
    }

    public static void clear() {
        BY_XUID.clear();
    }
}
