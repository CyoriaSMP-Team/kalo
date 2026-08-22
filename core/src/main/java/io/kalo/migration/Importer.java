package io.kalo.migration;

import org.bukkit.configuration.file.YamlConfiguration;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.List;

/**
 * Converts one other plugin's config format into Kalo pack YAML.
 *
 * <p>Every vendor is a peer here rather than a special case in a chain of {@code if}s, so
 * adding the next one is writing a class instead of editing a command. An add-on can
 * register its own the same way Kalo registers these.</p>
 *
 * <p>The contract that matters is not conversion — it is honesty. An importer must record
 * everything it cannot express in the {@link ImportReport} rather than dropping it, since
 * content that quietly stops working is discovered from players weeks later.</p>
 */
public interface Importer {

    /** Display name, used in messages: "Detected {@code name()} format". */
    @NotNull String name();

    /**
     * How strongly this file looks like the vendor's format.
     *
     * <p>Scored rather than boolean because these formats overlap — several are YAML maps
     * of content keys, and Nexo is a fork of Oraxen. The highest score wins, so an
     * importer should return a high score only for something distinctive to its vendor.</p>
     *
     * @return 0 for "not mine", higher for more confident
     */
    int detect(@NotNull YamlConfiguration source);

    /**
     * @param namespace the Kalo pack the content will live under, used when the source
     *                  format has no namespace of its own
     * @return Kalo pack YAML, ready to write into {@code configs/}
     */
    @NotNull String convert(@NotNull YamlConfiguration source,
                            @NotNull String namespace,
                            @NotNull ImportReport report);

    /**
     * Returns the relative directories within the source plugin's data folder that
     * contain assets (textures, models) referenced by the converted content.
     *
     * <p>These directories are copied into the Kalo pack's {@code assets/} folder
     * during import so the content works immediately without manual file copying.</p>
     *
     * @param pluginFolder the source plugin's data folder
     * @return list of relative directory paths to copy, or empty if no assets
     */
    default @NotNull List<File> assetDirectories(@NotNull File pluginFolder) {
        return List.of();
    }
}
