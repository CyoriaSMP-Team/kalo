package com.nexomc.nexo.api

import com.nexomc.nexo.items.ItemBuilder
import io.kalo.nexo.KaloItems
import io.kalo.nexo.NexoIds
import org.bukkit.inventory.ItemStack
import java.io.File
import java.util.Optional

/**
 * Nexo's item API, answered from Kalo's registries. Written as a Kotlin `object` with
 * `@JvmStatic` members on purpose: that is exactly how Nexo exposes it, and it is what makes
 * both Java callers (static) and Kotlin callers (`INSTANCE`) link.
 */
object NexoItems {

    @JvmStatic
    fun loadItems() = Unit

    /** Kalo owns loading; there is no per-item reload. */
    fun reloadItem(itemId: String) = Unit

    @JvmStatic
    fun idFromItem(item: ItemBuilder): String? = idFromItem(item.build())

    @JvmStatic
    fun idFromItem(item: ItemStack): String? = KaloItems.keyOf(item)?.let(NexoIds::fromKaloKey)

    @JvmStatic
    fun exists(itemId: String): Boolean = KaloItems.stackFor(itemId) != null

    @JvmStatic
    fun exists(itemStack: ItemStack): Boolean = KaloItems.keyOf(itemStack) != null

    @JvmStatic
    fun itemFromId(id: String): ItemBuilder? = KaloItems.stackFor(id)?.let(::ItemBuilder)

    @JvmStatic
    fun optionalItemFromId(id: String): Optional<ItemBuilder> = Optional.ofNullable(itemFromId(id))

    @JvmStatic
    fun builderFromItem(item: ItemStack): ItemBuilder? = idFromItem(item)?.let(::itemFromId)

    /** Kalo has no per-file exclusion; every item is "unexcluded". */
    @JvmStatic
    fun unexcludedItems(file: File): List<ItemBuilder> = emptyList()

    /** Kalo has no Nexo-style mechanics, so no item has one. */
    @JvmStatic
    fun hasMechanic(itemID: String, mechanicID: String): Boolean = false

    /** Kalo does not group items by source file. */
    @JvmStatic
    fun itemMap(): Map<File, Map<String, ItemBuilder>> = emptyMap()

    @JvmStatic
    fun entries(): Map<String, ItemBuilder> {
        val result = LinkedHashMap<String, ItemBuilder>()
        for (id in itemNames()) itemFromId(id)?.let { result[id] = it }
        return result
    }

    @JvmStatic
    fun items(): Set<ItemBuilder> = entries().values.toCollection(LinkedHashSet())

    @JvmStatic
    fun itemNames(): Set<String> = KaloItems.keys().map(NexoIds::fromKaloKey).toCollection(LinkedHashSet())

    @JvmStatic
    fun unexcludedItemNames(): Array<String> = itemNames().toTypedArray()
}
