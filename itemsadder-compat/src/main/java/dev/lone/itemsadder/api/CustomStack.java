package dev.lone.itemsadder.api;

import io.kalo.compat.CompatIds;
import io.kalo.compat.KaloItemLookup;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Subset of ItemsAdder's {@code CustomStack}, answered from Kalo's item registry.
 *
 * <p>ItemsAdder ids are already namespaced ({@code namespace:id}), which is exactly how Kalo
 * keys its items, so they map one to one. Only members whose signature is verified and whose
 * meaning is unambiguous exist; the rest fail with {@code NoSuchMethodError}. Left out because
 * Kalo has no equivalent or the meaning is unverified: display name and item name (encoding),
 * model, config and permission accessors, durability and usages, cooldowns and stats, and the
 * block, furniture and vehicle queries (Kalo blocks and furniture are not in the item registry).
 * Pinned by {@code ItemsAdderApiSignatureTest}; see {@code docs/ITEMSADDER_COMPAT.md}.</p>
 */
public class CustomStack {
    private final String namespacedId;

    private CustomStack(String namespacedId) {
        this.namespacedId = namespacedId;
    }

    public static Set<String> getNamespacedIdsInRegistry() {
        return new LinkedHashSet<>(KaloItemLookup.keys());
    }

    public static boolean isInRegistry(String namespacedId) {
        return KaloItemLookup.stackFor(namespacedId) != null;
    }

    @Nullable
    public static CustomStack byItemStack(ItemStack itemStack) {
        String key = KaloItemLookup.keyOf(itemStack);
        return key == null ? null : new CustomStack(key);
    }

    @Nullable
    public static CustomStack getInstance(String namespacedID) {
        String key = CompatIds.resolve(namespacedID, KaloItemLookup.keys());
        return key == null ? null : new CustomStack(key);
    }

    public String getNamespace() {
        int separator = namespacedId.indexOf(':');
        return separator < 0 ? "" : namespacedId.substring(0, separator);
    }

    public String getId() {
        return CompatIds.fromKaloKey(namespacedId);
    }

    public String getNamespacedID() {
        return namespacedId;
    }

    public boolean matchNamespacedID(CustomStack other) {
        return other != null && namespacedId.equals(other.namespacedId);
    }

    /** A fresh copy each call; changing it never changes Kalo's registry. */
    public ItemStack getItemStack() {
        return KaloItemLookup.stackFor(namespacedId);
    }

    static List<CustomStack> all() {
        List<CustomStack> result = new java.util.ArrayList<>();
        for (String key : KaloItemLookup.keys()) {
            result.add(new CustomStack(key));
        }
        return result;
    }
}
