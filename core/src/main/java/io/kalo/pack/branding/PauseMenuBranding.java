package io.kalo.pack.branding;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.kalo.pack.Json;
import io.kalo.pack.ResourcePack;
import io.kalo.pack.Writable;
import org.bukkit.configuration.ConfigurationSection;
import org.jetbrains.annotations.NotNull;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * First-party, opt-in Vanilla Java pause-menu branding.
 *
 * Native pack generation inserts a bitmap glyph into minecraft:default and
 * updates the Minecraft menu translation keys. It works without a client mod,
 * but cannot add genuinely clickable buttons to vanilla's ESC screen.
 *
 * The logo is a standalone PNG under the Kalo data folder, NOT hand-edited into
 * base-pack. Kalo reconstructs the result on every /kalo reload.
 */
public final class PauseMenuBranding {
    private static final String FONT_PATH = "assets/minecraft/font/default.json";
    private static final String LOGO_PATH = "assets/kalo/textures/font/pause_logo.png";
    private static final String PROVIDER_FILE = "kalo:font/pause_logo.png";
    private static final List<String> DEFAULT_FONT_REFERENCES = List.of(
            "minecraft:include/space", "minecraft:include/default", "minecraft:include/unifont");
    private static final int MAX_PNG_SIZE = 4 * 1024 * 1024;

    private PauseMenuBranding() { }

    public record Settings(
            @NotNull Path logo,
            int codepoint,
            int height,
            int ascent,
            @NotNull Map<String, Labels> locales) {
    }

    public record Labels(
            @NotNull String returnToGame,
            @NotNull String disconnect) {
    }

    /**
     * Reads a deliberately limited, declarative config. Fail-fast so malformed
     * settings cannot silently ship a missing-glyph menu to players.
     */
    public static Settings settings(@NotNull ConfigurationSection section, @NotNull Path dataFolder)
            throws IOException {
        String logoName = section.getString("logo", "branding/pause-logo.png");
        if (logoName == null || logoName.isBlank()) {
            throw new IOException("pause-menu.logo must be a PNG filename");
        }
        Path root = dataFolder.toAbsolutePath().normalize().toRealPath();
        Path file = root.resolve(logoName).normalize();
        if (!file.startsWith(root) || !Files.isRegularFile(file)) {
            throw new IOException("pause-menu.logo must exist inside the Kalo data directory: " + logoName);
        }
        file = file.toRealPath();
        if (!file.startsWith(root) || !file.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".png")) {
            throw new IOException("pause-menu.logo must be a local PNG inside the Kalo data directory");
        }

        String hex = section.getString("glyph-codepoint", "E7C0");
        int codepoint;
        try {
            codepoint = Integer.parseInt(hex.replaceFirst("(?i)^u\\+?", ""), 16);
        } catch (NumberFormatException exception) {
            throw new IOException("pause-menu.glyph-codepoint must be a hexadecimal PUA character", exception);
        }
        if (codepoint < 0xE000 || codepoint > 0xF8FF) {
            throw new IOException("pause-menu.glyph-codepoint must be a BMP private-use character (E000-F8FF)");
        }

        int height = section.getInt("height", 30);
        int ascent = section.getInt("ascent", 26);
        if (height < 1 || height > 128 || ascent < -128 || ascent > height) {
            throw new IOException("pause-menu.height must be 1-128 and ascent cannot exceed height");
        }

        java.util.Map<String, Labels> result = new java.util.LinkedHashMap<>();
        ConfigurationSection translations = section.getConfigurationSection("translations");
        if (translations == null || translations.getKeys(false).isEmpty()) {
            throw new IOException("pause-menu.translations requires at least one locale");
        }
        for (String locale : translations.getKeys(false)) {
            if (!locale.matches("[a-z]{2}_[a-z]{2}")) {
                throw new IOException("Invalid pause-menu locale: " + locale);
            }
            ConfigurationSection translation = translations.getConfigurationSection(locale);
            if (translation == null) {
                throw new IOException("pause-menu translations must contain keys per locale: " + locale);
            }
            result.put(locale, new Labels(
                    requiredLabel(translation.getString("return-to-game"), locale, "return-to-game"),
                    requiredLabel(translation.getString("disconnect"), locale, "disconnect")));
        }
        return new Settings(file, codepoint, height, ascent, Map.copyOf(result));
    }

    private static String requiredLabel(String label, String locale, String key) throws IOException {
        if (label == null || label.isBlank() || label.length() > 90 || label.chars().anyMatch(Character::isISOControl)) {
            throw new IOException("Invalid pause-menu.translations." + locale + "." + key);
        }
        return label;
    }

    public static void apply(@NotNull ResourcePack pack, @NotNull Settings settings) throws IOException {
        byte[] image = Files.readAllBytes(settings.logo());
        if (image.length > MAX_PNG_SIZE || image.length < 24) {
            throw new IOException("Pause-menu logo PNG must be 24 bytes to 4 MiB");
        }
        if (!(image[0] == (byte) 0x89 && image[1] == 'P' && image[2] == 'N' && image[3] == 'G')) {
            throw new IOException("Pause-menu logo is not a PNG");
        }
        BufferedImage preview = ImageIO.read(new ByteArrayInputStream(image));
        if (preview == null || preview.getWidth() < 1 || preview.getHeight() < 1
                || preview.getWidth() > 4096 || preview.getHeight() > 4096) {
            throw new IOException("Pause-menu PNG image has invalid dimensions");
        }

        // Prepare every changed file before mutating the pack (all-or-nothing).
        JsonObject font = readObject(pack.file(FONT_PATH), FONT_PATH);
        JsonArray providers = font.has("providers") ? font.getAsJsonArray("providers").deepCopy() : new JsonArray();
        if (providers.size() == 0) {
            for (String include : DEFAULT_FONT_REFERENCES) {
                JsonObject reference = new JsonObject();
                reference.addProperty("type", "reference");
                reference.addProperty("id", include);
                providers.add(reference);
            }
        }
        String glyph = Character.toString(settings.codepoint());
        for (var element : providers) {
            if (!element.isJsonObject()) continue;
            JsonObject existing = element.getAsJsonObject();
            if (!existing.has("chars")) continue;
            for (var row : existing.getAsJsonArray("chars")) {
                if (row.isJsonPrimitive() && row.getAsString().contains(glyph)) {
                    throw new IOException("Pause-menu glyph U+"
                            + Integer.toHexString(settings.codepoint()).toUpperCase(Locale.ROOT)
                            + " already used by another font provider");
                }
            }
        }
        JsonObject branding = new JsonObject();
        branding.addProperty("type", "bitmap");
        branding.addProperty("file", PROVIDER_FILE);
        branding.addProperty("height", settings.height());
        branding.addProperty("ascent", settings.ascent());
        JsonArray characters = new JsonArray();
        characters.add(glyph);
        branding.add("chars", characters);
        JsonArray mergedProviders = new JsonArray();
        mergedProviders.add(branding);
        for (var element : providers) mergedProviders.add(element);
        font.add("providers", mergedProviders);

        Map<String, JsonObject> languages = new java.util.LinkedHashMap<>();
        for (var entry : settings.locales().entrySet()) {
            String path = "assets/minecraft/lang/" + entry.getKey() + ".json";
            JsonObject root = readObject(pack.file(path), path);
            root.addProperty("menu.game", glyph);
            root.addProperty("menu.paused", glyph);
            root.addProperty("menu.returnToGame", entry.getValue().returnToGame());
            root.addProperty("menu.disconnect", entry.getValue().disconnect());
            languages.put(path, root);
        }

        pack.file(FONT_PATH, Json.writable(font));
        pack.file(LOGO_PATH, Writable.bytes(image));
        for (var entry : languages.entrySet()) {
            pack.file(entry.getKey(), Json.writable(entry.getValue()));
        }
    }

    private static JsonObject readObject(Writable existing, String path) throws IOException {
        if (existing == null) return new JsonObject();
        try {
            return JsonParser.parseString(new String(existing.toByteArray(), StandardCharsets.UTF_8))
                    .getAsJsonObject().deepCopy();
        } catch (RuntimeException exception) {
            throw new IOException("Cannot merge pause-menu branding with " + path, exception);
        }
    }
}
