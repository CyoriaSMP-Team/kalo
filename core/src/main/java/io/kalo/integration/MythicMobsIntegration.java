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
 * Optional MythicMobs hook. No MythicMobs type appears in this class's signature
 * so Kalo can load on servers without it.
 */
public final class MythicMobsIntegration {

    private static final String PLUGIN_NAME = "MythicMobs";
    private static boolean hooked;

    private MythicMobsIntegration() {
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
            MythicMobsHook.hook();
            hooked = true;
            Plugins.logger().info("Hooked into MythicMobs — Kalo items are available as MythicMobs drops (kalo:<key>)");
        } catch (Throwable t) {
            Plugins.logger().log(Level.WARNING, "Could not hook into MythicMobs", t);
        }
    }

    public static synchronized void unregister() {
        if (!hooked) {
            return;
        }
        try {
            MythicMobsHook.unhook();
        } catch (Throwable t) {
            Plugins.logger().log(Level.WARNING, "Could not unhook MythicMobs", t);
        } finally {
            hooked = false;
        }
    }

    public static boolean isHooked() {
        return hooked;
    }
}
