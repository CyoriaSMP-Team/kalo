package io.kalo.integration;

import io.kalo.utils.Plugins;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.logging.Level;

/**
 * Decides whether Geyser is reachable from this process.
 *
 * <p>Names no Geyser type of its own, for the same reason {@link PlaceholderApiIntegration}
 * does not: a class cannot load without resolving its supertypes and signatures, so a
 * guard living on the class that touches Geyser would throw before it could run.</p>
 */
public final class GeyserIntegration {

    /** Geyser's plugin name differs by platform; a server runs at most one of these. */
    private static final List<String> GEYSER_PLUGINS = List.of("Geyser-Spigot", "Geyser-Paper", "Geyser");

    private GeyserIntegration() {
    }

    /**
     * Hooks Geyser directly when it shares this JVM.
     *
     * @return whether the native path is active; when false the standalone extension is
     *         the way Bedrock content gets registered
     */
    public static boolean registerIfPresent(@NotNull Plugin plugin) {
        if (!geyserPresent()) {
            return false;
        }
        try {
            GeyserBridge.register(plugin);
            return true;
        } catch (Throwable t) {
            // Throwable: a Geyser version whose API has moved surfaces as LinkageError,
            // and Bedrock support failing must never stop the server from starting.
            Plugins.logger().log(Level.WARNING,
                    "Geyser is installed but could not be hooked; use the standalone extension instead", t);
            return false;
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
        for (String name : GEYSER_PLUGINS) {
            Plugin plugin = Bukkit.getPluginManager().getPlugin(name);
            if (plugin != null && plugin.isEnabled()) {
                return true;
            }
        }
        return false;
    }
}
