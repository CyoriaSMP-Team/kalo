package io.kalo.converter;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Finds Java Edition custom player head textures referenced by DeluxeMenus GUI
 * files, without modifying the source menus or downloading third-party images.
 *
 * Geyser can pre-register skin texture hashes so it generates corresponding
 * Bedrock inventory/skull geometry and textures at startup.
 */
public final class GuiCustomSkullScanner {
    private static final int MAX_FILES = 512;
    private static final long MAX_FILE_BYTES = 2L * 1024 * 1024;
    private static final int MAX_PROFILES = 1024;
    private static final int MAX_BASE64_LENGTH = 8192;
    private static final Pattern HEAD = Pattern.compile(
            "(?im)^\\s*material\\s*:\\s*['\\x22]?basehead-([A-Za-z0-9+/=]+)");
    private static final Pattern URL = Pattern.compile(
            "(?i)^https?://textures\\.minecraft\\.net/texture/([a-f0-9]{40,128})$");

    private GuiCustomSkullScanner() {}

    public record ScanResult(@NotNull List<String> skinHashes, int filesScanned,
                             int invalidProfiles) {}

    /** Safe against missing directories and broken YAML values; does not follow symlinks. */
    public static @NotNull ScanResult discover(@NotNull Path menuDirectory) throws IOException {
        if (!Files.isDirectory(menuDirectory)) return new ScanResult(List.of(), 0, 0);
        Set<String> hashes = new TreeSet<>();
        int invalid = 0;
        int count = 0;
        List<Path> menus;
        try (Stream<Path> paths = Files.walk(menuDirectory, 8)) {
            menus = paths.filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".yml")
                            || p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".yaml"))
                    .sorted().limit(MAX_FILES).toList();
        }
        for (Path file : menus) {
            // Symlinks can otherwise escape this plugin's data root.
            if (Files.isSymbolicLink(file) || Files.size(file) > MAX_FILE_BYTES) continue;
            String text = Files.readString(file, StandardCharsets.UTF_8);
            count++;
            Matcher matcher = HEAD.matcher(text);
            while (matcher.find()) {
                String hash = decodeTextureHash(matcher.group(1));
                if (hash == null) invalid++;
                else hashes.add(hash);
                if (hashes.size() >= MAX_PROFILES) return new ScanResult(List.copyOf(hashes), count, invalid);
            }
        }
        return new ScanResult(List.copyOf(hashes), count, invalid);
    }

    /** Return the Java menu's original Minecraft skin image URL for Cumulus Forms. */
    static String decodeMaterialHash(String material) {
        if (material == null || !material.regionMatches(true, 0, "basehead-", 0, 9)) return null;
        return decodeTextureHash(material.substring(9));
    }

    static String decodeTextureUrl(String material) {
        if (material == null || !material.regionMatches(true, 0, "basehead-", 0, 9)) return null;
        String hash = decodeTextureHash(material.substring(9));
        return hash == null ? null : "https://textures.minecraft.net/texture/" + hash;
    }

    static String decodeTextureHash(String profile) {
        if (profile == null || profile.isEmpty() || profile.length() > MAX_BASE64_LENGTH) return null;
        try {
            byte[] bytes = Base64.getDecoder().decode(profile);
            if (bytes.length > MAX_BASE64_LENGTH) return null;
            JsonObject json = JsonParser.parseString(new String(bytes, StandardCharsets.UTF_8))
                    .getAsJsonObject();
            String textureUrl = json.getAsJsonObject("textures")
                    .getAsJsonObject("SKIN").get("url").getAsString();
            Matcher matcher = URL.matcher(textureUrl);
            return matcher.matches() ? matcher.group(1).toLowerCase(Locale.ROOT) : null;
        } catch (RuntimeException ex) {
            return null;
        }
    }
}
