package io.kalo.platform.java;

import com.destroystokyo.paper.profile.ProfileProperty;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.kyori.adventure.key.Key;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.Base64;
import java.util.Collection;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * Resolves a player-specific emote skin from the player's signed profile or a disk cache.
 *
 * <p>Minecraft exposes a player's skin through the signed {@code textures} profile
 * property. The value is base64-encoded JSON whose {@code textures.SKIN.url} points at a
 * PNG. Kalo only fetches allowlisted hosts, caps the response size, validates the PNG
 * signature, and writes the result atomically to
 * {@code plugins/Kalo/skins/<uuid>.png}. When resolution fails, the model's baked skin
 * stays in use.</p>
 */
public final class PlayerSkinCache {

    public static final int MAX_BYTES = 1024 * 1024;
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(8);

    private PlayerSkinCache() {
    }

    public static @NotNull Path file(@NotNull Path dataFolder, @NotNull UUID playerId) {
        return dataFolder.resolve("skins").resolve(playerId + ".png");
    }

    public static @Nullable byte[] resolve(@NotNull Path dataFolder,
                                           @NotNull UUID playerId) {
        return resolve(dataFolder, playerId, null);
    }

    /**
     * Resolves the cache only when it still belongs to {@code expectedUrl}. A cache with no
     * provenance file is treated as operator-seeded and trusted; a cache written by an
     * earlier automatic fetch is invalidated as soon as the player's profile URL changes.
     */
    public static @Nullable byte[] resolve(@NotNull Path dataFolder,
                                           @NotNull UUID playerId,
                                           @Nullable String expectedUrl) {
        Path file = file(dataFolder, playerId);
        if (!Files.isRegularFile(file)) {
            return null;
        }
        try {
            if (Files.size(file) > MAX_BYTES) {
                return null;
            }
            byte[] bytes = Files.readAllBytes(file);
            if (!isPng(bytes)) {
                return null;
            }
            if (expectedUrl == null) {
                return bytes;
            }
            Path source = sourceFile(dataFolder, playerId);
            if (!Files.isRegularFile(source)) {
                return bytes;
            }
            return Files.readString(source).trim().equals(expectedUrl.trim()) ? bytes : null;
        } catch (IOException unreadable) {
            return null;
        }
    }

    private static @NotNull Path sourceFile(@NotNull Path dataFolder,
                                            @NotNull UUID playerId) {
        return dataFolder.resolve("skins").resolve(playerId + ".url");
    }

    /** Reads the first signed {@code textures} property and returns its SKIN URL. */
    public static @Nullable String textureUrl(
            @NotNull Collection<ProfileProperty> properties) {
        for (ProfileProperty property : properties) {
            if (property != null && "textures".equalsIgnoreCase(property.getName())) {
                return decodeTextureUrl(property.getValue());
            }
        }
        return null;
    }

    /** Decodes the base64 profile property into {@code textures.SKIN.url}. */
    public static @Nullable String decodeTextureUrl(@Nullable String encoded) {
        if (encoded == null || encoded.isBlank()) {
            return null;
        }
        try {
            byte[] decoded = Base64.getMimeDecoder().decode(encoded.trim());
            JsonObject root = JsonParser.parseString(
                    new String(decoded, StandardCharsets.UTF_8)).getAsJsonObject();
            JsonObject textures = root.getAsJsonObject("textures");
            if (textures == null) {
                return null;
            }
            JsonObject skin = textures.getAsJsonObject("SKIN");
            if (skin == null || !skin.has("url") || skin.get("url").isJsonNull()) {
                return null;
            }
            String url = skin.get("url").getAsString();
            return url.isBlank() ? null : url;
        } catch (RuntimeException malformed) {
            return null;
        }
    }

    /**
     * Normalizes a skin URL and rejects anything outside the allowlist. Mojang's historic
     * plain-HTTP textures host is upgraded to HTTPS; custom hosts keep their scheme so an
     * operator can run a local mirror.
     */
    public static @Nullable URI safeUri(@Nullable String rawUrl,
                                       @NotNull Set<String> allowedHosts) {
        if (rawUrl == null || rawUrl.isBlank() || allowedHosts.isEmpty()) {
            return null;
        }
        try {
            URI parsed = URI.create(rawUrl.trim());
            String scheme = parsed.getScheme();
            String host = parsed.getHost();
            if (scheme == null || host == null || parsed.getUserInfo() != null) {
                return null;
            }
            scheme = scheme.toLowerCase(Locale.ROOT);
            host = host.toLowerCase(Locale.ROOT);
            if (!scheme.equals("http") && !scheme.equals("https")) {
                return null;
            }
            if (!allowedHosts.contains(host)) {
                return null;
            }
            if (scheme.equals("http") && (host.equals("textures.minecraft.net")
                    || host.equals("textures.mojang.com"))) {
                scheme = "https";
            }
            return new URI(scheme, null, host, parsed.getPort(),
                    parsed.getPath(), parsed.getQuery(), null);
        } catch (IllegalArgumentException | URISyntaxException invalid) {
            return null;
        }
    }

    /** Downloads, validates and stores a skin; returns the bytes only when all succeed. */
    public static @Nullable byte[] fetchAndStore(@NotNull Path dataFolder,
                                                 @NotNull UUID playerId,
                                                 @Nullable String rawUrl,
                                                 @NotNull Set<String> allowedHosts,
                                                 @NotNull HttpClient client) {
        URI uri = safeUri(rawUrl, allowedHosts);
        if (uri == null) {
            return null;
        }
        byte[] bytes = download(client, uri);
        if (bytes == null || !store(dataFolder, playerId, rawUrl, bytes)) {
            return null;
        }
        return bytes;
    }

    static @Nullable byte[] download(@NotNull HttpClient client, @NotNull URI uri) {
        try {
            HttpRequest request = HttpRequest.newBuilder(uri)
                    .timeout(REQUEST_TIMEOUT)
                    .header("User-Agent", "Kalo")
                    .GET()
                    .build();
            return read(client.send(request, HttpResponse.BodyHandlers.ofInputStream()));
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            return null;
        } catch (IOException | RuntimeException failed) {
            return null;
        }
    }

    /** Streams a bounded response body and validates its PNG signature. */
    static @Nullable byte[] read(@NotNull HttpResponse<InputStream> response) {
        try (InputStream body = response.body()) {
            if (response.statusCode() != 200) {
                return null;
            }
            String length = response.headers().firstValue("content-length").orElse(null);
            if (length != null) {
                try {
                    if (Long.parseLong(length) > MAX_BYTES) {
                        return null;
                    }
                } catch (NumberFormatException ignored) {
                    // A missing or malformed length is not fatal; the stream is capped below.
                }
            }
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int total = 0;
            int read;
            while ((read = body.read(buffer)) != -1) {
                total += read;
                if (total > MAX_BYTES) {
                    return null;
                }
                output.write(buffer, 0, read);
            }
            byte[] bytes = output.toByteArray();
            return isPng(bytes) ? bytes : null;
        } catch (IOException unreadable) {
            return null;
        }
    }

    static boolean store(@NotNull Path dataFolder, @NotNull UUID playerId,
                         byte @NotNull [] png) {
        return store(dataFolder, playerId, null, png);
    }

    static boolean store(@NotNull Path dataFolder, @NotNull UUID playerId,
                         @Nullable String sourceUrl, byte @NotNull [] png) {
        if (!isPng(png)) {
            return false;
        }
        Path target = file(dataFolder, playerId);
        Path source = sourceFile(dataFolder, playerId);
        Path temporary = null;
        try {
            Files.createDirectories(target.getParent());
            temporary = Files.createTempFile(target.getParent(), playerId + "-", ".tmp");
            Files.write(temporary, png);
            try {
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException unsupported) {
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
            }
            // Provenance follows the PNG. If it cannot be written, drop both files rather
            // than leave an old URL pointing at new bytes or an untracked automatic cache;
            // the next resolve then refetches instead of silently trusting the wrong source.
            if (sourceUrl == null) {
                Files.deleteIfExists(source);
            } else {
                try {
                    Files.writeString(source, sourceUrl.trim() + "\n");
                } catch (IOException provenanceFailed) {
                    Files.deleteIfExists(source);
                    Files.deleteIfExists(target);
                    return false;
                }
            }
            return true;
        } catch (IOException failed) {
            if (temporary != null) {
                try {
                    Files.deleteIfExists(temporary);
                } catch (IOException ignored) {
                    // Best effort: the next successful store replaces the partial file.
                }
            }
            return false;
        }
    }

    /** Pack path the Bedrock body-emote entity samples for its base texture. */
    public static @NotNull String overlayPath(@NotNull Key emoteKey) {
        return "textures/entity/" + emoteKey.namespace() + "_" + emoteKey.value()
                + "_emote_t0.png";
    }

    /**
     * Unique Java texture key for a body emote. A per-player overlay can replace this
     * path without changing any other content that shares the authored skin texture.
     */
    public static @NotNull Key javaSkinKey(@NotNull Key emoteKey) {
        return Key.key(emoteKey.namespace(), "emote/" + emoteKey.value() + "_skin");
    }

    /** Java pack path of the unique body-emote texture. */
    public static @NotNull String javaOverlayPath(@NotNull Key emoteKey) {
        Key key = javaSkinKey(emoteKey);
        return "assets/" + key.namespace() + "/textures/" + key.value() + ".png";
    }

    static boolean isPng(byte @NotNull [] bytes) {
        return bytes.length > 24
                && (bytes[0] & 0xFF) == 0x89 && bytes[1] == 'P'
                && bytes[2] == 'N' && bytes[3] == 'G';
    }
}
