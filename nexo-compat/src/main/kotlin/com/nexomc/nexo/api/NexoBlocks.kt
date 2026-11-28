package com.nexomc.nexo.api

import io.kalo.compat.CompatIds
import io.kalo.compat.KaloBlocks
import io.kalo.compat.KaloItemLookup
import org.bukkit.GameMode
import org.bukkit.Location
import org.bukkit.block.Block
import org.bukkit.block.data.BlockData
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack

/**
 * Nexo's block API, answered from Kalo's blocks. Only members whose signature is verified and whose
 * meaning maps onto a Kalo block exist. Not implemented, so a call fails with `NoSuchMethodError`:
 * the mechanic accessors (`customBlockMechanic`, `noteBlockMechanic`, `stringMechanic`,
 * `chorusBlockMechanic`; Kalo has no Nexo mechanics), the per-kind id and test methods (Nexo's
 * note/string/chorus kinds are not Kalo's carriers), and the `Drop` overload of `remove`.
 *
 * `remove` drops the item when `forceDrop` is set, or when a player who is not in creative mode
 * breaks it; with no player it does not drop. That rule is a choice made here: Nexo's own default
 * has not been verified.
 */
object NexoBlocks {

    /** Every Kalo block id (not furniture), flat. */
    @JvmStatic
    fun blockIDs(): Array<String> =
        KaloBlocks.blockKeys().map(CompatIds::fromKaloKey).toTypedArray()

    @JvmStatic
    fun isCustomBlock(block: Block): Boolean = KaloBlocks.kindAt(block) == KaloItemLookup.Kind.BLOCK

    @JvmStatic
    fun isCustomBlock(itemStack: ItemStack): Boolean =
        KaloBlocks.kindOfStack(itemStack) == KaloItemLookup.Kind.BLOCK

    @JvmStatic
    fun isCustomBlock(itemId: String): Boolean = KaloBlocks.kindOfId(itemId) == KaloItemLookup.Kind.BLOCK

    /** Places the block; nothing happens if the id is not a block. Call on the thread that owns the location. */
    @JvmStatic
    fun place(itemID: String, location: Location) {
        if (KaloBlocks.kindOfId(itemID) == KaloItemLookup.Kind.BLOCK) {
            KaloBlocks.place(itemID, location)
        }
    }

    @JvmStatic
    fun blockData(itemID: String): BlockData? =
        if (KaloBlocks.kindOfId(itemID) == KaloItemLookup.Kind.BLOCK) KaloBlocks.blockData(itemID) else null

    @JvmStatic
    fun remove(location: Location, player: Player?, forceDrop: Boolean): Boolean {
        if (KaloBlocks.kindAt(location.block) != KaloItemLookup.Kind.BLOCK) return false
        val drop = forceDrop || (player != null && player.gameMode != GameMode.CREATIVE)
        return KaloBlocks.remove(location, drop)
    }

    @JvmStatic
    fun remove(location: Location, player: Player?): Boolean = remove(location, player, false)

    @JvmStatic
    fun remove(location: Location): Boolean = remove(location, null, false)
}
