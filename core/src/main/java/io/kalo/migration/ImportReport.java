package io.kalo.migration;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Unmodifiable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * What an import managed, and — more importantly — what it did not.
 *
 * <p>A migration that silently drops what it does not understand is worse than one that
 * refuses: the server owner finds out weeks later when a player asks where an item went.
 * Every source key the importer did not consume is recorded here so it can be shown.</p>
 */
public final class ImportReport {

    private final List<String> imported = new ArrayList<>();
    private final List<String> failed = new ArrayList<>();
    /** Source config keys the importer recognised the name of but cannot express. */
    private final Set<String> unsupported = new LinkedHashSet<>();
    private final List<String> warnings = new ArrayList<>();

    public void imported(@NotNull String key) {
        imported.add(key);
    }

    public void failed(@NotNull String key, @NotNull String reason) {
        failed.add(key + " — " + reason);
    }

    /**
     * @param path config path in the source, e.g. {@code Mechanics.durability}
     */
    public void unsupported(@NotNull String path) {
        unsupported.add(path);
    }

    public void warn(@NotNull String message) {
        warnings.add(message);
    }

    public @NotNull @Unmodifiable List<String> imported() {
        return List.copyOf(imported);
    }

    public @NotNull @Unmodifiable List<String> failed() {
        return List.copyOf(failed);
    }

    public @NotNull @Unmodifiable Set<String> unsupported() {
        return Collections.unmodifiableSet(new LinkedHashSet<>(unsupported));
    }

    public @NotNull @Unmodifiable List<String> warnings() {
        return List.copyOf(warnings);
    }

    public boolean hasProblems() {
        return !failed.isEmpty() || !unsupported.isEmpty();
    }

    /** A human summary, one line per fact, for printing to a console or a command sender. */
    public @NotNull List<String> lines() {
        List<String> lines = new ArrayList<>();
        lines.add("Imported " + imported.size() + " content definition(s)");

        if (!failed.isEmpty()) {
            lines.add("Failed (" + failed.size() + "):");
            failed.forEach(entry -> lines.add("  " + entry));
        }
        if (!unsupported.isEmpty()) {
            lines.add("Not carried over — these need doing by hand (" + unsupported.size() + "):");
            unsupported.forEach(path -> lines.add("  " + path));
        }
        warnings.forEach(warning -> lines.add("Note: " + warning));

        return lines;
    }
}
