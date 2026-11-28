package io.kalo.pack.host;

import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.HexFormat;

/**
 * Pushes a completed immutable pack directly from Kalo to the private Relay.
 * Never routes end-user downloads through Minecraft, SSH, timers or a watcher.
 * Transport must use a private encrypted network (WireGuard/Tailscale), or HTTPS.
 */
public final class RelayPackPublisher {
    private final URI endpoint;
    private final Path tokenFile;
    private final HttpClient client;

    public RelayPackPublisher(@NotNull String uploadUrl, @NotNull Path tokenFile) {
        this.endpoint = URI.create(uploadUrl.replaceAll("/+$", ""));
        if (!(endpoint.getScheme().equalsIgnoreCase("http")
                || endpoint.getScheme().equalsIgnoreCase("https"))
                || endpoint.getHost() == null
                || endpoint.getUserInfo() != null
                || endpoint.getQuery() != null
                || endpoint.getFragment() != null) {
            throw new IllegalArgumentException("relay-upload-url must be an HTTP(S) URL");
        }
        if (!endpoint.getPath().endsWith("/v1/packs")) {
            throw new IllegalArgumentException("relay-upload-url must end in /v1/packs");
        }
        this.tokenFile = tokenFile;
        this.client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
    }

    public void upload(@NotNull String versionId, byte @NotNull [] bytes)
            throws IOException, InterruptedException {
        String token = Files.readString(tokenFile, StandardCharsets.UTF_8).trim();
        if (token.isEmpty()) {
            throw new IOException("Relay credential file is empty");
        }
        String sha256;
        try {
            sha256 = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (java.security.NoSuchAlgorithmException impossible) {
            throw new IOException("SHA-256 is missing", impossible);
        }
        HttpRequest request = HttpRequest.newBuilder(
                        URI.create(endpoint + "/" + versionId))
                .timeout(Duration.ofSeconds(45))
                .header("Authorization", "Bearer " + token)
                .header("X-Pack-SHA256", sha256)
                .header("Content-Type", "application/zip")
                .PUT(HttpRequest.BodyPublishers.ofByteArray(bytes))
                .build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        if ((response.statusCode() != 201 && response.statusCode() != 200)
                || !sha256.equals(response.body().trim())) {
            throw new IOException("Relay rejected pack: HTTP " + response.statusCode());
        }
    }
}
