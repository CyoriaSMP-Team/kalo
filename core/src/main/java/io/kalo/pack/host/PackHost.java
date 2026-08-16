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

    private HttpServer server;
    /** Part of the URL so a changed pack cannot be served from a client's cache. */
    private volatile String token = UUID.randomUUID().toString();
    private volatile String sha1 = "";

    public PackHost(@NotNull File packFile, int port, @NotNull String publicAddress) {
        this.packFile = packFile;
        this.port = port;
        this.publicAddress = publicAddress;
    }

    /** @return whether the host started; a failure here must not stop the plugin */
    public boolean start() {
        try {
            server = HttpServer.create(new InetSocketAddress(port), 0);
            server.createContext("/", this::handle);
            // A virtual-thread executor: downloads are IO-bound and a pack can be tens of
            // megabytes, so a fixed pool would stall joins behind slow clients.
            server.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
            server.start();

            refresh();
            LOGGER.info("Serving the resource pack at " + url());
            return true;
        } catch (IOException e) {
            LOGGER.log(Level.WARNING,
                    "Could not start the pack host on port " + port + "; players will not receive the pack", e);
            return false;
        }
    }

    public void stop() {
        if (server != null) {
            // Zero delay: the server is shutting down and there is nothing worth waiting
            // for a half-finished pack download to accomplish.
            server.stop(0);
            server = null;
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
        token = UUID.randomUUID().toString();
        sha1 = computeSha1();
    }

    public @NotNull String url() {
        return "http://" + publicAddress + ":" + port + "/" + token + "/pack.zip";
    }

    /** Lowercase hex SHA-1, which is what the client verifies the download against. */
    public @NotNull String sha1() {
        return sha1;
    }

    public boolean available() {
        return server != null && packFile.isFile() && !sha1.isEmpty();
    }

    private void handle(@NotNull HttpExchange exchange) throws IOException {
        try (exchange) {
            if (!"GET".equals(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(405, -1);
                return;
            }
            // The token is not security — it is cache busting — but serving any path would
            // make the host an open file server for anything under it.
            if (!exchange.getRequestURI().getPath().equals("/" + token + "/pack.zip")) {
                exchange.sendResponseHeaders(404, -1);
                return;
            }
            if (!packFile.isFile()) {
                exchange.sendResponseHeaders(503, -1);
                return;
            }

            exchange.getResponseHeaders().add("Content-Type", "application/zip");
            exchange.sendResponseHeaders(200, packFile.length());
            try (OutputStream out = exchange.getResponseBody()) {
                Files.copy(packFile.toPath(), out);
            }
        } catch (IOException e) {
            // A client disconnecting mid-download is routine, not a fault worth a trace.
            LOGGER.fine("Pack download ended early: " + e.getMessage());
        }
    }

    private @NotNull String computeSha1() {
        if (!packFile.isFile()) {
            return "";
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-1");
            try (var input = Files.newInputStream(packFile.toPath())) {
                byte[] buffer = new byte[8192];
                int read;
                while ((read = input.read(buffer)) > 0) {
                    digest.update(buffer, 0, read);
                }
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (Exception e) {
            // Without a hash the client re-downloads every join, so this is worth saying.
            LOGGER.log(Level.WARNING, "Could not hash the generated pack", e);
            return "";
        }
    }

    public @Nullable HttpServer server() {
        return server;
    }
}
