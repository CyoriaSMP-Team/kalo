package net.momirealms.craftengine.bukkit.api;

import io.kalo.compat.KaloItemLookup;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

/**
 * The part of CraftEngine's item API that uses only Bukkit types, answered from Kalo. Anything that
 * takes or returns CraftEngine's own types ({@code Key}, {@code BukkitItemDefinition},
 * {@code ItemDefinition}: {@code byId}, {@code byItemStack}, {@code loadedItems},
 * {@code getCustomItemId}) is not implemented and fails with {@code NoSuchMethodError}.
 */
public final class CraftEngineItems {
    private CraftEngineItems() {
    }

    /** True for any Kalo item, including the item form of a Kalo block or furniture piece. */
    public static boolean isCustomItem(@NotNull ItemStack itemStack) {
        return KaloItemLookup.keyOf(itemStack) != null;
    }
}
