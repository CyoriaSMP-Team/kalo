package io.kalo.platform.java;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.kalo.content.glyph.definition.GlyphDefinition;
import io.kalo.pack.Json;
import io.kalo.pack.ResourcePack;
import io.kalo.pack.Writable;
import net.kyori.adventure.key.Key;
import org.jetbrains.annotations.NotNull;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Writes custom glyphs into the pack's font files.
 *
 * <p>Each glyph becomes a {@code bitmap} provider bound to one character:</p>
 *
 * <pre>
 * assets/minecraft/font/default.json
 *   {"providers":[{"type":"bitmap","file":"mypack:font/coin.png",
 *                  "ascent":8,"height":9,"chars":[""]}]}
 * </pre>
 *
 * <p>Providers are <em>appended</em> to whatever the font already has. A font file is a
 * list, and replacing it would drop the vanilla providers the game needs to render
 * ordinary text — writing {@code default.json} with only custom glyphs leaves a server
 * where nothing but the icons is legible.</p>
 */
public final class JavaGlyphCompiler {
    private static final Logger LOGGER = Logger.getLogger(JavaGlyphCompiler.class.getName());

    /** Vanilla's own providers, referenced so ordinary text still renders. */
    private static final List<String> DEFAULT_FONT_INCLUDES = List.of(
            "minecraft:include/space", "minecraft:include/default", "minecraft:include/unifont");

    private JavaGlyphCompiler() {
    }

    public static void compileGlyphs(@NotNull ResourcePack pack, @NotNull Iterable<GlyphDefinition> glyphs) {
        Map<Key, List<GlyphDefinition>> byFont = new TreeMap<>(java.util.Comparator.comparing(Key::asString));
        Map<Integer, String> claimed = new LinkedHashMap<>();

        for (GlyphDefinition glyph : glyphs) {
            String previous = claimed.putIfAbsent(glyph.character(), glyph.key().asString());
            if (previous != null) {
                // Two glyphs on one character means one is invisible, and which one wins
                // is down to file order — worth naming both rather than leaving a mystery.
                LOGGER.warning("Glyph " + glyph.key().asString() + " uses character U+"
                        + Integer.toHexString(glyph.character()).toUpperCase()
                        + ", already taken by " + previous + "; one of them will not render");
            }
            if (!glyph.usesPrivateUseArea()) {
                LOGGER.warning("Glyph " + glyph.key().asString() + " uses U+"
                        + Integer.toHexString(glyph.character()).toUpperCase()
                        + ", which is outside the Private Use Area — that character will be "
                        + "replaced by this image everywhere it appears in ordinary text");
            }
            byFont.computeIfAbsent(glyph.font(), ignored -> new java.util.ArrayList<>()).add(glyph);
        }

        byFont.forEach((font, fontGlyphs) -> writeFont(pack, font, fontGlyphs));
    }

    private static void writeFont(@NotNull ResourcePack pack,
                                  @NotNull Key font,
                                  @NotNull List<GlyphDefinition> glyphs) {
        String path = "assets/" + font.namespace() + "/font/" + font.value() + ".json";

        JsonArray providers = existingProviders(pack, path, font);
        for (GlyphDefinition glyph : glyphs) {
            providers.add(bitmapProvider(glyph));
        }

        JsonObject root = new JsonObject();
        root.add("providers", providers);
        pack.file(path, Json.writable(root));
    }

    /**
     * The providers already in the pack, or vanilla's defaults when writing
     * {@code minecraft:default} for the first time.
     */
    private static @NotNull JsonArray existingProviders(@NotNull ResourcePack pack,
                                                        @NotNull String path,
                                                        @NotNull Key font) {
        Writable existing = pack.file(path);
        if (existing != null) {
            try {
                JsonObject json = JsonParser.parseString(
                        new String(existing.toByteArray(), StandardCharsets.UTF_8)).getAsJsonObject();
                JsonArray providers = json.getAsJsonArray("providers");
                if (providers != null) {
                    return providers.deepCopy();
                }
            } catch (Exception e) {
                LOGGER.log(Level.WARNING, "Could not read existing font " + path + "; starting from defaults", e);
            }
        }

        JsonArray providers = new JsonArray();
        if (font.equals(Key.key("minecraft", "default"))) {
            // Overwriting the default font without these leaves a server where nothing
            // but the custom icons is readable.
            for (String include : DEFAULT_FONT_INCLUDES) {
                JsonObject reference = new JsonObject();
                reference.addProperty("type", "reference");
                reference.addProperty("id", include);
                providers.add(reference);
            }
        }
        return providers;
    }

    private static @NotNull JsonObject bitmapProvider(@NotNull GlyphDefinition glyph) {
        JsonArray chars = new JsonArray();
        chars.add(glyph.asString());

        JsonObject provider = new JsonObject();
        provider.addProperty("type", "bitmap");
        provider.addProperty("file", glyph.texture().asString() + ".png");
        provider.addProperty("ascent", glyph.ascent());
        provider.addProperty("height", glyph.height());
        provider.add("chars", chars);
        return provider;
    }
}
