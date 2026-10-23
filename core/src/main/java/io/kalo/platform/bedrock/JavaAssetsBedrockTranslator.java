package io.kalo.platform.bedrock;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.kalo.pack.Json;
import io.kalo.pack.ResourcePack;
import io.kalo.pack.Writable;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Translates portable Java assets into Bedrock's native resource-pack layout.
 *
 * <p>Geyser item/block identity is still provided by BedrockPackCompiler: a PNG or
 * model name alone does not reveal the ItemStack material, predicates, or custom model
 * data the server actually sends. This stage translates the portable resources and
 * makes all supported namespaced textures addressable through Bedrock atlases without
 * inventing registrations that would show incorrect items in-game.</p>
 */
public final class JavaAssetsBedrockTranslator {
    private static final int MAX_ASSETS = 16_384;
    private static final int MAX_LANGUAGE_BYTES = 2 * 1024 * 1024;
    private static final int MAX_LANGUAGE_LINES = 20_000;
    private static final int MAX_REPORT_EXAMPLES = 12;

    private JavaAssetsBedrockTranslator() {}

    public record Report(int textures, int vanillaTextures, int soundFiles,
                         int locales, int languageEntries, int unsupportedAssets,
                         @NotNull List<String> unsupportedExamples) {
        public Report {
            unsupportedExamples = List.copyOf(unsupportedExamples);
        }
    }

    /** Generates Bedrock texture atlases, portable sounds and .lang files in one pass. */
    public static @NotNull Report translate(@NotNull ResourcePack javaSource,
                                            @NotNull ResourcePack bedrock,
                                            @NotNull JsonObject itemTextures,
                                            @NotNull JsonObject terrainTextures) {
        List<Map.Entry<String, Writable>> source = javaSource.files().entrySet().stream()
                .sorted(Map.Entry.comparingByKey()).toList();
        JsonObject sounds = new JsonObject();
        JsonObject languages = new JsonObject();
        Set<String> locales = new HashSet<>();
        ConversionIssues unsupported = new ConversionIssues();
        int textureCount = 0;
        int vanillaCount = 0;
        int soundCount = 0;
        int entryCount = 0;
        int assetCount = 0;

        for (Map.Entry<String, Writable> asset : source) {
            String path = asset.getKey();
            if (!path.startsWith("assets/")) continue;
            if (++assetCount > MAX_ASSETS) {
                unsupported.note(path + " (asset limit)");
                break;
            }
            String[] parts = path.split("/", 4);
            if (parts.length != 4 || !parts[1].matches("[a-z0-9_.-]+")) {
                unsupported.note(path);
                continue;
            }
            String namespace = parts[1];
            String section = parts[2];
            String relative = parts[3];
            if (relative.startsWith("._") || relative.contains("/._")) continue;

            if (section.equals("textures") && relative.endsWith(".png")) {
                // Do not lie to clients by copying a Java ZIP with assets/ unchanged.
                // Bedrock needs native textures/ paths and atlases instead.
                String stem = relative.substring(0, relative.length() - 4);
                String destination = "textures/kalo/java/" + namespace + "/" + relative;
                if (bedrock.file(destination) == null) bedrock.file(destination, asset.getValue());
                String bedrockPath = destination.substring(0, destination.length() - 4);
                String atlasKey = "java_" + namespace + "_" + stem.replaceAll("[^a-zA-Z0-9_]", "_");
                // Hash-resistant key: two distinct Java paths that normalize to the
                // same atlas name must not silently overwrite one another.
                if (itemTextures.has(atlasKey) || terrainTextures.has(atlasKey)) {
                    atlasKey += "_" + Integer.toUnsignedString(path.hashCode(), 36);
                }
                JsonObject texture = new JsonObject();
                texture.addProperty("textures", bedrockPath);
                itemTextures.add(atlasKey, texture);
                if (stem.startsWith("block/") || stem.startsWith("blocks/")) {
                    terrainTextures.add(atlasKey, texture.deepCopy());
                }
                // Vanilla overrides can affect Bedrock's default texture references
                // without a custom Geyser item registration. Names that differ
                // between editions require an explicit mapping; we never guess them.
                if (namespace.equals("minecraft") && stem.startsWith("item/")) {
                    String vanilla = "textures/items/" + stem.substring(5) + ".png";
                    if (bedrock.file(vanilla) == null) {
                        bedrock.file(vanilla, asset.getValue());
                        vanillaCount++;
                    }
                } else if (namespace.equals("minecraft") && stem.startsWith("block/")) {
                    String vanilla = "textures/blocks/" + stem.substring(6) + ".png";
                    if (bedrock.file(vanilla) == null) {
                        bedrock.file(vanilla, asset.getValue());
                        vanillaCount++;
                    }
                }
                textureCount++;
            } else if (section.equals("sounds") && relative.endsWith(".ogg")) {
                String stem = relative.substring(0, relative.length() - 4);
                String target = "sounds/kalo/java/" + namespace + "/" + relative;
                if (bedrock.file(target) == null) bedrock.file(target, asset.getValue());
                String soundKey = "kalo.java." + namespace + "." + stem.replace('/', '.');
                JsonObject entry = new JsonObject();
                entry.addProperty("category", "neutral");
                com.google.gson.JsonArray soundList = new com.google.gson.JsonArray();
                soundList.add(target.substring(0, target.length() - 4));
                entry.add("sounds", soundList);
                sounds.add(soundKey, entry);
                soundCount++;
            } else if (section.equals("lang") && relative.endsWith(".json")) {
                String locale = relative.substring(0, relative.length() - 5);
                if (!locale.matches("[a-z]{2}_[a-z]{2}") || relative.contains("/")) {
                    unsupported.note(path);
                    continue;
                }
                try {
                    byte[] data = asset.getValue().toByteArray();
                    if (data.length > MAX_LANGUAGE_BYTES) {
                        unsupported.note(path + " (language too large)");
                        continue;
                    }
                    JsonElement parsed = JsonParser.parseString(new String(data, StandardCharsets.UTF_8));
                    if (!parsed.isJsonObject()) {
                        unsupported.note(path + " (not a JSON object)");
                        continue;
                    }
                    JsonObject strings = languages.has(locale) ? languages.getAsJsonObject(locale) : new JsonObject();
                    for (Map.Entry<String, JsonElement> value : parsed.getAsJsonObject().entrySet()) {
                        if (!value.getValue().isJsonPrimitive() || !value.getValue().getAsJsonPrimitive().isString()) continue;
                        if (strings.size() >= MAX_LANGUAGE_LINES) break;
                        strings.addProperty(value.getKey(), value.getValue().getAsString());
                        entryCount++;
                    }
                    languages.add(locale, strings);
                    locales.add(locale);
                } catch (IOException | RuntimeException broken) {
                    unsupported.note(path + " (invalid language)");
                }
            } else if (section.equals("models") || section.equals("items")
                    || section.equals("blockstates") || section.equals("shaders")
                    || section.equals("font") || (section.equals("textures") && relative.endsWith(".mcmeta"))) {
                // Model predicates/shaders/font providers are Java-only. The caller
                // reports these rather than claiming they were converted. Runtime
                // item-model bindings go through the existing BedrockPackCompiler.
                unsupported.note(path);
            }
        }

        if (soundCount > 0) {
            JsonObject definitions = new JsonObject();
            definitions.addProperty("format_version", "1.14.0");
            definitions.add("sound_definitions", sounds);
            bedrock.file("sounds/sound_definitions.json", Json.writable(definitions));
        }
        if (!locales.isEmpty()) {
            com.google.gson.JsonArray enabled = new com.google.gson.JsonArray();
            locales.stream().sorted().forEach(locale -> {
                String bedrockLocale = locale.substring(0, 2) + "_"
                        + locale.substring(3).toUpperCase(java.util.Locale.ROOT);
                enabled.add(bedrockLocale);
                JsonObject strings = languages.getAsJsonObject(locale);
                StringBuilder translated = new StringBuilder();
                strings.entrySet().stream().sorted(Comparator.comparing(Map.Entry::getKey))
                        .forEach(entry -> translated.append(entry.getKey()).append('=')
                                .append(entry.getValue().getAsString()
                                        .replace("\\", "\\\\").replace("\r", "")
                                        .replace("\n", "\\n"))
                                .append('\n'));
                bedrock.file("texts/" + bedrockLocale + ".lang", Writable.string(translated.toString()));
            });
            bedrock.file("texts/languages.json", Json.writable(enabled));
        }
        return new Report(textureCount, vanillaCount, soundCount, locales.size(),
                entryCount, unsupported.count, unsupported.examples);
    }

    private static final class ConversionIssues {
        private int count;
        private final List<String> examples = new ArrayList<>();

        void note(String path) {
            count++;
            // Bounded report: do not retain thousands of model paths in memory.
            if (examples.size() < MAX_REPORT_EXAMPLES) examples.add(path);
        }
    }
}
