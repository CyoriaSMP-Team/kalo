package io.kalo.model;

import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * A model source that cannot be trusted or parsed. These are fatal: the importer never
 * returns a partially-populated model for malformed JSON, duplicate ids, invalid
 * dimensions or unsafe paths.
 */
public final class ModelImportException extends Exception {

    private final List<ModelIR.Diagnostic> diagnostics;

    public ModelImportException(@NotNull String message) {
        this(message, List.of());
    }

    public ModelImportException(@NotNull String message,
                                @NotNull List<ModelIR.Diagnostic> diagnostics) {
        super(message);
        this.diagnostics = List.copyOf(diagnostics);
    }

    public ModelImportException(@NotNull String message, @NotNull Throwable cause) {
        super(message, cause);
        this.diagnostics = List.of();
    }

    public @NotNull List<ModelIR.Diagnostic> diagnostics() {
        return diagnostics;
    }
}
