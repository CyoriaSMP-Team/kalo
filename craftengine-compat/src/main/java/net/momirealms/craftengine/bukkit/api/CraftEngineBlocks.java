package net.momirealms.craftengine.bukkit.api;

import io.kalo.compat.KaloBlocks;
import io.kalo.compat.KaloItemLookup;
import org.bukkit.block.Block;
import org.jetbrains.annotations.NotNull;

/**
 * The part of CraftEngine's block API that uses only Bukkit types, answered from Kalo. Placing, block
 * states, definitions and the player/loot overloads of {@code remove} take or return CraftEngine's own
 * types and are not implemented: they fail with {@code NoSuchMethodError}. Furniture is not a block
 * here, as in CraftEngine.
 *
 * <p>{@code remove} leaves air without dropping the item and fires no block events; what CraftEngine's
 * own defaults are has not been verified. Call on the thread that owns the block.</p>
 */
public final class CraftEngineBlocks {
    private CraftEngineBlocks() {
    }

    public static boolean isCustomBlock(@NotNull Block block) {
        return KaloBlocks.kindAt(block) == KaloItemLookup.Kind.BLOCK;
    }

    public static boolean remove(@NotNull Block block) {
        return remove(block, false);
    }

    /** Kalo blocks are not moved by pistons, so the flag does not change anything. */
    public static boolean remove(@NotNull Block block, boolean movedByPiston) {
        return isCustomBlock(block) && KaloBlocks.remove(block.getLocation(), false);
    }
}
