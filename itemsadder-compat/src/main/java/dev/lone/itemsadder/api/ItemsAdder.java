package dev.lone.itemsadder.api;

import io.kalo.compat.KaloItemLookup;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Subset of ItemsAdder's static {@code ItemsAdder} class, answered from Kalo's item registry.
 * Only the item lookups remain, including the ones ItemsAdder itself marks deprecated, because
 * older plugins still call them. Custom blocks, crops, liquids, recipes, resource-pack and
 * totem methods are not implemented and fail with {@code NoSuchMethodError}.
 */
public class ItemsAdder {
    private ItemsAdder() {
    }

    public static List<CustomStack> getAllItems() {
        return CustomStack.all();
    }

    public static List<CustomStack> getAllItems(String namespace) {
        List<CustomStack> result = new ArrayList<>();
        for (CustomStack stack : CustomStack.all()) {
            if (stack.getNamespace().equals(namespace)) {
                result.add(stack);
            }
        }
        return result;
    }

    public static List<CustomStack> getAllItems(Material material) {
        List<CustomStack> result = new ArrayList<>();
        for (CustomStack stack : CustomStack.all()) {
            ItemStack item = stack.getItemStack();
            if (item != null && item.getType() == material) {
                result.add(stack);
            }
        }
        return result;
    }

    /** Kalo loads its content before any plugin that depends on it enables. */
    @Deprecated
    public static boolean areItemsLoaded() {
        return true;
    }

    @Deprecated
    public static boolean isCustomItem(ItemStack itemStack) {
        return KaloItemLookup.keyOf(itemStack) != null;
    }

    @Deprecated
    public static boolean isCustomItem(String customItemName) {
        return CustomStack.isInRegistry(customItemName);
    }

    @Deprecated
    @Nullable
    public static ItemStack getCustomItem(String namespacedId) {
        return KaloItemLookup.stackFor(namespacedId);
    }
}
