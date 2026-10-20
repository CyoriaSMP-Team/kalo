package io.kalo.pack.branding;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.kalo.pack.Json;
import io.kalo.pack.ResourcePack;
import io.kalo.pack.Writable;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Kalo-owned named bitmap glyph registry, independently implemented using
 * vanilla Minecraft font JSON. No Oraxen source code is incorporated.
 *
 * Each glyph is a PNG (or several contiguous codepoints for wide logos).
 * Definitions come from Kalo/glyphs/*.yml, and are available to UI templates
 * as <glyph:name>. Output is deterministic and preserves existing providers.
 */
public final class NativeGlyphRegistry {
    private static final String FONT_PATH = "assets/minecraft/font/default.json";
    private static final Pattern PLACEHOLDER = Pattern.compile("<glyph:([a-z0-9_]+)>");
    private static final Pattern NAME = Pattern.compile("[a-z][a-z0-9_]{0,48}");
    private static final int GLYPH_MAX = 256;
    private static final int MAX_SLICES = 32;
    private static final double SAFE_ADVANCE = 30;
    private static final int MAX_PNG_BYTES = 4 * 1024 * 1024;

    public record Definition(String name, Path image, int codepoint, int height, int ascent) { }

    private NativeGlyphRegistry() { }

    public static Map<String, Definition> load(Path dataDir) throws IOException {
        Path root = dataDir.toRealPath();
        Path directory = root.resolve("glyphs");
        Map<String, Definition> definitions = new TreeMap<>();
        if (!Files.isDirectory(directory)) return definitions;
        try (DirectoryStream<Path> files = Files.newDirectoryStream(directory, "*.yml")) {
            for (Path file : files) {
                var yaml = YamlConfiguration.loadConfiguration(file.toFile());
                for (String name : yaml.getKeys(false)) {
                    if (!NAME.matcher(name).matches()) {
                        throw new IOException("Invalid glyph name in " + file.getFileName() + ": " + name);
                    }
                    ConfigurationSection entry = yaml.getConfigurationSection(name);
                    if (entry == null) throw new IOException("Glyph " + name + " must be a YAML object");
                    String imagePath = entry.getString("texture", "");
                    if (imagePath == null || imagePath.isBlank()) {
                        throw new IOException("Glyph " + name + " requires a texture file");
                    }
                    Path image = root.resolve(imagePath).normalize();
                    if (!image.startsWith(root) || !Files.isRegularFile(image)
                            || !image.toRealPath().startsWith(root)
                            || !image.toString().toLowerCase(java.util.Locale.ROOT).endsWith(".png")) {
                        throw new IOException("Glyph " + name + " must use a PNG inside plugins/Kalo");
                    }
                    String hex = entry.getString("codepoint", "");
                    if (hex == null || !hex.matches("(?i)(?:U\\+)?[0-9a-f]{4,5}")) {
                        throw new IOException("Glyph " + name + " requires a hex codepoint, e.g. E7D0");
                    }
                    int codepoint = Integer.parseInt(hex.replaceFirst("(?i)^U\\+", ""), 16);
                    Definition def = new Definition(name, image.toRealPath(), codepoint,
                            entry.getInt("height", 16), entry.getInt("ascent", 12));
                    if (definitions.putIfAbsent(name, def) != null) {
                        throw new IOException("Duplicate named glyph " + name);
                    }
                }
            }
        }
        return definitions;
    }

    /**
     * Compiles all glyph providers together, without discarding third-party
     * fonts and without partial writes if a definition is invalid.
     */
    public static Map<String, String> compile(ResourcePack pack, Map<String, Definition> defs) throws IOException {
        if (defs.isEmpty()) return Map.of();
        JsonObject font;
        try {
            Writable existing = pack.file(FONT_PATH);
            font = existing == null ? new JsonObject() :
                    JsonParser.parseString(new String(existing.toByteArray(), StandardCharsets.UTF_8))
                            .getAsJsonObject().deepCopy();
        } catch (RuntimeException error) {
            throw new IOException("Invalid existing font/default.json", error);
        }
        JsonArray providers = font.has("providers")
                ? font.getAsJsonArray("providers").deepCopy() : new JsonArray();
        if (providers.size() == 0) {
            for (String ref : List.of("minecraft:include/space", "minecraft:include/default",
                    "minecraft:include/unifont")) {
                JsonObject provider = new JsonObject();
                provider.addProperty("type", "reference");
                provider.addProperty("id", ref);
                providers.add(provider);
            }
        }
        Set<Integer> reserved = new HashSet<>();
        for (var element : providers) {
            if (!element.isJsonObject() || !element.getAsJsonObject().has("chars")) continue;
            for (var row : element.getAsJsonObject().getAsJsonArray("chars")) {
                if (!row.isJsonPrimitive()) continue;
                row.getAsString().codePoints().filter(n -> n > 0).forEach(reserved::add);
            }
        }
        Map<String, byte[]> assets = new TreeMap<>();
        Map<String, String> symbols = new LinkedHashMap<>();
        JsonArray front = new JsonArray();
        for (var entry : new TreeMap<>(defs).entrySet()) {
            Definition def = entry.getValue();
            if (!NAME.matcher(entry.getKey()).matches() || !entry.getKey().equals(def.name())) {
                throw new IOException("Invalid glyph id " + entry.getKey());
            }
            if (def.codepoint() < 0xE000 || def.codepoint() > 0xF8FF - MAX_SLICES + 1) {
                throw new IOException("Glyph " + def.name() + " codepoint outside safe private-use range");
            }
            if (def.height() < 1 || def.height() > 128
                    || def.ascent() < -128 || def.ascent() > def.height()) {
                throw new IOException("Glyph " + def.name() + " has invalid ascent/height");
            }
            byte[] raw = Files.readAllBytes(def.image());
            if (raw.length < 24 || raw.length > MAX_PNG_BYTES) {
                throw new IOException("Glyph " + def.name() + " PNG exceeds 4MiB or is too short");
            }
            BufferedImage input = ImageIO.read(new ByteArrayInputStream(raw));
            if (input == null || input.getWidth() < 1 || input.getHeight() < 1
                    || input.getWidth() > 4096 || input.getHeight() > 4096) {
                throw new IOException("Glyph " + def.name() + " PNG is malformed or too large");
            }
            int width = input.getWidth(), height = input.getHeight();
            int dstW = width, dstH = height;
            if (width > GLYPH_MAX || height > GLYPH_MAX) {
                double ratio = Math.min((double) GLYPH_MAX / width, (double) GLYPH_MAX / height);
                dstW = Math.max(1, Math.min(GLYPH_MAX, (int) Math.round(width * ratio)));
                dstH = Math.max(1, Math.min(GLYPH_MAX, (int) Math.round(height * ratio)));
            }
            int slices = 1;
            while ((double) dstW / slices * def.height() / dstH > SAFE_ADVANCE && slices < MAX_SLICES) {
                slices *= 2;
            }
            if ((double) dstW / slices * def.height() / dstH > SAFE_ADVANCE) {
                throw new IOException("Glyph " + def.name() + " is too wide after 32 slices");
            }
            StringBuilder glyphs = new StringBuilder();
            for (int i = 0; i < slices; i++) {
                int value = def.codepoint() + i;
                if (!reserved.add(value)) throw new IOException("Duplicate glyph codepoint U+"
                        + Integer.toHexString(value).toUpperCase(java.util.Locale.ROOT));
                glyphs.appendCodePoint(value);
            }
            byte[] sprite = raw;
            int paddedWidth = dstW + (slices - dstW % slices) % slices;
            if (dstW != width || dstH != height || paddedWidth != dstW) {
                BufferedImage sheet = new BufferedImage(paddedWidth, dstH, BufferedImage.TYPE_INT_ARGB);
                Graphics2D g = sheet.createGraphics();
                try {
                    g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                            RenderingHints.VALUE_INTERPOLATION_BICUBIC);
                    g.drawImage(input, 0, 0, dstW, dstH, null);
                } finally {
                    g.dispose();
                }
                ByteArrayOutputStream result = new ByteArrayOutputStream();
                if (!ImageIO.write(sheet, "png", result)) throw new IOException("Cannot encode glyph sprite");
                sprite = result.toByteArray();
            }
            String assetPath = "assets/kalo/textures/font/" + def.name() + ".png";
            String providerId = "kalo:font/" + def.name() + ".png";
            JsonObject provider = new JsonObject();
            provider.addProperty("type", "bitmap");
            provider.addProperty("file", providerId);
            provider.addProperty("height", def.height());
            provider.addProperty("ascent", def.ascent());
            JsonArray chars = new JsonArray();
            chars.add(glyphs.toString());
            provider.add("chars", chars);
            front.add(provider);
            assets.put(assetPath, sprite);
            symbols.put(def.name(), glyphs.toString());
        }
        for (var provider : providers) front.add(provider);
        font.add("providers", front);

        // Everything validated; commit changes to the in-memory pack.
        pack.file(FONT_PATH, Json.writable(font));
        for (var entry : assets.entrySet()) {
            pack.file(entry.getKey(), Writable.bytes(entry.getValue()));
        }
        return Map.copyOf(symbols);
    }

    /** Replaces named placeholders in a Kalo-managed menu/language template. */
    public static String render(String template, Map<String, String> symbols) throws IOException {
        Matcher match = PLACEHOLDER.matcher(template);
        StringBuffer out = new StringBuffer();
        while (match.find()) {
            String symbol = symbols.get(match.group(1));
            if (symbol == null) throw new IOException("Undefined glyph placeholder: " + match.group(1));
            match.appendReplacement(out, Matcher.quoteReplacement(symbol));
        }
        match.appendTail(out);
        return out.toString();
    }
}
