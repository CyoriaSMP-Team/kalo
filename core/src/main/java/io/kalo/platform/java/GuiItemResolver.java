package io.kalo.platform.java;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.Function;

/**
 * Resolves a GUI item reference without ever silently substituting PAPER for missing
 * custom content. Preserving the original Kalo stack preserves its item_model, PDC key
 * and properties, so Geyser can map the *same* item as the rest of the world.
 */
public final class GuiItemResolver {
    private GuiItemResolver() {}

    public static @Nullable ItemStack resolve(@NotNull String name, int amount,
            @NotNull Function<NamespacedKey, ItemStack> customLookup) {
        if (name.isBlank()) return null;
        Material material = Material.matchMaterial(name);
        if (material != null && material.isItem()) {
            ItemStack item = new ItemStack(material);
            item.setAmount(boundedAmount(amount, item.getMaxStackSize()));
            return item;
        }
        // Explicit namespace prevents a typo in a vanilla material from resolving to
        // an unrelated Kalo item in the implicit minecraft namespace.
        NamespacedKey key = customKey(name);
        if (key == null) return null;
        ItemStack original = customLookup.apply(key);
        if (original == null) return null;
        ItemStack copy = original.clone();
        copy.setAmount(boundedAmount(amount, copy.getMaxStackSize()));
        return copy;
    }

    static @Nullable NamespacedKey customKey(@NotNull String name) {
        if (!name.contains(":")) return null;
        try {
            return NamespacedKey.fromString(name);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    static int boundedAmount(int amount, int maxStackSize) {
        return Math.clamp(amount, 1, Math.max(1, maxStackSize));
    }
}
