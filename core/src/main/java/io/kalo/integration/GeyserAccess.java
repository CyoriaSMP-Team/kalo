package io.kalo.integration;

import org.geysermc.cumulus.form.Form;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Method;
import java.util.UUID;

/**
 * Optional Geyser API access that never links against Geyser.
 *
 * <p>Kalo runs on two kinds of network. A backend behind a proxy has Floodgate but no
 * Geyser, so a direct {@code GeyserApi.api()} call resolves to nothing and throws
 * {@link NoClassDefFoundError} — an error, not an exception, so neither {@code catch
 * (Exception)} nor a null check saves the caller. Inventory opens then fail on every menu.
 * A standalone server has Geyser but no Floodgate. Reflection over the public interface
 * works on both, and an absent Geyser is simply false.</p>
 */
public final class GeyserAccess {

    private static final String API_TYPE = "org.geysermc.geyser.api.GeyserApi";
    private static final String CONNECTION_TYPE = "org.geysermc.api.connection.Connection";

    private GeyserAccess() {
    }

    /** Whether this UUID is a Bedrock connection known to the given Geyser classloader. */
    public static boolean isBedrock(@Nullable ClassLoader geyserLoader, @NotNull UUID uuid) {
        Object connection = connection(geyserLoader, uuid);
        return connection != null;
    }

    /** Sends a Cumulus form, returning false when no Geyser connection owns this UUID. */
    public static boolean sendForm(@Nullable ClassLoader geyserLoader, @NotNull UUID uuid,
                                   @NotNull Form form) {
        Object connection = connection(geyserLoader, uuid);
        if (connection == null) {
            return false;
        }
        try {
            Class<?> type = Class.forName(CONNECTION_TYPE, false, geyserLoader);
            Method send = type.getMethod("sendForm", Form.class);
            return Boolean.TRUE.equals(send.invoke(connection, form));
        } catch (ReflectiveOperationException | LinkageError | RuntimeException unavailable) {
            return false;
        }
    }

    private static @Nullable Object connection(@Nullable ClassLoader geyserLoader,
                                               @NotNull UUID uuid) {
        if (geyserLoader == null) {
            return null;
        }
        try {
            Class<?> apiType = Class.forName(API_TYPE, false, geyserLoader);
            Object api = apiType.getMethod("api").invoke(null);
            if (api == null) {
                return null;
            }
            return apiType.getMethod("connectionByUuid", UUID.class).invoke(api, uuid);
        } catch (ReflectiveOperationException | LinkageError | RuntimeException unavailable) {
            // Geyser is not installed, not started, or a different API generation.
            return null;
        }
    }
}
