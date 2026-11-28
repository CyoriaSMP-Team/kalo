package io.kalo.platform.java;

import com.sun.net.httpserver.HttpServer;
import org.bukkit.configuration.file.YamlConfiguration;
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
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlayerSkinServiceTest {

    @TempDir
    Path temporary;

    private static byte[] png() throws Exception {
        BufferedImage image = new BufferedImage(8, 8, BufferedImage.TYPE_INT_ARGB);
        image.setRGB(0, 0, 0xFFFF0000);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ImageIO.write(image, "png", output);
        return output.toByteArray();
    }

    private static HttpServer server(byte[] body, AtomicInteger requests) throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/skin.png", exchange -> {
            requests.incrementAndGet();
            exchange.sendResponseHeaders(200, body.length);
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
    void aCachedSkinResolvesWithoutFetching() throws Exception {
        byte[] png = png();
        UUID player = UUID.randomUUID();
        Path file = PlayerSkinCache.file(temporary, player);
        Files.createDirectories(file.getParent());
        Files.write(file, png);
        AtomicInteger requests = new AtomicInteger();
        HttpServer server = server(png, requests);
        try {
            PlayerSkinService service = new PlayerSkinService(temporary, true,
                    Set.of("127.0.0.1"), HttpClient.newHttpClient());
            CompletableFuture<byte[]> resolved = new CompletableFuture<>();

            service.resolve(player, url(server), resolved::complete);

            assertArrayEquals(png, resolved.get(5, TimeUnit.SECONDS));
            assertTrue(requests.get() == 0);
        } finally {
            server.stop(0);
        }
    }

    @Test
    void aRemoteSkinIsFetchedOnceAndReusedFromCache() throws Exception {
        byte[] png = png();
        UUID player = UUID.randomUUID();
        AtomicInteger requests = new AtomicInteger();
        HttpServer server = server(png, requests);
        try {
            PlayerSkinService service = new PlayerSkinService(temporary, true,
                    Set.of("127.0.0.1"), HttpClient.newHttpClient());
            CompletableFuture<byte[]> first = new CompletableFuture<>();

            service.resolve(player, url(server), first::complete);

            assertArrayEquals(png, first.get(5, TimeUnit.SECONDS));
            assertTrue(requests.get() == 1);
            assertArrayEquals(png, PlayerSkinCache.resolve(temporary, player));

            CompletableFuture<byte[]> second = new CompletableFuture<>();
            service.resolve(player, url(server), second::complete);
            assertArrayEquals(png, second.get(5, TimeUnit.SECONDS));
            assertTrue(requests.get() == 1);
        } finally {
            server.stop(0);
        }
    }

    @Test
    void aChangedProfileUrlRefetchesTheCachedSkin() throws Exception {
        byte[] first = png();
        byte[] second = png();
        second[second.length - 1] = 7;
        AtomicInteger requests = new AtomicInteger();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/one.png", exchange -> {
            requests.incrementAndGet();
            exchange.sendResponseHeaders(200, first.length);
            try (OutputStream output = exchange.getResponseBody()) {
                output.write(first);
            }
        });
        server.createContext("/two.png", exchange -> {
            requests.incrementAndGet();
            exchange.sendResponseHeaders(200, second.length);
            try (OutputStream output = exchange.getResponseBody()) {
                output.write(second);
            }
        });
        server.start();
        try {
            String base = "http://127.0.0.1:" + server.getAddress().getPort();
            PlayerSkinService service = new PlayerSkinService(temporary, true,
                    Set.of("127.0.0.1"), HttpClient.newHttpClient());
            UUID player = UUID.randomUUID();

            CompletableFuture<byte[]> one = new CompletableFuture<>();
            service.resolve(player, base + "/one.png", one::complete);
            assertArrayEquals(first, one.get(5, TimeUnit.SECONDS));

            CompletableFuture<byte[]> two = new CompletableFuture<>();
            service.resolve(player, base + "/two.png", two::complete);

            assertArrayEquals(second, two.get(5, TimeUnit.SECONDS));
            assertTrue(requests.get() == 2, "a profile URL change must fetch again");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void fetchDisabledLeavesTheBakedSkinInUse() {
        PlayerSkinService service = new PlayerSkinService(temporary, false,
                Set.of("127.0.0.1"), HttpClient.newHttpClient());
        AtomicBoolean called = new AtomicBoolean();

        service.resolve(UUID.randomUUID(), "http://127.0.0.1/skin.png",
                bytes -> called.set(true));

        assertFalse(called.get());
        assertNull(PlayerSkinCache.resolve(temporary, UUID.randomUUID()));
    }

    @Test
    void concurrentRequestsForOnePlayerShareOneFetch() throws Exception {
        byte[] png = png();
        UUID player = UUID.randomUUID();
        AtomicInteger requests = new AtomicInteger();
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/skin.png", exchange -> {
            requests.incrementAndGet();
            started.countDown();
            try {
                release.await(5, TimeUnit.SECONDS);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            }
            exchange.sendResponseHeaders(200, png.length);
            try (OutputStream output = exchange.getResponseBody()) {
                output.write(png);
            }
        });
        server.start();
        try {
            PlayerSkinService service = new PlayerSkinService(temporary, true,
                    Set.of("127.0.0.1"), HttpClient.newHttpClient());
            CompletableFuture<byte[]> first = new CompletableFuture<>();

            service.resolve(player, url(server), first::complete);
            assertTrue(started.await(5, TimeUnit.SECONDS));
            service.resolve(player, url(server), bytes -> { });
            release.countDown();

            assertArrayEquals(png, first.get(5, TimeUnit.SECONDS));
            assertTrue(requests.get() == 1);
        } finally {
            release.countDown();
            server.stop(0);
        }
    }

    @Test
    void configControlsFetchingAndTheHostAllowlist() {
        YamlConfiguration defaults = new YamlConfiguration();
        PlayerSkinService defaultService = PlayerSkinService.fromConfig(temporary, defaults);

        assertTrue(defaultService.fetchEnabled());
        assertTrue(defaultService.allowsHost("textures.minecraft.net"));
        assertFalse(defaultService.allowsHost("127.0.0.1"));

        YamlConfiguration custom = new YamlConfiguration();
        custom.set("emotes.skin-fetch", false);
        custom.set("emotes.skin-hosts", List.of("Example.COM"));
        PlayerSkinService customService = PlayerSkinService.fromConfig(temporary, custom);

        assertFalse(customService.fetchEnabled());
        assertTrue(customService.allowsHost("example.com"));
        assertFalse(customService.allowsHost("textures.minecraft.net"));
    }
}
