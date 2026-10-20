package io.kalo.pack.host;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class RelayPackPublisherTest {
    private HttpServer relay;
    private PackHost host;
    @AfterEach
    void cleanup() {
        if (host != null) host.stop();
        if (relay != null) relay.stop(0);
    }

    @Test
    void publishBeforeAdvertiseAndKeepLastGoodOnFailure(@TempDir Path temp) throws Exception {
        byte[] first = "first zip payload".getBytes(StandardCharsets.UTF_8);
        byte[] second = "second zip payload".getBytes(StandardCharsets.UTF_8);
        Path pack = temp.resolve("generated.zip");
        Path token = temp.resolve("credential");
        Files.writeString(token, "test-secret");
        Files.write(pack, first);

        AtomicBoolean fail = new AtomicBoolean(false);
        AtomicInteger requests = new AtomicInteger(0);
        relay = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        relay.createContext("/v1/packs/", exchange -> {
            try (exchange) {
                requests.incrementAndGet();
                byte[] body = exchange.getRequestBody().readAllBytes();
                assertEquals("Bearer test-secret", exchange.getRequestHeaders().getFirst("Authorization"));
                if (fail.get()) {
                    exchange.sendResponseHeaders(503, -1);
                } else {
                    String hash = exchange.getRequestHeaders().getFirst("X-Pack-SHA256");
                    assertNotNull(hash);
                    var digest = java.security.MessageDigest.getInstance("SHA-256").digest(body);
                    assertEquals(java.util.HexFormat.of().formatHex(digest), hash);
                    byte[] response = hash.getBytes(StandardCharsets.UTF_8);
                    exchange.sendResponseHeaders(201, response.length);
                    exchange.getResponseBody().write(response);
                }
            } catch (Exception error) {
                throw new RuntimeException(error);
            }
        });
        relay.start();
        String origin = "http://127.0.0.1:" + relay.getAddress().getPort() + "/v1/packs";
        RelayPackPublisher publisher = new RelayPackPublisher(origin, token);
        host = new PackHost(pack.toFile(), 38274, "127.0.0.1",
                "https://kalo.example", "127.0.0.1", publisher);

        assertTrue(host.start());
        assertNull(host.server(), "Direct mode should not listen on a local HTTP port");
        assertNull(host.snapshot(), "No Relay acknowledgement, no player-visible pack");
        assertTrue(host.publishToRelay());
        assertNotNull(host.snapshot());
        String firstUrl = host.url();
        String firstHash = host.sha1();
        assertEquals(1, requests.get());
        assertFalse(host.publishToRelay(), "Same bytes must not be re-uploaded");
        assertEquals(1, requests.get());

        Files.write(pack, second);
        fail.set(true);
        assertThrows(IOException.class, host::publishToRelay);
        assertEquals(firstUrl, host.url(), "Failed upload must not activate a new URL");
        assertEquals(firstHash, host.sha1(), "Failed upload must retain published SHA-1");

        fail.set(false);
        assertTrue(host.publishToRelay());
        assertNotEquals(firstUrl, host.url());
        assertNotEquals(firstHash, host.sha1());
        assertEquals(3, requests.get());

        String newerUrl = host.url();
        host.stop();
        host = new PackHost(pack.toFile(), 38274, "127.0.0.1",
                "https://kalo.example", "127.0.0.1", publisher);
        assertTrue(host.start());
        assertNull(host.snapshot());
        assertTrue(host.publishToRelay());
        assertEquals(newerUrl, host.url(), "Same bytes should give stable URLs across reboot");
    }

    @Test
    void rejectsUnexpectedUploadEndpoint(@TempDir Path tmp) {
        assertThrows(IllegalArgumentException.class, () ->
                new RelayPackPublisher("https://kalo.example/other", tmp.resolve("secret")));
    }
}
