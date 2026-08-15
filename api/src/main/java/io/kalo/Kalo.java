package io.kalo;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

public final class Kalo {
    private static KaloPlugin plugin;

    private Kalo() {
    }

    public static @NotNull KaloPlugin plugin() {
        return Objects.requireNonNull(plugin, "Plugin not initialized");
    }

    @ApiStatus.Internal
    static void registerPlugin(KaloPlugin plugin) {
        if (Kalo.plugin != null)
            throw new UnsupportedOperationException("Cannot redefine singleton plugin");
        Kalo.plugin = plugin;
    }

    @ApiStatus.Internal
    static void unregisterPlugin() {
        Kalo.plugin = null;
    }
}
