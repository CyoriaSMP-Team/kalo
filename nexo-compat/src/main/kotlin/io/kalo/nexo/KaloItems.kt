package io.kalo.nexo

import io.kalo.compat.KaloItemLookup
import org.bukkit.inventory.ItemStack

/** Nexo-facing view of the shared Kalo lookup; see [KaloItemLookup]. */
object KaloItems {
    fun keys(): List<String> = KaloItemLookup.keys()
    fun stackFor(id: String): ItemStack? = KaloItemLookup.stackFor(id)
    fun keyOf(stack: ItemStack?): String? = KaloItemLookup.keyOf(stack)
}
