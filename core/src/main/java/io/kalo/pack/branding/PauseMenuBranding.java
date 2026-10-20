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
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
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
    // Minecraft bitmap providers reject an individual glyph bigger than 256x256.
    // Every branding texture is one glyph (one char in one row), so this is
    // also the maximum size of the resulting PNG file.
    private static final int MAX_GLYPH_DIMENSION = 256;
    // FontSet's filtered glyph path rejects advances over 32 px. Splitting
    // also ensures each atlas sprite occupies comfortably less than a page.
    private static final int TARGET_SLICE_ADVANCE = 28;
    private static final int MAX_LOGO_SLICES = 32;

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
        if (codepoint < 0xE000 || codepoint > 0xF8FF - MAX_LOGO_SLICES + 1) {
            throw new IOException("pause-menu.glyph-codepoint must leave space for the native bitmap slices (E000-F8E0)");
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
        validateMenuTranslations(pack, settings);
        Map<String, NativeGlyphRegistry.Definition> items = Map.of("pause_logo",
                new NativeGlyphRegistry.Definition("pause_logo", settings.logo(),
                        settings.codepoint(), settings.height(), settings.ascent()));
        Map<String,String> glyphs = NativeGlyphRegistry.compile(pack, items);
        writeMenuTranslations(pack, settings, glyphs);
    }

    /** Validate all language documents before any glyph pack modifications. */
    public static void validateMenuTranslations(@NotNull ResourcePack pack,
                                                @NotNull Settings settings) throws IOException {
        for (String locale : settings.locales().keySet()) {
            String path = "assets/minecraft/lang/" + locale + ".json";
            try {
                Writable existing = pack.file(path);
                if (existing != null) JsonParser.parseString(
                        new String(existing.toByteArray(), StandardCharsets.UTF_8)).getAsJsonObject();
            } catch (RuntimeException error) {
                throw new IOException("Invalid existing translation " + path, error);
            }
        }
    }

    public static void writeMenuTranslations(@NotNull ResourcePack pack, @NotNull Settings settings,
                                             @NotNull Map<String,String> glyphs) throws IOException {
        writeMenuTranslations(pack, settings, glyphs, "<glyph:pause_logo>");
    }

    public static void writeMenuTranslations(@NotNull ResourcePack pack, @NotNull Settings settings,
                                             @NotNull Map<String,String> glyphs,
                                             @NotNull String titleTemplate) throws IOException {
        String title = NativeGlyphRegistry.render(titleTemplate, glyphs);
        Map<String, JsonObject> updates = new java.util.LinkedHashMap<>();
        for (var entry : settings.locales().entrySet()) {
            String path = "assets/minecraft/lang/" + entry.getKey() + ".json";
            JsonObject object;
            try {
                Writable old = pack.file(path);
                object = old == null ? new JsonObject() :
                        JsonParser.parseString(new String(old.toByteArray(), StandardCharsets.UTF_8))
                                .getAsJsonObject().deepCopy();
            } catch (RuntimeException error) {
                throw new IOException("Invalid existing translation " + path, error);
            }
            object.addProperty("menu.game", title);
            object.addProperty("menu.paused", title);
            object.addProperty("menu.returnToGame", entry.getValue().returnToGame());
            object.addProperty("menu.disconnect", entry.getValue().disconnect());
            updates.put(path, object);
        }
        for (var item : updates.entrySet()) {
            pack.file(item.getKey(), Json.writable(item.getValue()));
        }
    }
}
