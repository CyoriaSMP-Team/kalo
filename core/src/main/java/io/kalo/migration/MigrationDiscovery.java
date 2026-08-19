package io.kalo.migration;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.bukkit.configuration.file.YamlConfiguration;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Finds vendor configuration files without making the server owner know each plugin's
 * directory layout.
 *
 * <p>The old import command required a path copied from a vendor's documentation. That is
 * a poor migration experience: every supported plugin already has a data folder and its
 * YAML files are the thing we need to inspect. This class keeps discovery separate from
 * the command so it can be tested without constructing a Paper command.</p>
 */
public final class MigrationDiscovery {

    private MigrationDiscovery() {
    }

    /** A YAML file that one of Kalo's importers confidently recognises. */
    public record Candidate(@NotNull File file, @NotNull Importer importer) {
    }

    /**
     * Scans a plugin data folder recursively.
     *
     * <p>Unknown YAML is deliberately ignored. A plugin folder contains settings files
     * that are not content, and treating every YAML file as an import candidate would make
     * autocomplete claim support for a plugin before it had found anything useful.</p>
     */
    public static @NotNull List<Candidate> scan(@NotNull File pluginFolder) {
        if (!pluginFolder.isDirectory()) {
            return List.of();
        }

        List<Candidate> candidates = new ArrayList<>();
        for (File file : io.kalo.utils.Files.listFilesRecursively(pluginFolder, ".yml", ".yaml")) {
            String lowerName = file.getName().toLowerCase(Locale.ROOT);
            if (lowerName.endsWith(".kalo.yml") || lowerName.endsWith(".kalo.yaml")) {
                continue;
            }

            YamlConfiguration source = YamlConfiguration.loadConfiguration(file);
            Importer importer = Importers.detect(source);
            if (importer != null) {
                candidates.add(new Candidate(file, importer));
            }
        }

        candidates.sort(Comparator.comparing(candidate -> candidate.file().getAbsolutePath()));
        return List.copyOf(candidates);
    }

    /**
     * Plugin names shown by {@code /kalo import <tab>}.
     *
     * <p>Only plugins with at least one recognised content file are suggested. The check
     * is cheap at command-completion scale and prevents a list full of unrelated plugins.</p>
     */
    public static @NotNull List<String> installedPluginNames() {
        List<String> names = new ArrayList<>();
        for (Plugin plugin : Bukkit.getPluginManager().getPlugins()) {
            if (!scan(plugin.getDataFolder()).isEmpty()) {
                names.add(plugin.getName());
            }
        }
        names.sort(String.CASE_INSENSITIVE_ORDER);
        return List.copyOf(names);
    }

    /** Finds an installed plugin by name without relying on case-sensitive user input. */
    public static Plugin findInstalled(@NotNull String name) {
        for (Plugin plugin : Bukkit.getPluginManager().getPlugins()) {
            if (plugin.getName().equalsIgnoreCase(name)) {
                return plugin;
            }
        }
        return null;
    }
}
