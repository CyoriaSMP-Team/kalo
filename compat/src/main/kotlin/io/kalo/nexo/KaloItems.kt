package io.kalo.nexo

import io.kalo.Kalo
import net.kyori.adventure.key.Key
import org.bukkit.NamespacedKey
import org.bukkit.inventory.ItemStack
import org.bukkit.persistence.PersistentDataType

/**
 * The only place the shim touches Kalo's registries. Everything Nexo-shaped goes through here,
 * so the Nexo classes carry no Kalo knowledge and Kalo's API can move without touching them.
 */
object KaloItems {
    /** Kalo stamps every item it builds with its namespaced key under this PDC entry. */
    private val ITEM_ID_KEY = NamespacedKey("kalo", "item")

    private fun registry() = Kalo.plugin().registryManager().registries().item()

    /** Every Kalo item key as "namespace:value". */
    fun keys(): List<String> = registry().entries().map { it.left().asString() }

    /** A fresh copy of the stack for a Nexo id, or null when no Kalo item matches. */
    fun stackFor(id: String): ItemStack? {
        val key = NexoIds.resolve(id, keys()) ?: return null
        return registry().get(Key.key(key)).map { it.itemStack().get() }.orElse(null)
    }

    /** The Kalo key string stamped on [stack], or null if it is not a Kalo item. */
    fun keyOf(stack: ItemStack?): String? {
        if (stack == null || stack.isEmpty) return null
        val meta = stack.itemMeta ?: return null
        return meta.persistentDataContainer.get(ITEM_ID_KEY, PersistentDataType.STRING)
    }
}
