package io.kalo.utils;

import org.jetbrains.annotations.NotNull;

import java.io.File;

public final class Constants {
    public static final String PLUGIN_ID = "kalo";
    public static final String PLUGIN_NAME = "Kalo";

    private Constants() {
    }

    /**
     * The plugin's data folder.
     *
     * <p>Resolved from the running plugin rather than hardcoded. The previous
     * {@code new File("plugins", "neko")} was both relative to the process working
     * directory and lowercase, while the server creates a folder named after the plugin —
     * two different directories on any case-sensitive filesystem.</p>
     */
    public static @NotNull File dataFolder() {
        return Plugins.plugin().getDataFolder();
    }
}
