package io.kalo.integration;

import io.kalo.utils.Plugins;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;

import java.util.logging.Level;

/**
 * Decides whether Geyser is reachable from this process.
 *
 * <p>Names no Geyser type of its own, for the same reason {@link PlaceholderApiIntegration}
 * does not: a class cannot load without resolving its supertypes and signatures, so a
 * guard living on the class that touches Geyser would throw before it could run.</p>
 */
public final class GeyserIntegration {

    /**
     * The official Bukkit/Paper bootstrap name. This must stay aligned with the optional
     * join-classpath dependency in paper-plugin.yml: detecting an unrelated/fork name
     * without that classloader edge only leads to a guaranteed LinkageError.
     */
    private static final String GEYSER_PLUGIN = "Geyser-Spigot";
    // Object keeps this guard class free of Geyser types in its fields and signatures.
    private static Object registration;

    private GeyserIntegration() {
    }

    /**
     * Hooks Geyser directly when it shares this JVM.
     *
     * @return whether the native path is active; when false the standalone extension is
     *         the way Bedrock content gets registered
     */
    public static synchronized boolean registerIfPresent(@NotNull Plugin plugin) {
        if (registration != null) {
            return true;
        }
        if (!geyserPresent()) {
            return false;
        }
        try {
            registration = GeyserBridge.register(plugin);
            return true;
        } catch (Throwable t) {
            // Throwable: a Geyser version whose API has moved surfaces as LinkageError,
            // and Bedrock support failing must never stop the server from starting.
            Plugins.logger().log(Level.WARNING,
                    "Geyser is installed but could not be hooked; use the standalone extension instead", t);
            return false;
        }
    }

    /** Releases Geyser's owned subscriptions when Kalo is disabled. */
    public static synchronized void unregister() {
        Object registered = registration;
        registration = null;
        if (registered == null) {
            return;
        }
        try {
            GeyserBridge.unregister(registered);
        } catch (Throwable t) {
            Plugins.logger().log(Level.WARNING, "Could not unregister Kalo from Geyser", t);
        }
    }

    /**
     * Whether Geyser shares this JVM.
     *
     * <p>Public because the answer decides more than the hook: with no Geyser here there
     * may be nothing to build a Bedrock pack for.</p>
     */
    public static boolean present() {
        return geyserPresent();
    }

    private static boolean geyserPresent() {
        Plugin plugin = Bukkit.getPluginManager().getPlugin(GEYSER_PLUGIN);
        return plugin != null && plugin.isEnabled();
    }
}
