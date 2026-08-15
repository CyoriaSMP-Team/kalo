package io.kalo.geyser;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * The half of Kalo's output that the Geyser side needs.
 *
 * <p>The server plugin and this extension run in different processes — Paper and Geyser —
 * so they share a file, not classes. That file is {@code bedrock-mappings.json}, written
 * by the plugin next to the generated packs.</p>
 */
public record KaloMappings(List<BlockEntry> blocks) {

    /**
     * One custom block, as both platforms see it.
     *
     * @param javaKey          the Kalo content key, e.g. {@code mypack:ruby_block}
     * @param bedrockId        the Bedrock block identifier to register
     * @param javaCarrierState which of the note block states this block occupies on Java;
     *                         {@code null} when the plugin had not assigned one yet
     */
    public record BlockEntry(String javaKey, String bedrockId, Integer javaCarrierState) {
    }

    public static KaloMappings empty() {
        return new KaloMappings(List.of());
    }

    /**
     * Reads the mapping file.
     *
     * <p>A missing file is not an error: Geyser may well start before the Paper side has
     * ever generated one, and an extension that refused to load in that case would be
     * harder to set up than one that simply has nothing to register yet.</p>
     */
    public static KaloMappings load(Path file) throws IOException {
        if (!Files.exists(file)) {
            return empty();
        }

        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            JsonElement parsed = JsonParser.parseReader(reader);
            if (!parsed.isJsonObject()) {
                throw new IOException("expected a JSON object in " + file);
            }
            return parse(parsed.getAsJsonObject());
        }
    }

    static KaloMappings parse(JsonObject root) throws IOException {
        List<BlockEntry> blocks = new ArrayList<>();

        JsonElement blockArray = root.get("kalo:blocks");
        if (blockArray != null && blockArray.isJsonArray()) {
            for (JsonElement element : blockArray.getAsJsonArray()) {
                if (!element.isJsonObject()) {
                    continue;
                }
                JsonObject entry = element.getAsJsonObject();

                String javaKey = string(entry, "java_key");
                String bedrockId = string(entry, "bedrock_identifier");
                if (javaKey == null || bedrockId == null) {
                    throw new IOException("block entry is missing java_key or bedrock_identifier: " + entry);
                }

                Integer state = entry.has("java_carrier_state") && !entry.get("java_carrier_state").isJsonNull()
                        ? entry.get("java_carrier_state").getAsInt()
                        : null;

                blocks.add(new BlockEntry(javaKey, bedrockId, state));
            }
        }

        return new KaloMappings(List.copyOf(blocks));
    }

    private static String string(JsonObject object, String member) {
        JsonElement element = object.get(member);
        return element != null && element.isJsonPrimitive() ? element.getAsString() : null;
    }

    /** Convenience for {@code kalo:blocks} being absent entirely. */
    public boolean isEmpty() {
        return blocks.isEmpty();
    }
}
