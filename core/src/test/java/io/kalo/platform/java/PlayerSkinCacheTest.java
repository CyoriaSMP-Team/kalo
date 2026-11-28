package io.kalo.platform.java;

import com.destroystokyo.paper.profile.ProfileProperty;
import com.sun.net.httpserver.HttpServer;
import net.kyori.adventure.key.Key;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.Collections;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlayerSkinCacheTest {

    @TempDir
    Path temporary;

    private static byte[] png() throws Exception {
        BufferedImage image = new BufferedImage(8, 8, BufferedImage.TYPE_INT_ARGB);
        image.setRGB(0, 0, 0xFFFF0000);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ImageIO.write(image, "png", output);
        return output.toByteArray();
    }

    private static String encode(String json) {
        return Base64.getEncoder().encodeToString(json.getBytes(StandardCharsets.UTF_8));
    }

    private static HttpServer server(int status, byte[] body) throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        String path = "/skin.png";
        server.createContext(path, exchange -> {
            exchange.sendResponseHeaders(status, body.length);
            try (OutputStream output = exchange.getResponseBody()) {
                output.write(body);
            }
        });
        server.start();
        return server;
    }

    private static String url(HttpServer server) {
        return "http://127.0.0.1:" + server.getAddress().getPort() + "/skin.png";
    }

    @Test
    void aTexturePropertyDecodesToTheSkinUrl() {
        Set<ProfileProperty> properties = Set.of(new ProfileProperty("textures", encode("""
                {"textures":{"SKIN":{"url":"http://textures.minecraft.net/texture/abc"}}}
                """)));

        assertEquals("http://textures.minecraft.net/texture/abc",
                PlayerSkinCache.textureUrl(properties));
    }

    @Test
    void malformedTexturePropertiesAreIgnored() {
        assertNull(PlayerSkinCache.textureUrl(Collections.emptySet()));
        assertNull(PlayerSkinCache.textureUrl(Set.of(
                new ProfileProperty("textures", "not base64!"))));
        assertNull(PlayerSkinCache.textureUrl(Set.of(
                new ProfileProperty("textures", encode("{\"textures\":{}}")))));
        assertNull(PlayerSkinCache.textureUrl(Set.of(
                new ProfileProperty("textures", encode(
                        "{\"textures\":{\"SKIN\":{}}}")))));
    }

    @Test
    void onlyAllowlistedSkinHostsAreAcceptedAndMojangHttpIsUpgraded() {
        Set<String> hosts = Set.of("textures.minecraft.net");

        assertEquals("https://textures.minecraft.net/texture/abc",
                PlayerSkinCache.safeUri("http://textures.minecraft.net/texture/abc", hosts)
                        .toString());
        assertNull(PlayerSkinCache.safeUri("http://127.0.0.1:8080/skin.png", hosts));
        assertNull(PlayerSkinCache.safeUri("file:///etc/passwd", hosts));
        assertNull(PlayerSkinCache.safeUri(
                "https://textures.minecraft.net.evil.test/skin.png", hosts));
    }

    @Test
    void aFetchedPngIsStoredAndReused() throws Exception {
        byte[] png = png();
        HttpServer server = server(200, png);
        try {
            UUID player = UUID.randomUUID();

            byte[] fetched = PlayerSkinCache.fetchAndStore(temporary, player, url(server),
                    Set.of("127.0.0.1"), HttpClient.newHttpClient());

            assertArrayEquals(png, fetched);
            assertArrayEquals(png, PlayerSkinCache.resolve(temporary, player));
        } finally {
            server.stop(0);
        }
    }

    @Test
    void aCachedSkinIsInvalidatedWhenTheProfileUrlChanges() throws Exception {
        UUID player = UUID.randomUUID();
        byte[] png = png();
        String first = "https://textures.minecraft.net/texture/one";
        String second = "https://textures.minecraft.net/texture/two";

        assertTrue(PlayerSkinCache.store(temporary, player, first, png));

        assertArrayEquals(png, PlayerSkinCache.resolve(temporary, player, first));
        assertNull(PlayerSkinCache.resolve(temporary, player, second));
        assertArrayEquals(png, PlayerSkinCache.resolve(temporary, player),
                "an operator-cache read is still allowed without a source URL");
    }

    @Test
    void aNonPngResponseIsRejectedAndNotCached() throws Exception {
        HttpServer server = server(200, "<html>not a skin</html>"
                .getBytes(StandardCharsets.UTF_8));
        try {
            UUID player = UUID.randomUUID();

            byte[] fetched = PlayerSkinCache.fetchAndStore(temporary, player, url(server),
                    Set.of("127.0.0.1"), HttpClient.newHttpClient());

            assertNull(fetched);
            assertNull(PlayerSkinCache.resolve(temporary, player));
        } finally {
            server.stop(0);
        }
    }

    @Test
    void anOversizedResponseIsRejectedAndNotCached() throws Exception {
        byte[] oversized = new byte[PlayerSkinCache.MAX_BYTES + 1];
        oversized[0] = (byte) 0x89;
        oversized[1] = 'P';
        oversized[2] = 'N';
        oversized[3] = 'G';
        HttpServer server = server(200, oversized);
        try {
            UUID player = UUID.randomUUID();

            byte[] fetched = PlayerSkinCache.fetchAndStore(temporary, player, url(server),
                    Set.of("127.0.0.1"), HttpClient.newHttpClient());

            assertNull(fetched);
            assertNull(PlayerSkinCache.resolve(temporary, player));
        } finally {
            server.stop(0);
        }
    }

    @Test
    void aCachedPngIsResolvedForThatPlayer() throws Exception {
        UUID player = UUID.randomUUID();
        Path file = PlayerSkinCache.file(temporary, player);
        Files.createDirectories(file.getParent());
        Files.write(file, png());

        byte[] resolved = PlayerSkinCache.resolve(temporary, player);

        assertNotNull(resolved);
        assertEquals(0xFFFF0000, ImageIO.read(new java.io.ByteArrayInputStream(resolved))
                .getRGB(0, 0));
    }

    @Test
    void aMissingOrInvalidFileFallsBackToThePackSkin() throws Exception {
        UUID missing = UUID.randomUUID();
        assertNull(PlayerSkinCache.resolve(temporary, missing));

        UUID invalid = UUID.randomUUID();
        Path file = PlayerSkinCache.file(temporary, invalid);
        Files.createDirectories(file.getParent());
        Files.writeString(file, "not a png");
        assertNull(PlayerSkinCache.resolve(temporary, invalid));
    }

    @Test
    void theOverlayPathMatchesTheBedrockBodyEmoteTexture() {
        assertEquals("textures/entity/testpack_body_wave_emote_t0.png",
                PlayerSkinCache.overlayPath(Key.key("testpack", "body_wave")));
    }
}
