package io.kalo.platform.bedrock;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.kalo.pack.Writable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.TreeMap;

/**
 * Describes which content key owns each generated Bedrock pack file.
 *
 * <p>The compiler writes this manifest into the global pack. At staging time the stager
 * reads it and drops files whose owner is not allowed for that XUID, without guessing from
 * filenames. Paths absent from the manifest are shared assets and are always kept.</p>
 */
public final class SessionContentManifest {

    public static final String PATH = "kalo-content.json";
    public static final int FORMAT_VERSION = 1;

    private SessionContentManifest() {
    }

    public static @NotNull JsonObject json(@NotNull Map<String, String> owners) {
        JsonObject root = new JsonObject();
        root.addProperty("format_version", FORMAT_VERSION);
        JsonObject entries = new JsonObject();
        new TreeMap<>(owners).forEach(entries::addProperty);
        root.add("entries", entries);
        return root;
    }

    public static @NotNull Map<String, String> read(@Nullable Writable content) {
        if (content == null) {
            return Map.of();
        }
        try {
            JsonObject root = JsonParser.parseString(
                    new String(content.toByteArray(), StandardCharsets.UTF_8))
                    .getAsJsonObject();
            JsonObject entries = root.getAsJsonObject("entries");
            if (entries == null) {
                return Map.of();
            }
            Map<String, String> owners = new TreeMap<>();
            for (Map.Entry<String, com.google.gson.JsonElement> entry
                    : entries.entrySet()) {
                if (entry.getValue().isJsonPrimitive()) {
                    owners.put(entry.getKey(), entry.getValue().getAsString());
                }
            }
            return Map.copyOf(owners);
        } catch (IOException | RuntimeException malformed) {
            return Map.of();
        }
    }
}
