package io.kalo.compat;

import io.kalo.Kalo;
import io.kalo.registry.Registries;
import net.kyori.adventure.key.Key;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * The only place the shims touch Kalo's registries. Everything shaped like another plugin's API
 * goes through here, so those classes carry no Kalo knowledge and Kalo's API can move without
 * touching them.
 *
 * <p>Other plugins treat blocks and furniture as items too (they have an item form and an id), so
 * the lookup spans Kalo's three registries: items, blocks and furniture. Kalo stamps an item's key
 * under {@code kalo:item} and a block's or furniture's item form under {@code kalo:block}; both are
 * read here.</p>
 */
public final class KaloItemLookup {
    private static final NamespacedKey ITEM_ID_KEY = new NamespacedKey("kalo", "item");
    private static final NamespacedKey BLOCK_ID_KEY = new NamespacedKey("kalo", "block");

    /** What kind of Kalo content a key names. */
    public enum Kind { ITEM, BLOCK, FURNITURE }

    private KaloItemLookup() {
    }

    private static Registries registries() {
        return Kalo.plugin().registryManager().registries();
    }

    /** Every Kalo item, block and furniture key as "namespace:value", items first, no duplicates. */
    public static @NotNull List<String> keys() {
        Registries registries = registries();
        Set<String> keys = new LinkedHashSet<>();
        registries.item().entries().forEach(entry -> keys.add(entry.left().asString()));
        registries.block().entries().forEach(entry -> keys.add(entry.left().asString()));
        registries.furniture().entries().forEach(entry -> keys.add(entry.left().asString()));
        return new ArrayList<>(keys);
    }

    /** What the exact Kalo key names, or null if no such content exists. Furniture wins over block. */
    public static @Nullable Kind kindOf(@NotNull String key) {
        Registries registries = registries();
        Key parsed;
        try {
            parsed = Key.key(key);
        } catch (RuntimeException invalid) {
            return null;
        }
        if (registries.item().get(parsed).isPresent()) {
            return Kind.ITEM;
        }
        if (registries.furniture().get(parsed).isPresent()) {
            return Kind.FURNITURE;
        }
        return registries.block().get(parsed).isPresent() ? Kind.BLOCK : null;
    }

    /** A fresh copy of the stack for a flat or namespaced id, or null when nothing matches. */
    public static @Nullable ItemStack stackFor(@NotNull String id) {
        String key = CompatIds.resolve(id, keys());
        if (key == null) {
            return null;
        }
        Registries registries = registries();
        Key parsed = Key.key(key);
        Optional<ItemStack> found = registries.item().get(parsed).map(item -> item.itemStack().get());
        if (found.isEmpty()) {
            found = registries.furniture().get(parsed).map(furniture -> furniture.itemStack().get());
        }
        if (found.isEmpty()) {
            found = registries.block().get(parsed).map(block -> block.itemStack().get());
        }
        return found.orElse(null);
    }

    /** The Kalo key string stamped on the stack (item, block or furniture), or null if not Kalo's. */
    public static @Nullable String keyOf(@Nullable ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return null;
        }
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            return null;
        }
        String key = meta.getPersistentDataContainer().get(ITEM_ID_KEY, PersistentDataType.STRING);
        return key != null ? key : meta.getPersistentDataContainer().get(BLOCK_ID_KEY, PersistentDataType.STRING);
    }
}
