package io.kalo.integration;

import io.kalo.utils.Plugins;
import org.bukkit.Bukkit;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.logging.Level;

/**
 * Classloader boundary for the optional Bedrock Forms bridge.
 *
 * <p>{@code DeluxeMenuFormBridge} names Cumulus and Geyser types directly. Loading that
 * class on a Java-only server, or on a proxy setup where Geyser is not a backend plugin,
 * must not be able to fail Kalo startup. This guard therefore tests the installed plugins
 * first and reaches the bridge reflectively; absence is a logged no-op, not a
 * {@code NoClassDefFoundError} during class initialization.</p>
 */
public final class BedrockFormsIntegration {

    private static final String FLOODGATE = "floodgate";
    private static final String GEYSER = "Geyser-Spigot";

    private BedrockFormsIntegration() {
    }

    /**
     * @return whether the forms listener was registered
     */
    public static boolean initialize(@NotNull JavaPlugin plugin) {
        if (!plugin.getConfig().getBoolean("bedrock-forms.enabled", false)) {
            return false;
        }
        Plugin floodgate = Bukkit.getPluginManager().getPlugin(FLOODGATE);
        Plugin geyser = Bukkit.getPluginManager().getPlugin(GEYSER);
        String provider = provider(floodgate != null && floodgate.isEnabled(),
                geyser != null && geyser.isEnabled());
        if (provider == null) {
            // Java-only and proxy-topology servers reach this branch. It is not an error:
            // Kalo's Java menus remain fully functional.
            Plugins.logger().info("Bedrock Forms enabled, but neither Floodgate nor"
                    + " Geyser-Spigot shares this server; Java menus remain active");
            return false;
        }
        try {
            Class<?> bridge = Class.forName("io.kalo.converter.DeluxeMenuFormBridge",
                    true, plugin.getClass().getClassLoader());
            Object listener = bridge.getMethod("create", JavaPlugin.class)
                    .invoke(null, plugin);
            if (!(listener instanceof Listener eventListener)) {
                throw new IllegalStateException("form bridge is not a Bukkit listener");
            }
            Bukkit.getPluginManager().registerEvents(eventListener, plugin);
            return true;
        } catch (Throwable incompatible) {
            // Throwable on purpose: optional Floodgate/Cumulus/Geyser APIs may be absent
            // or from a different major version. Forms must never take the server down.
            Plugins.logger().log(Level.WARNING, "Bedrock Forms unavailable via "
                    + provider + "; original Java menus remain active", incompatible);
            return false;
        }
    }

    /** Pure provider selection, package-visible for the no-Floodgate/no-Geyser tests. */
    static @Nullable String provider(boolean floodgate, boolean geyser) {
        if (floodgate) {
            return FLOODGATE;
        }
        return geyser ? GEYSER : null;
    }
}
