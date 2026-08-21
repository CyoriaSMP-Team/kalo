package io.kalo.pack.host;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Level;

/**
 * Serves the generated resource pack over HTTP.
 *
 * <p>Generating a pack is only half the job: without somewhere to fetch it from, the file
 * sits in the data folder and no player ever sees the content. This is the smallest thing
 * that closes that gap and does not require an external host — the piece Kalo Cloud would
 * later replace with a managed CDN rather than something a server owner must set up.</p>
 *
 * <p>Uses the JDK's own HTTP server. A resource pack download is a single GET of a static
 * file; pulling in a web framework to do that would be a dependency for nothing.</p>
 */
public final class PackHost {
    // Not Plugins.logger(): the host is a plain HTTP server and should stay runnable, and
    // testable, without a live plugin instance.
    private static final java.util.logging.Logger LOGGER =
            java.util.logging.Logger.getLogger(PackHost.class.getName());

    private final File packFile;
    private final int port;
    private final String publicAddress;

    private volatile HttpServer server;
    private ExecutorService executor;
    /** URL, hash and bytes change as one snapshot. */
    private volatile PackVersion version =
            new PackVersion(UUID.randomUUID().toString(), "", null);

    public PackHost(@NotNull File packFile, int port, @NotNull String publicAddress) {
        this.packFile = packFile;
        this.port = port;
        this.publicAddress = publicAddress;
    }

    /** @return whether the host started; a failure here must not stop the plugin */
    public boolean start() {
        try {
            if (port < 1 || port > 65_535) {
                // Port zero asks the OS for an ephemeral port, but url() would still
                // advertise :0 and therefore hand every player an unreachable URL.
                throw new IllegalArgumentException("port must be between 1 and 65535, got " + port);
            }
            if (publicAddress.isBlank()) {
                throw new IllegalArgumentException("public address must not be blank");
            }

            server = HttpServer.create(new InetSocketAddress(port), 0);
            server.createContext("/", this::handle);
            // A virtual-thread executor: downloads are IO-bound and a pack can be tens of
            // megabytes, so a fixed pool would stall joins behind slow clients.
            executor = Executors.newVirtualThreadPerTaskExecutor();
            server.setExecutor(executor);
            server.start();

            refresh();
            LOGGER.info("Serving the resource pack at " + url());
            return true;
        } catch (IOException | RuntimeException e) {
            stop();
            LOGGER.log(Level.WARNING,
                    "Could not start the pack host on port " + port + "; players will not receive the pack", e);
            return false;
        }
    }

    public void stop() {
        HttpServer running = server;
        server = null;
        if (running != null) {
            // Zero delay: the server is shutting down and there is nothing worth waiting
            // for a half-finished pack download to accomplish.
            running.stop(0);
        }
        ExecutorService runningExecutor = executor;
        executor = null;
        if (runningExecutor != null) {
            runningExecutor.shutdownNow();
        }
    }

    /**
     * Recomputes the hash and rotates the URL token after the pack has been regenerated.
     *
     * <p>The token matters because Minecraft caches a pack by URL. Reusing the URL after a
     * content change leaves players on the old pack with no indication anything is
     * wrong.</p>
     */
    public void refresh() {
        byte[] bytes = readPack();
        version = new PackVersion(
                UUID.randomUUID().toString(), bytes == null ? "" : computeSha1(bytes), bytes);
    }

    public @NotNull String url() {
        return url(version.token());
    }

    /** Lowercase hex SHA-1, which is what the client verifies the download against. */
    public @NotNull String sha1() {
        return version.sha1();
    }

    public boolean available() {
        return snapshot() != null;
    }

    /** A coherent URL/hash pair for one resource-pack request. */
    public @Nullable Snapshot snapshot() {
        PackVersion current = version;
        if (server == null || current.bytes() == null || current.sha1().isEmpty()) {
            return null;
        }
        return new Snapshot(url(current.token()), current.sha1());
    }

    private @NotNull String url(@NotNull String token) {
        String address = publicAddress.trim();
        // URI syntax requires brackets around an IPv6 literal.
        if (address.indexOf(':') >= 0 && !(address.startsWith("[") && address.endsWith("]"))) {
            address = "[" + address + "]";
        }
        return "http://" + address + ":" + port + "/" + token + "/pack.zip";
    }

    private void handle(@NotNull HttpExchange exchange) throws IOException {
        try (exchange) {
            if (!"GET".equals(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(405, -1);
                return;
            }
            // The token is not security — it is cache busting — but serving any path would
            // make the host an open file server for anything under it.
            PackVersion current = version;
            if (!exchange.getRequestURI().getPath().equals("/" + current.token() + "/pack.zip")) {
                exchange.sendResponseHeaders(404, -1);
                return;
            }
            byte[] bytes = current.bytes();
            if (bytes == null) {
                exchange.sendResponseHeaders(503, -1);
                return;
            }

            exchange.getResponseHeaders().add("Content-Type", "application/zip");
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(bytes);
            }
        } catch (IOException e) {
            // A client disconnecting mid-download is routine, not a fault worth a trace.
            LOGGER.fine("Pack download ended early: " + e.getMessage());
        }
    }

    private @Nullable byte[] readPack() {
        if (!packFile.isFile()) {
            return null;
        }
        try {
            return Files.readAllBytes(packFile.toPath());
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Could not snapshot the generated pack", e);
            return null;
        }
    }

    private static @NotNull String computeSha1(@NotNull byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-1").digest(bytes));
        } catch (Exception e) {
            // Required by every Java runtime; retaining the guard keeps hosting optional
            // even on a non-conforming VM.
            LOGGER.log(Level.WARNING, "Could not hash the generated pack", e);
            return "";
        }
    }

    public @Nullable HttpServer server() {
        return server;
    }

    public record Snapshot(@NotNull String url, @NotNull String sha1) {
    }

    private record PackVersion(@NotNull String token, @NotNull String sha1, @Nullable byte[] bytes) {
    }
}
