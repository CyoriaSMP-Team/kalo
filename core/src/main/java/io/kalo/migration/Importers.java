package io.kalo.migration;

import org.bukkit.configuration.file.YamlConfiguration;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.util.ArrayList;
import java.util.List;

/**
 * The registry of format importers.
 *
 * <p>Detection is scored rather than first-match: these formats overlap heavily — Nexo is
 * a fork of Oraxen, and several are plain YAML maps of content keys — so picking the first
 * one that says "maybe" would misread files routinely. The most confident importer wins.</p>
 */
public final class Importers {

    private static final List<Importer> IMPORTERS = new ArrayList<>(List.of(
            new NexoImporter(),
            new OraxenFormatImporter(),
            new ItemsAdderFormatImporter(),
            new NekoImporter(),
            new CraftEngineImporter()
    ));

    private Importers() {
    }

    /** Lets an add-on support a format Kalo does not ship with. */
    public static synchronized void register(@NotNull Importer importer) {
        IMPORTERS.add(importer);
    }

    public static synchronized @NotNull @Unmodifiable List<Importer> all() {
        return List.copyOf(IMPORTERS);
    }

    /**
     * Picks the importer most confident about this file.
     *
     * @return the best match, or {@code null} when nothing recognises the file — better
     *         than guessing and producing plausible-looking nonsense
     */
    public static synchronized @Nullable Importer detect(@NotNull YamlConfiguration source) {
        Importer best = null;
        int bestScore = 0;

        for (Importer importer : IMPORTERS) {
            int score = importer.detect(source);
            if (score > bestScore) {
                bestScore = score;
                best = importer;
            }
        }
        return best;
    }
}
