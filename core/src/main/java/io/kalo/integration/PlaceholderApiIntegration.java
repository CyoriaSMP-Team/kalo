package io.kalo.integration;

import io.kalo.utils.Plugins;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.server.PluginDisableEvent;
import org.bukkit.event.server.PluginEnableEvent;
import org.bukkit.event.server.ServerLoadEvent;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;

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

    private static final String PLUGIN_NAME = "PlaceholderAPI";
    // Deliberately Object: resolving PlaceholderApiHook's superclass before the guard is
    // exactly what this class exists to avoid.
    private static Object installedExpansion;

    private PlaceholderApiIntegration() {
    }

    /**
     * Installs now when possible and also at ServerLoad/plugin-enable time.
     *
     * <p>Kalo has no hard dependency on PlaceholderAPI, so enable order is not guaranteed.
     * The previous one-shot call from Kalo's onEnable could run before PlaceholderAPI was
     * enabled and then never try again.</p>
     */
    public static void initialize(@NotNull Plugin owner) {
        Bukkit.getPluginManager().registerEvents(new Listener() {
            @EventHandler
            public void onServerLoad(ServerLoadEvent event) {
                registerIfPresent();
            }

            @EventHandler
            public void onPluginEnable(PluginEnableEvent event) {
                if (PLUGIN_NAME.equals(event.getPlugin().getName())) {
                    registerIfPresent();
                }
            }

            @EventHandler
            public void onPluginDisable(PluginDisableEvent event) {
                if (PLUGIN_NAME.equals(event.getPlugin().getName())) {
                    unregister();
                }
            }
        }, owner);
        registerIfPresent();
    }

    public static synchronized void registerIfPresent() {
        if (installedExpansion != null) {
            return;
        }
        Plugin placeholderApi = Bukkit.getPluginManager().getPlugin(PLUGIN_NAME);
        if (placeholderApi == null || !placeholderApi.isEnabled()) {
            return;
        }
        try {
            // First reference to PlaceholderApiHook, and therefore the first time its
            // supertype has to resolve — inside the guard, and inside the catch.
            installedExpansion = PlaceholderApiHook.install();
            if (installedExpansion != null) {
                Plugins.logger().info("Registered PlaceholderAPI expansion");
            } else {
                Plugins.logger().warning("PlaceholderAPI refused to register the Kalo expansion");
            }
        } catch (Throwable t) {
            // Throwable, not Exception: a version mismatch surfaces as LinkageError, and
            // an optional integration must never stop the plugin from loading.
            Plugins.logger().log(Level.WARNING, "Could not register the PlaceholderAPI expansion", t);
        }
    }

    /** Removes the persistent expansion before Kalo's singleton and registries disappear. */
    public static synchronized void unregister() {
        Object expansion = installedExpansion;
        installedExpansion = null;
        if (expansion == null) {
            return;
        }
        try {
            PlaceholderApiHook.uninstall(expansion);
        } catch (Throwable t) {
            Plugins.logger().log(Level.WARNING, "Could not unregister the PlaceholderAPI expansion", t);
        }
    }
}
