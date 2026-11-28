package com.nexomc.nexo.api

import io.kalo.compat.CompatIds
import io.kalo.compat.KaloBlocks
import io.kalo.compat.KaloItemLookup
import org.bukkit.Location
import org.bukkit.inventory.ItemStack

/**
 * Nexo's furniture API, answered from Kalo's furniture. Kalo furniture is a static, block-backed
 * piece, not an entity, so everything that returns or takes a furniture entity (`place`, `remove`,
 * `baseEntity`, `furnitureMechanic`, `updateFurniture`, `findTargetFurniture`, `isFurniture(Entity)`)
 * has nothing to work with and is not implemented: a call fails with `NoSuchMethodError`.
 */
object NexoFurniture {

    @JvmStatic
    fun furnitureIDs(): Array<String> =
        KaloBlocks.furnitureKeys().map(CompatIds::fromKaloKey).toTypedArray()

    @JvmStatic
    fun isFurniture(location: Location): Boolean =
        KaloBlocks.kindAt(location.block) == KaloItemLookup.Kind.FURNITURE

    @JvmStatic
    fun isFurniture(itemID: String): Boolean = KaloBlocks.kindOfId(itemID) == KaloItemLookup.Kind.FURNITURE

    @JvmStatic
    fun isFurniture(itemStack: ItemStack): Boolean =
        KaloBlocks.kindOfStack(itemStack) == KaloItemLookup.Kind.FURNITURE
}
