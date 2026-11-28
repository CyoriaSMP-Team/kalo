package io.kalo.pack.host;

import io.kalo.platform.java.JavaEmoteSkinOverlay;
import io.kalo.pack.ZipPackWriter;
import net.kyori.adventure.resource.ResourcePackInfo;
import net.kyori.adventure.resource.ResourcePackRequest;
import net.kyori.adventure.key.Key;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.ServerSocket;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PackDeliveryListenerTest {

    private PackHost host;

    @AfterEach
    void stopHost() {
        if (host != null) {
            host.stop();
            host = null;
        }
    }

    private static int freePort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }

    private static byte[] get(String url) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) URI.create(url).toURL().openConnection();
        try (var input = connection.getInputStream()) {
            return input.readAllBytes();
        }
    }

    private static byte[] skin(int marker) {
        byte[] bytes = new byte[32];
        bytes[0] = (byte) 0x89;
        bytes[1] = 'P';
        bytes[2] = 'N';
        bytes[3] = 'G';
        bytes[31] = (byte) marker;
        return bytes;
    }

    @Test
    void anOverlayRequestStacksThePlayersSkinOnTopOfTheGlobalPack(@TempDir Path dir)
            throws Exception {
        Path global = dir.resolve("generated.zip");
        byte[] globalBytes = "global-pack".getBytes();
        Files.write(global, globalBytes);
        host = new PackHost(global.toFile(), freePort(), "127.0.0.1");
        assertTrue(host.start());
        PackDeliveryListener delivery = new PackDeliveryListener(host, true);
        byte[] overlayBytes = ZipPackWriter.toBytes(JavaEmoteSkinOverlay.pack(
                Key.key("testpack", "body_wave"), skin(1)));

        ResourcePackRequest request = delivery.requestWithOverlay(
                overlayBytes, UUID.randomUUID());

        assertNotNull(request);
        assertEquals(1, request.packs().size(),
                "the overlay stacks on the existing global pack");
        assertFalse(request.replace());
        assertFalse(request.required(),
                "a cosmetic skin overlay must not kick a player who declines it");
        assertArrayEquals(globalBytes, get(host.url()));

        ResourcePackInfo overlay = request.packs().get(0);
        assertTrue(overlay.uri().getPath().contains("/overlay/"));
        assertArrayEquals(overlayBytes, get(overlay.uri().toString()));
        assertEquals(HexFormat.of().formatHex(
                        MessageDigest.getInstance("SHA-1").digest(overlayBytes)),
                overlay.hash());
    }

    @Test
    void twoPlayersGetDifferentOverlayPackIdsAndBytes(@TempDir Path dir) throws Exception {
        Path global = dir.resolve("generated.zip");
        Files.write(global, "global-pack".getBytes());
        host = new PackHost(global.toFile(), freePort(), "127.0.0.1");
        assertTrue(host.start());
        PackDeliveryListener delivery = new PackDeliveryListener(host, true);

        ResourcePackRequest one = delivery.requestWithOverlay(
                ZipPackWriter.toBytes(JavaEmoteSkinOverlay.pack(
                        Key.key("testpack", "body_wave"), skin(1))),
                UUID.randomUUID());
        ResourcePackRequest two = delivery.requestWithOverlay(
                ZipPackWriter.toBytes(JavaEmoteSkinOverlay.pack(
                        Key.key("testpack", "body_wave"), skin(2))),
                UUID.randomUUID());

        assertNotNull(one);
        assertNotNull(two);
        assertNotEquals(one.packs().get(0).id(), two.packs().get(0).id());
        assertNotEquals(one.packs().get(0).uri(), two.packs().get(0).uri());
        assertArrayEquals(skin(1), unzipSkin(
                get(one.packs().get(0).uri().toString())));
        assertArrayEquals(skin(2), unzipSkin(
                get(two.packs().get(0).uri().toString())));
    }

    private static byte[] unzipSkin(byte[] archive) throws IOException {
        try (java.util.zip.ZipInputStream zip = new java.util.zip.ZipInputStream(
                new java.io.ByteArrayInputStream(archive))) {
            java.util.zip.ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (entry.getName().endsWith("_skin.png")) {
                    return zip.readAllBytes();
                }
            }
        }
        return null;
    }
}
