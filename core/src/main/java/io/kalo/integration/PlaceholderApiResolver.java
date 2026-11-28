package io.kalo.integration;

import me.clip.placeholderapi.PlaceholderAPI;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

/**
 * Direct PlaceholderAPI bridge, loaded only through the guarded integration class.
 *
 * <p>Keeping the import in this class means a Java-only server never resolves
 * PlaceholderAPI when no condition asks for an external placeholder.</p>
 */
public final class PlaceholderApiResolver {

    private PlaceholderApiResolver() {
    }

    public static @NotNull String resolve(@NotNull Player player,
                                          @NotNull String placeholder) {
        String token = placeholder;
        if (!(token.startsWith("%") && token.endsWith("%"))) {
            token = "%" + token + "%";
        }
        String value = PlaceholderAPI.setPlaceholders(player, token);
        // PlaceholderAPI returns the token unchanged when no expansion knows it.
        return value == null || value.equals(token) ? "" : value;
    }
}
