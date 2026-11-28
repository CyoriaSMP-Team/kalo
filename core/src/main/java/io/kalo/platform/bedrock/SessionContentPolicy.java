package io.kalo.platform.bedrock;

import org.bukkit.configuration.ConfigurationSection;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * Decides which content a session may receive and use.
 *
 * <p>A session may be restricted to whole content namespaces or individual keys. In
 * addition, a content key can require a permission node; that node is checked for pack
 * staging and by the Java gameplay gates. An unlisted session with no permission rule
 * receives everything, so enabling the feature without a rule changes nothing. Shared pack
 * files with no content owner — manifests, atlases and base translations — are always
 * included.</p>
 */
public final class SessionContentPolicy {

    private static final SessionContentPolicy ALL =
            new SessionContentPolicy(Map.of(), Map.of());

    private final Map<String, Set<String>> sessions;
    private final Map<String, String> permissions;

    private SessionContentPolicy(@NotNull Map<String, Set<String>> sessions,
                                 @NotNull Map<String, String> permissions) {
        this.sessions = Map.copyOf(sessions);
        this.permissions = Map.copyOf(permissions);
    }

    public static @NotNull SessionContentPolicy all() {
        return ALL;
    }

    public static @NotNull SessionContentPolicy of(
            @NotNull Map<String, ? extends Collection<String>> sessions) {
        return of(sessions, Map.of());
    }

    public static @NotNull SessionContentPolicy of(
            @NotNull Map<String, ? extends Collection<String>> sessions,
            @NotNull Map<String, String> permissions) {
        Map<String, Set<String>> normalized = new TreeMap<>();
        sessions.forEach((xuid, allowed) -> {
            if (xuid == null || allowed == null) {
                return;
            }
            Set<String> values = allowed.stream()
                    .filter(value -> value != null && !value.isBlank())
                    .map(value -> value.trim().toLowerCase(Locale.ROOT))
                    .collect(Collectors.toUnmodifiableSet());
            normalized.put(xuid.trim(), values);
        });
        Map<String, String> nodes = new TreeMap<>();
        permissions.forEach((key, node) -> {
            if (key != null && node != null && !node.isBlank()) {
                nodes.put(key.trim().toLowerCase(Locale.ROOT), node.trim());
            }
        });
        if (normalized.isEmpty() && nodes.isEmpty()) {
            return ALL;
        }
        return new SessionContentPolicy(normalized, nodes);
    }

    public static @NotNull SessionContentPolicy fromConfig(
            @Nullable ConfigurationSection config) {
        if (config == null || !config.getBoolean("enabled", false)) {
            return ALL;
        }
        Map<String, Collection<String>> values = new TreeMap<>();
        ConfigurationSection sessions = config.getConfigurationSection("sessions");
        if (sessions != null) {
            for (String xuid : sessions.getKeys(false)) {
                values.put(xuid, sessions.getStringList(xuid));
            }
        }
        Map<String, String> permissions = new TreeMap<>();
        ConfigurationSection nodes = config.getConfigurationSection("permissions");
        if (nodes != null) {
            for (String key : nodes.getKeys(false)) {
                String node = nodes.getString(key);
                if (node != null && !node.isBlank()) {
                    permissions.put(key, node);
                }
            }
        }
        return of(values, permissions);
    }

    /** Reads the live plugin config; a missing or broken section means no filtering. */
    public static @NotNull SessionContentPolicy fromPluginConfig() {
        try {
            return fromConfig(io.kalo.utils.Plugins.plugin().getConfig()
                    .getConfigurationSection("bedrock-packs.session-content"));
        } catch (Throwable unavailable) {
            return ALL;
        }
    }

    /** Backward-compatible check that assumes every permission node is granted. */
    public boolean allows(@Nullable String xuid, @Nullable String contentKey) {
        return allows(xuid, contentKey, node -> true);
    }

    /**
     * @param contentKey    the owning content key, or null for a shared pack file
     * @param hasPermission the player's permission predicate; restricted content fails
     *                      closed when a node is required
     */
    public boolean allows(@Nullable String xuid, @Nullable String contentKey,
                          @NotNull Predicate<String> hasPermission) {
        if (contentKey == null) {
            return true;
        }
        String key = contentKey.toLowerCase(Locale.ROOT);
        if (xuid != null) {
            Set<String> allowed = sessions.get(xuid.trim());
            if (allowed != null && !allowed.contains("*") && !allowsKey(allowed, key)) {
                return false;
            }
        }
        String node = permissionNode(key);
        return node == null || hasPermission.test(node);
    }

    private static boolean allowsKey(Set<String> allowed, String key) {
        if (allowed.contains(key)) {
            return true;
        }
        int separator = key.indexOf(':');
        return separator > 0 && allowed.contains(key.substring(0, separator));
    }

    private @Nullable String permissionNode(String contentKey) {
        String exact = permissions.get(contentKey);
        if (exact != null) {
            return exact;
        }
        int separator = contentKey.indexOf(':');
        if (separator > 0) {
            return permissions.get(contentKey.substring(0, separator) + ":*");
        }
        return null;
    }

    /** Stable digest component; "all" keeps the older session-digest format unchanged. */
    public @NotNull String fingerprint(@Nullable String xuid) {
        return fingerprint(xuid, node -> true);
    }

    /**
     * Includes the permission outcome for every configured node, so a session whose
     * entitlements differ cannot reuse another session's staged pack.
     */
    public @NotNull String fingerprint(@Nullable String xuid,
                                       @NotNull Predicate<String> hasPermission) {
        String base = allowlistFingerprint(xuid);
        if (permissions.isEmpty()) {
            return base;
        }
        StringBuilder fingerprint = new StringBuilder(base);
        for (Map.Entry<String, String> entry : permissions.entrySet()) {
            fingerprint.append('|').append(entry.getKey()).append('=')
                    .append(hasPermission.test(entry.getValue()) ? '1' : '0');
        }
        return fingerprint.toString();
    }

    private @NotNull String allowlistFingerprint(@Nullable String xuid) {
        if (sessions.isEmpty() || xuid == null) {
            return "all";
        }
        Set<String> allowed = sessions.get(xuid.trim());
        if (allowed == null || allowed.contains("*")) {
            return "all";
        }
        if (allowed.isEmpty()) {
            return "none";
        }
        return allowed.stream().sorted()
                .collect(Collectors.joining(",", "allow:", ""));
    }
}
