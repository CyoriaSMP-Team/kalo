package com.nexomc.nexo.items

import net.kyori.adventure.text.Component
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.enchantments.Enchantment
import org.bukkit.inventory.ItemFlag
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.Damageable
import org.bukkit.persistence.PersistentDataType

/**
 * Subset of Nexo's `ItemBuilder`, backed by a plain [ItemStack].
 *
 * Only the members listed here exist. A plugin calling anything else gets a
 * `NoSuchMethodError` at that call, which is the honest failure: a stub that silently did
 * nothing would hand out wrong items. Supported surface is documented in `docs/NEXO_COMPAT.md`.
 */
class ItemBuilder : Cloneable {
    private var stack: ItemStack

    constructor(material: Material) {
        stack = ItemStack(material)
    }

    constructor(itemStack: ItemStack) {
        stack = itemStack.clone()
    }

    constructor(itemBuilder: ItemBuilder) {
        stack = itemBuilder.stack.clone()
    }

    private inline fun edit(block: (org.bukkit.inventory.meta.ItemMeta) -> Unit): ItemBuilder {
        val meta = stack.itemMeta ?: return this
        block(meta)
        stack.itemMeta = meta
        return this
    }

    // --- amount / type ---
    fun getAmount(): Int = stack.amount
    fun setAmount(amount: Int): ItemBuilder { stack.amount = amount; return this }
    fun getType(): Material = stack.type
    fun setType(type: Material): ItemBuilder { stack = stack.withType(type); return this }

    // --- naming ---
    fun getDisplayName(): Component? = stack.itemMeta?.displayName()
    fun displayName(displayName: Component?): ItemBuilder = edit { it.displayName(displayName) }
    fun getItemName(): Component? = stack.itemMeta?.takeIf { it.hasItemName() }?.itemName()
    fun itemName(itemName: Component?): ItemBuilder = edit { it.itemName(itemName) }
    fun hasItemName(): Boolean = stack.itemMeta?.hasItemName() == true

    // --- lore ---
    fun getLore(): List<Component>? = stack.itemMeta?.lore()
    fun lore(): List<Component>? = getLore()
    fun lore(lore: List<Component>?): ItemBuilder = edit { it.lore(lore) }
    fun hasLores(): Boolean = stack.itemMeta?.hasLore() == true

    // --- durability / flags ---
    fun getDurability(): Int? = (stack.itemMeta as? Damageable)?.damage
    fun setDurability(durability: Int): ItemBuilder = edit { (it as? Damageable)?.damage = durability }
    fun getUnbreakable(): Boolean = stack.itemMeta?.isUnbreakable == true
    fun setUnbreakable(unbreakable: Boolean): ItemBuilder = edit { it.isUnbreakable = unbreakable }
    fun addItemFlags(vararg itemFlags: ItemFlag): ItemBuilder = edit { it.addItemFlags(*itemFlags) }
    fun itemFlags(): Set<ItemFlag> = stack.itemMeta?.itemFlags ?: emptySet()
    fun getItemFlags(): Set<ItemFlag> = itemFlags()

    // --- model data ---
    fun getCustomModelData(): Int? = stack.itemMeta?.takeIf { it.hasCustomModelData() }?.customModelData
    fun customModelData(customModelData: Int?): ItemBuilder = edit { it.setCustomModelData(customModelData) }

    // --- enchants ---
    fun addEnchant(enchant: Enchantment, level: Int): ItemBuilder = edit { it.addEnchant(enchant, level, true) }
    fun addEnchants(enchants: Map<Enchantment, Int>): ItemBuilder = edit { meta ->
        enchants.forEach { (enchant, level) -> meta.addEnchant(enchant, level, true) }
    }
    fun getEnchantments(): Map<Enchantment, Int> = stack.itemMeta?.enchants ?: emptyMap()

    // --- persistent data ---
    fun <T : Any, Z : Any> customTag(key: NamespacedKey, type: PersistentDataType<T, Z>, data: Z): ItemBuilder =
        edit { it.persistentDataContainer.set(key, type, data) }

    fun <T : Any, Z : Any> customTag(key: NamespacedKey, type: PersistentDataType<T, Z>): Z? =
        stack.itemMeta?.persistentDataContainer?.get(key, type)

    fun removeCustomTag(key: NamespacedKey): ItemBuilder = edit { it.persistentDataContainer.remove(key) }

    // --- Nexo-specific: Kalo has no equivalent, so these report "none" ---
    fun hasNexoMeta(): Boolean = false

    // --- output ---
    fun build(): ItemStack = stack.clone()
    fun buildArray(amount: Int): Array<ItemStack> = Array(amount) { stack.clone() }
    fun referenceCopy(): ItemStack = stack.clone()
    public override fun clone(): ItemBuilder = ItemBuilder(this)
    fun regenerateItem(): ItemBuilder = this
}
