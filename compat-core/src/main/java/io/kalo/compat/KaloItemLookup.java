package io.kalo.compat;

import io.kalo.Kalo;
import io.kalo.content.item.Item;
import net.kyori.adventure.key.Key;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * The only place the shims touch Kalo's registries. Everything shaped like another plugin's API
 * goes through here, so those classes carry no Kalo knowledge and Kalo's API can move without
 * touching them.
 */
public final class KaloItemLookup {
    /** Kalo stamps every item it builds with its namespaced key under this PDC entry. */
    private static final NamespacedKey ITEM_ID_KEY = new NamespacedKey("kalo", "item");

    private KaloItemLookup() {
    }

    /** Every Kalo item key as "namespace:value". */
    public static @NotNull List<String> keys() {
        List<String> keys = new ArrayList<>();
        Kalo.plugin().registryManager().registries().item().entries()
                .forEach(entry -> keys.add(entry.left().asString()));
        return keys;
    }

    /** A fresh copy of the stack for a flat or namespaced id, or null when no Kalo item matches. */
    public static @Nullable ItemStack stackFor(@NotNull String id) {
        String key = CompatIds.resolve(id, keys());
        if (key == null) {
            return null;
        }
        return Kalo.plugin().registryManager().registries().item().get(Key.key(key))
                .map(Item::itemStack).map(stack -> stack.get()).orElse(null);
    }

    /** The Kalo key string stamped on the stack, or null if it is not a Kalo item. */
    public static @Nullable String keyOf(@Nullable ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return null;
        }
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            return null;
        }
        return meta.getPersistentDataContainer().get(ITEM_ID_KEY, PersistentDataType.STRING);
    }
}
