package io.th0rgal.oraxen.items;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Subset of Oraxen's {@code ItemBuilder}, backed by a plain {@link ItemStack}.
 *
 * <p>Only members whose signature and meaning are verified exist. A plugin calling anything
 * else gets a {@code NoSuchMethodError} at that call, which is the honest failure: a stub that
 * silently did nothing would hand out wrong items. Name and lore accessors in particular are
 * left out because Oraxen's string encoding for them has not been verified. The supported
 * surface is pinned by {@code OraxenApiSignatureTest} and listed in {@code docs/ORAXEN_COMPAT.md}.</p>
 */
public class ItemBuilder implements Cloneable {
    private ItemStack stack;

    public ItemBuilder(final Material material) {
        this.stack = new ItemStack(material);
    }

    public ItemBuilder(@NotNull ItemStack itemStack) {
        this.stack = itemStack.clone();
    }

    private ItemBuilder(ItemBuilder other) {
        this.stack = other.stack.clone();
    }

    private ItemBuilder edit(Consumer<ItemMeta> change) {
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            change.accept(meta);
            stack.setItemMeta(meta);
        }
        return this;
    }

    public Material getType() {
        return stack.getType();
    }

    public ItemBuilder setType(final Material type) {
        stack = stack.withType(type);
        return this;
    }

    public ItemBuilder setAmount(int amount) {
        stack.setAmount(amount);
        return this;
    }

    public boolean hasLores() {
        ItemMeta meta = stack.getItemMeta();
        return meta != null && meta.hasLore();
    }

    public ItemBuilder setUnbreakable(final boolean unbreakable) {
        return edit(meta -> meta.setUnbreakable(unbreakable));
    }

    public Integer getDurability() {
        return stack.getItemMeta() instanceof Damageable damageable ? damageable.getDamage() : null;
    }

    public ItemBuilder setDurability(Integer durability) {
        return durability == null ? this : edit(meta -> {
            if (meta instanceof Damageable damageable) {
                damageable.setDamage(durability);
            }
        });
    }

    public boolean hasItemModel() {
        ItemMeta meta = stack.getItemMeta();
        return meta != null && meta.hasItemModel();
    }

    public NamespacedKey getItemModel() {
        ItemMeta meta = stack.getItemMeta();
        return meta != null && meta.hasItemModel() ? meta.getItemModel() : null;
    }

    public ItemBuilder setItemModel(final NamespacedKey itemModel) {
        return edit(meta -> meta.setItemModel(itemModel));
    }

    @SuppressWarnings("deprecation")
    public ItemBuilder setCustomModelData(final int customModelData) {
        return edit(meta -> meta.setCustomModelData(customModelData));
    }

    public ItemBuilder addItemFlags(final ItemFlag... itemFlags) {
        return edit(meta -> meta.addItemFlags(itemFlags));
    }

    public List<ItemFlag> getItemFlags() {
        ItemMeta meta = stack.getItemMeta();
        return meta == null ? new ArrayList<>() : new ArrayList<>(meta.getItemFlags());
    }

    public ItemBuilder addEnchant(final Enchantment enchant, final int level) {
        return edit(meta -> meta.addEnchant(enchant, level, true));
    }

    public ItemBuilder addEnchants(final Map<Enchantment, Integer> enchants) {
        return edit(meta -> enchants.forEach((enchant, level) -> meta.addEnchant(enchant, level, true)));
    }

    public <T, Z> ItemBuilder setCustomTag(final NamespacedKey namespacedKey,
                                           final PersistentDataType<T, Z> dataType, final Z data) {
        return edit(meta -> meta.getPersistentDataContainer().set(namespacedKey, dataType, data));
    }

    public <T, Z> Z getCustomTag(final NamespacedKey namespacedKey, final PersistentDataType<T, Z> dataType) {
        ItemMeta meta = stack.getItemMeta();
        return meta == null ? null : meta.getPersistentDataContainer().get(namespacedKey, dataType);
    }

    public ItemBuilder removeCustomTag(NamespacedKey key) {
        return edit(meta -> meta.getPersistentDataContainer().remove(key));
    }

    /** Kalo has no Oraxen-style per-item resource meta. */
    public boolean hasOraxenMeta() {
        return false;
    }

    public ItemStack getReferenceClone() {
        return stack.clone();
    }

    @Override
    public ItemBuilder clone() {
        return new ItemBuilder(this);
    }

    /** Kalo owns item generation; there is nothing to regenerate. */
    public synchronized ItemBuilder regen() {
        return this;
    }

    public ItemStack[] buildArray(final int amount) {
        ItemStack[] result = new ItemStack[amount];
        for (int i = 0; i < amount; i++) {
            result[i] = stack.clone();
        }
        return result;
    }

    public ItemStack build() {
        return stack.clone();
    }
}
