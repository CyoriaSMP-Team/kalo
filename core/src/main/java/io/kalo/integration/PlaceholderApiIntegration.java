package io.kalo.integration;

import io.kalo.utils.Plugins;
import org.bukkit.Bukkit;

import java.util.logging.Level;

/**
 * Decides whether to wire up the PlaceholderAPI expansion.
 *
 * <p><b>This class must not extend or otherwise name any PlaceholderAPI type in its own
 * signature.</b> A class cannot be loaded without resolving its supertypes, so a guard
 * method living on the expansion class itself is unreachable when PlaceholderAPI is
 * absent — the JVM throws {@code NoClassDefFoundError} at the call site before the guard
 * can run. That is exactly what happened here, and it printed a stack trace on every
 * server that did not have PlaceholderAPI installed, which is most of them.</p>
 *
 * <p>Keeping the check on a class with no PlaceholderAPI supertype means
 * {@link PlaceholderApiHook} is not loaded until the branch that actually needs it.</p>
 */
public final class PlaceholderApiIntegration {

    private PlaceholderApiIntegration() {
    }

    public static void registerIfPresent() {
        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") == null) {
            return;
        }
        try {
            // First reference to PlaceholderApiHook, and therefore the first time its
            // supertype has to resolve — inside the guard, and inside the catch.
            PlaceholderApiHook.install();
            Plugins.logger().info("Registered PlaceholderAPI expansion");
        } catch (Throwable t) {
            // Throwable, not Exception: a version mismatch surfaces as LinkageError, and
            // an optional integration must never stop the plugin from loading.
            Plugins.logger().log(Level.WARNING, "Could not register the PlaceholderAPI expansion", t);
        }
    }
}
