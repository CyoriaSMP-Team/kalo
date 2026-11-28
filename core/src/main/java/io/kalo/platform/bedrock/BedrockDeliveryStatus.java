package io.kalo.platform.bedrock;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;

/**
 * Where the Bedrock pack went, in one line.
 *
 * <p>Silent failure is the reason this exists: the pack was generated, no Geyser read it,
 * and the only symptom was Bedrock clients showing vanilla models. Diagnostics now say
 * whether the pack reached a Geyser and, if not, why.</p>
 */
public final class BedrockDeliveryStatus {

    public enum Mode {
        /** Geyser shares this JVM; the API path registers items directly. */
        IN_PROCESS,
        /** Kalo wrote its pack and mappings into a separate Geyser home. */
        STANDALONE,
        /** The pack exists only in Kalo's own folder. */
        NONE
    }

    private static volatile Mode mode = Mode.NONE;
    private static volatile String detail = "not generated yet";

    private BedrockDeliveryStatus() {
    }

    public static void inProcess() {
        mode = Mode.IN_PROCESS;
        detail = "Geyser shares this JVM";
    }

    public static void standalone(@NotNull Path home, int updatedFiles) {
        mode = Mode.STANDALONE;
        detail = home + (updatedFiles == 0 ? " (up to date)" : " (" + updatedFiles + " file(s) updated)");
    }

    public static void failed(@Nullable Path home, @Nullable String reason) {
        mode = Mode.NONE;
        detail = (home == null ? "" : home + ": ") + (reason == null ? "unknown error" : reason);
    }

    public static void notDelivered(@NotNull String reason) {
        mode = Mode.NONE;
        detail = reason;
    }

    public static @NotNull Mode mode() {
        return mode;
    }

    /** One human-readable line for {@code /kalo doctor}. */
    public static @NotNull String describe() {
        return switch (mode) {
            case IN_PROCESS -> "in-process Geyser";
            case STANDALONE -> "standalone -> " + detail;
            case NONE -> "NOT DELIVERED (" + detail + ")";
        };
    }
}
