package io.kalo.platform.java;

import io.kalo.integration.GeyserIntegration;
import io.kalo.platform.bedrock.SessionContentPolicy;
import net.kyori.adventure.key.Key;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Server-side gameplay entitlement gate for content that is also filtered from packs.
 *
 * <p>The same {@link SessionContentPolicy} used at staging decides whether a player may be
 * given, pick up, use or place a content item. A player UUID is used as the session key so
 * Java and Bedrock players share one policy.</p>
 */
public final class ContentEntitlements {

    private static final long CACHE_NANOS = java.util.concurrent.TimeUnit.SECONDS.toNanos(2);
    private static volatile SessionContentPolicy cachedPolicy;
    private static volatile long cachedAtNanos;

    private ContentEntitlements() {
    }

    public static boolean allows(@NotNull Player player, @NotNull Key contentKey) {
        String sessionKey = GeyserIntegration.support().xuid(player.getUniqueId());
        if (sessionKey == null || sessionKey.isBlank()) {
            sessionKey = player.getUniqueId().toString();
        }
        return policy().allows(sessionKey, contentKey.asString(), player::hasPermission);
    }

    /** Item pickup/interact/craft events are hot; a short cache avoids parsing YAML per event. */
    private static SessionContentPolicy policy() {
        long now = System.nanoTime();
        SessionContentPolicy policy = cachedPolicy;
        if (policy == null || now - cachedAtNanos > CACHE_NANOS) {
            policy = SessionContentPolicy.fromPluginConfig();
            cachedPolicy = policy;
            cachedAtNanos = now;
        }
        return policy;
    }

    public static boolean allows(@NotNull Player player, @Nullable String contentKey) {
        if (contentKey == null || contentKey.isBlank()) {
            return true;
        }
        try {
            return allows(player, Key.key(contentKey));
        } catch (RuntimeException malformed) {
            return false;
        }
    }
}
