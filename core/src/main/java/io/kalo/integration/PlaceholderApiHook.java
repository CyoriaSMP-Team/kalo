package io.kalo.integration;

import io.kalo.manager.RegistryManager;
import io.kalo.utils.Constants;
import io.kalo.utils.Plugins;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Exposes Kalo content to PlaceholderAPI.
 *
 * <p>Only ever loaded from {@link PlaceholderApiIntegration}, which does the
 * is-it-installed check. This class extends a PlaceholderAPI type, so loading it at all
 * requires PlaceholderAPI to be present — the guard cannot live here.</p>
 *
 * <table>
 *   <tr><td>{@code %kalo_held_id%}</td><td>the Kalo id of the held item, or empty</td></tr>
 *   <tr><td>{@code %kalo_held_name%}</td><td>its display name as plain text</td></tr>
 *   <tr><td>{@code %kalo_is_held_<key>%}</td><td>{@code true}/{@code false}</td></tr>
 *   <tr><td>{@code %kalo_count_<key>%}</td><td>how many are in the player's inventory</td></tr>
 *   <tr><td>{@code %kalo_items%}</td><td>number of registered items</td></tr>
 * </table>
 */
public final class PlaceholderApiHook extends PlaceholderExpansion {

    private PlaceholderApiHook() {
    }

    /**
     * Called only after {@link PlaceholderApiIntegration} has confirmed PlaceholderAPI is
     * loaded. Named {@code install} rather than {@code register} to avoid colliding with
     * {@link PlaceholderExpansion#register()}, which this calls.
     */
    static @Nullable Object install() {
        PlaceholderApiHook hook = new PlaceholderApiHook();
        return hook.register() ? hook : null;
    }

    /** Object-typed so the guarding integration never exposes a PlaceholderAPI type. */
    static void uninstall(@NotNull Object installed) {
        if (installed instanceof PlaceholderApiHook hook) {
            hook.unregister();
        }
    }

    @Override
    public @NotNull String getIdentifier() {
        return Constants.PLUGIN_ID;
    }

    @Override
    public @NotNull String getAuthor() {
        return "Kalo";
    }

    @Override
    public @NotNull String getVersion() {
        return Plugins.plugin().getPluginMeta().getVersion();
    }

    /** Kalo outlives a PlaceholderAPI reload, so the expansion should too. */
    @Override
    public boolean persist() {
        return true;
    }

    /**
     * {@code OfflinePlayer} rather than {@code Player}: PlaceholderAPI resolves
     * placeholders for offline players too, and most of these need an online inventory,
     * so the online case is narrowed explicitly below.
     */
    @Override
    public @Nullable String onRequest(@Nullable OfflinePlayer offlinePlayer, @NotNull String params) {
        RegistryManager.GlobalRegistries registries = RegistryManager.GlobalRegistries.registries();

        if (params.equals("items")) {
            return String.valueOf(registries.item().entries().size());
        }
        if (params.equals("blocks")) {
            return String.valueOf(registries.block().entries().size());
        }

        // Everything below reads a live inventory, which an offline player does not have.
        if (!(offlinePlayer instanceof Player player)) {
            return null;
        }

        if (params.equals("held_id")) {
            HeldContentResolver.ResolvedContent held = heldContent(player, registries);
            return held != null ? held.id() : "";
        }
        if (params.equals("held_name")) {
            return heldName(player, registries);
        }
        if (params.startsWith("is_held_")) {
            HeldContentResolver.ResolvedContent held = heldContent(player, registries);
            return String.valueOf(held != null
                    && params.substring("is_held_".length()).equals(held.id()));
        }
        if (params.startsWith("count_")) {
            return String.valueOf(count(player, params.substring("count_".length()), registries));
        }

        // null rather than "" so PlaceholderAPI leaves an unknown placeholder visible
        // instead of quietly blanking it, which is easier to debug in a config.
        return null;
    }

    private static @Nullable HeldContentResolver.ResolvedContent heldContent(
            @NotNull Player player,
            @NotNull RegistryManager.GlobalRegistries registries
    ) {
        ItemStack held = player.getInventory().getItemInMainHand();
        return held.isEmpty() ? null : HeldContentResolver.resolve(held, registries);
    }

    private static @NotNull String heldName(
            @NotNull Player player,
            @NotNull RegistryManager.GlobalRegistries registries
    ) {
        HeldContentResolver.ResolvedContent held = heldContent(player, registries);
        if (held == null) {
            return "";
        }
        return HeldContentResolver.displayName(held.content());
    }

    private static int count(
            @NotNull Player player,
            @NotNull String id,
            @NotNull RegistryManager.GlobalRegistries registries
    ) {
        int total = 0;
        for (ItemStack stack : player.getInventory().getContents()) {
            HeldContentResolver.ResolvedContent content = stack == null || stack.isEmpty()
                    ? null : HeldContentResolver.resolve(stack, registries);
            if (content != null && id.equals(content.id())) {
                total += stack.getAmount();
            }
        }
        return total;
    }

}
