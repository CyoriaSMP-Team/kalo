package io.kalo.utils;

import io.kalo.Kalo;
import io.kalo.KaloPlugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

import java.util.logging.Logger;

public final class Plugins {

    private Plugins() {
    }

    public static @NotNull JavaPlugin plugin(@NotNull KaloPlugin kaloPlugin) {
        return (JavaPlugin) kaloPlugin;
    }

    public static @NotNull JavaPlugin plugin() {
        return plugin(Kalo.plugin());
    }

    public static @NotNull Logger logger() {
        return plugin().getLogger();
    }
}
