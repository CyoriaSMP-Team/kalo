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
 * Optional ModelEngine hook. No ModelEngine type in this class's signature.
 */
public final class ModelEngineIntegration {

    private static final String PLUGIN_NAME = "ModelEngine";
    private static boolean hooked;

    private ModelEngineIntegration() {
    }

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
        if (hooked) {
            return;
        }
        Plugin plugin = Bukkit.getPluginManager().getPlugin(PLUGIN_NAME);
        if (plugin == null || !plugin.isEnabled()) {
            return;
        }
        try {
            ModelEngineHook.hook();
            hooked = true;
            Plugins.logger().info("Hooked into ModelEngine — Kalo armor models are available for ModelEngine entities");
        } catch (Throwable t) {
            Plugins.logger().log(Level.WARNING, "Could not hook into ModelEngine", t);
        }
    }

    public static synchronized void unregister() {
        if (!hooked) {
            return;
        }
        try {
            ModelEngineHook.unhook();
        } catch (Throwable t) {
            Plugins.logger().log(Level.WARNING, "Could not unhook ModelEngine", t);
        } finally {
            hooked = false;
        }
    }

    public static boolean isHooked() {
        return hooked;
    }
}
