package io.kalo.pack.host;

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

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PackHostTest {

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
        connection.setRequestMethod("GET");
        try (var input = connection.getInputStream()) {
            return input.readAllBytes();
        }
    }

    private static int status(String url) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) URI.create(url).toURL().openConnection();
        connection.setRequestMethod("GET");
        return connection.getResponseCode();
    }

    private PackHost hostFor(Path pack) throws IOException {
        host = new PackHost(pack.toFile(), freePort(), "127.0.0.1");
        assertTrue(host.start(), "host should have started");
        return host;
    }

    @Test
    void servesThePackBytesUnchanged(@TempDir Path dir) throws IOException {
        Path pack = dir.resolve("generated.zip");
        byte[] content = "not really a zip, but bytes are bytes".getBytes();
        Files.write(pack, content);

        assertArrayEquals(content, get(hostFor(pack).url()));
    }

    @Test
    void theHashMatchesWhatAClientWouldCompute(@TempDir Path dir) throws Exception {
        // The client verifies the download against this. A wrong hash makes it re-download
        // every join at best, and reject the pack at worst.
        Path pack = dir.resolve("generated.zip");
        Files.write(pack, "content".getBytes());

        MessageDigest digest = MessageDigest.getInstance("SHA-1");
        String expected = HexFormat.of().formatHex(digest.digest(Files.readAllBytes(pack)));

        assertEquals(expected, hostFor(pack).sha1());
    }

    @Test
    void regeneratingRotatesTheUrlSoClientsDoNotServeAStaleCachedPack(@TempDir Path dir) throws IOException {
        // Minecraft caches a pack by URL. Reusing it after a content change strands every
        // player on the old pack with nothing to indicate anything is wrong.
        Path pack = dir.resolve("generated.zip");
        Files.write(pack, "first".getBytes());
        PackHost host = hostFor(pack);

        String firstUrl = host.url();
        String firstHash = host.sha1();

        Files.write(pack, "second, different".getBytes());
        host.refresh();

        assertNotEquals(firstUrl, host.url());
        assertNotEquals(firstHash, host.sha1());
        assertArrayEquals("second, different".getBytes(), get(host.url()));
    }

    @Test
    void theOldUrlStopsWorkingAfterARefresh(@TempDir Path dir) throws IOException {
        Path pack = dir.resolve("generated.zip");
        Files.write(pack, "first".getBytes());
        PackHost host = hostFor(pack);

        String stale = host.url();
        host.refresh();

        assertEquals(404, status(stale));
    }

    @Test
    void replacingTheFileCannotMixNewBytesWithTheOldHash(@TempDir Path dir) throws IOException {
        Path pack = dir.resolve("generated.zip");
        Files.write(pack, "first".getBytes());
        PackHost host = hostFor(pack);

        String firstUrl = host.url();
        String firstHash = host.sha1();
        Files.write(pack, "second".getBytes());

        // ZipPackWriter replaces the file before ResourcePackManager calls refresh().
        // During that window the old URL/hash must still serve the matching old bytes.
        assertArrayEquals("first".getBytes(), get(firstUrl));
        assertEquals(firstHash, host.sha1());

        host.refresh();
        assertArrayEquals("second".getBytes(), get(host.url()));
    }

    @Test
    void anyOtherPathIs404(@TempDir Path dir) throws IOException {
        // The token is cache busting, not security — but the host still must not serve
        // whatever else happens to be reachable.
        Path pack = dir.resolve("generated.zip");
        Files.write(pack, "content".getBytes());
        PackHost host = hostFor(pack);

        String base = host.url().substring(0, host.url().lastIndexOf('/'));
        assertEquals(404, status(base + "/../secrets"));
        assertEquals(404, status(base.substring(0, base.lastIndexOf('/')) + "/pack.zip"));
    }

    @Test
    void aMissingPackIsNotAdvertisedAsAvailable(@TempDir Path dir) throws IOException {
        // Generation may not have run yet, and sending a URL that 503s is worse than
        // sending nothing.
        Path pack = dir.resolve("absent.zip");
        host = new PackHost(pack.toFile(), freePort(), "127.0.0.1");
        assertTrue(host.start());

        assertFalse(host.available());
        assertEquals("", host.sha1());
    }

    @Test
    void availableOnceThePackExists(@TempDir Path dir) throws IOException {
        Path pack = dir.resolve("generated.zip");
        Files.write(pack, "content".getBytes());

        assertTrue(hostFor(pack).available());
    }

    @Test
    void invalidPortFailsClosedInsteadOfCrashingPluginStartup(@TempDir Path dir) throws IOException {
        Path pack = dir.resolve("generated.zip");
        Files.write(pack, "content".getBytes());
        host = new PackHost(pack.toFile(), 70_000, "127.0.0.1");

        assertFalse(host.start());
        assertFalse(host.available());

        host = new PackHost(pack.toFile(), 0, "127.0.0.1");
        assertFalse(host.start(), "ephemeral port zero cannot be advertised to clients");
        assertFalse(host.available());
    }

    @Test
    void ipv6PublicAddressIsRenderedAsAValidUrl(@TempDir Path dir) {
        host = new PackHost(dir.resolve("generated.zip").toFile(), 8163, "::1");

        assertTrue(host.url().startsWith("http://[::1]:8163/"));
    }
}
