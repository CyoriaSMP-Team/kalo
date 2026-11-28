package io.kalo.model;

import io.kalo.model.ModelIR.Diagnostic;
import io.kalo.model.ModelIR.Severity;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Shared logging for model diagnostics so every compiler reports the same way. */
public final class ModelDiagnostics {

    private ModelDiagnostics() {
    }

    public static void log(@NotNull Logger logger, @NotNull String subject,
                           @NotNull List<Diagnostic> diagnostics) {
        for (Diagnostic diagnostic : diagnostics) {
            Level level = switch (diagnostic.severity()) {
                case ERROR -> Level.SEVERE;
                case UNSUPPORTED -> Level.WARNING;
                case WARNING -> Level.WARNING;
                case INFO -> Level.FINE;
            };
            logger.log(level, subject + " [" + diagnostic.code() + "]: "
                    + diagnostic.message());
        }
    }

    public static boolean hasErrors(@NotNull List<Diagnostic> diagnostics) {
        return diagnostics.stream().anyMatch(d -> d.severity() == Severity.ERROR);
    }
}
