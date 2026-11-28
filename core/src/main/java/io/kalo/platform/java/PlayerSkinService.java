package io.kalo.platform.java;

import org.bukkit.configuration.ConfigurationSection;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * Resolves emote skins for a player, preferring the disk cache and falling back to the
 * player's signed skin URL.
 *
 * <p>Network work is asynchronous and at most one fetch per player is in flight. The
 * callback runs on the thread that completed the lookup: the caller's thread for a cache
 * hit, or the HTTP client's thread after a download. A callback that only mutates the
 * thread-safe Bedrock overlay map is therefore safe without another scheduler hop.</p>
 */
public final class PlayerSkinService {

    public static final Set<String> DEFAULT_HOSTS =
            Set.of("textures.minecraft.net", "textures.mojang.com");
    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(8);

    private final Path dataFolder;
    private final boolean fetchEnabled;
    private final Set<String> allowedHosts;
    private final HttpClient client;
    private final Duration timeout;
    private final Set<UUID> inFlight = ConcurrentHashMap.newKeySet();

    public PlayerSkinService(@NotNull Path dataFolder, boolean fetchEnabled,
                             @NotNull Set<String> allowedHosts,
                             @NotNull HttpClient client) {
        this(dataFolder, fetchEnabled, allowedHosts, client, DEFAULT_TIMEOUT);
    }

    public PlayerSkinService(@NotNull Path dataFolder, boolean fetchEnabled,
                             @NotNull Set<String> allowedHosts,
                             @NotNull HttpClient client, @NotNull Duration timeout) {
        this.dataFolder = dataFolder;
        this.fetchEnabled = fetchEnabled;
        this.allowedHosts = allowedHosts.stream()
                .filter(Objects::nonNull)
                .map(host -> host.trim().toLowerCase(Locale.ROOT))
                .filter(host -> !host.isEmpty())
                .collect(Collectors.toUnmodifiableSet());
        this.client = client;
        this.timeout = timeout;
    }

    public static @NotNull PlayerSkinService fromConfig(
            @NotNull Path dataFolder, @Nullable ConfigurationSection config) {
        boolean enabled = config == null
                || config.getBoolean("emotes.skin-fetch", true);
        List<String> hosts = config == null ? List.of()
                : config.getStringList("emotes.skin-hosts");
        Set<String> allowed = hosts.isEmpty() ? DEFAULT_HOSTS : Set.copyOf(hosts);
        int seconds = config == null ? (int) DEFAULT_TIMEOUT.toSeconds()
                : config.getInt("emotes.skin-timeout-seconds",
                (int) DEFAULT_TIMEOUT.toSeconds());
        Duration timeout = Duration.ofSeconds(Math.max(1, Math.min(30, seconds)));
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(timeout)
                // Redirects are not followed: an allowlisted host must not be able to
                // bounce the fetch to an arbitrary address.
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
        return new PlayerSkinService(dataFolder, enabled, allowed, client, timeout);
    }

    public boolean fetchEnabled() {
        return fetchEnabled;
    }

    public boolean allowsHost(@Nullable String host) {
        return host != null && allowedHosts.contains(host.toLowerCase(Locale.ROOT));
    }

    /** Cached bytes for this player, or null when no valid PNG is on disk. */
    public @Nullable byte[] cached(@NotNull UUID playerId) {
        return PlayerSkinCache.resolve(dataFolder, playerId);
    }

    /**
     * Resolves a skin and invokes {@code onResolved} exactly once when one is available.
     *
     * <p>A cache hit calls back immediately. Otherwise, when fetching is enabled and the
     * profile URL is present, the download runs in the background; failures are silent so
     * the model keeps its baked skin.</p>
     */
    public void resolve(@NotNull UUID playerId, @Nullable String textureUrl,
                        @NotNull Consumer<byte[]> onResolved) {
        byte[] cached = PlayerSkinCache.resolve(dataFolder, playerId, textureUrl);
        if (cached != null) {
            onResolved.accept(cached);
            return;
        }
        if (!fetchEnabled || textureUrl == null || textureUrl.isBlank()) {
            return;
        }
        URI uri = PlayerSkinCache.safeUri(textureUrl, allowedHosts);
        if (uri == null || !inFlight.add(playerId)) {
            return;
        }
        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(timeout)
                .header("User-Agent", "Kalo")
                .GET()
                .build();
        try {
            client.sendAsync(request, HttpResponse.BodyHandlers.ofInputStream())
                    .thenApply(PlayerSkinCache::read)
                    .whenComplete((bytes, failure) -> {
                        byte[] resolved = null;
                        try {
                            if (failure == null && bytes != null
                                    && PlayerSkinCache.store(dataFolder, playerId,
                                    textureUrl, bytes)) {
                                resolved = bytes;
                            }
                        } catch (RuntimeException storeFailed) {
                            // A failed write is a silent fallback to the baked skin.
                        } finally {
                            // Remove before the callback: a listener that immediately
                            // resolves the same player for a changed URL must be able to
                            // start the next fetch instead of being dropped as in-flight.
                            inFlight.remove(playerId);
                        }
                        if (resolved != null) {
                            try {
                                onResolved.accept(resolved);
                            } catch (RuntimeException callbackFailed) {
                                // A consumer must not break the fetch pipeline.
                            }
                        }
                    });
        } catch (RuntimeException rejected) {
            inFlight.remove(playerId);
        }
    }
}
