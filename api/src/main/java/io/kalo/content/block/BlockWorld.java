package io.kalo.content.block;

import org.bukkit.block.data.BlockData;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Places, removes and identifies Kalo blocks (and furniture, which is block-backed) in the world
 * from code, without a player or a placement event.
 *
 * <p>Every method must be called on the thread that owns the target block: the main thread, or the
 * region thread on Folia. These calls do not fire {@code BlockPlaceEvent} or
 * {@code BlockBreakEvent}; callers that need protection plugins to see the change must fire their
 * own events.</p>
 */
public interface BlockWorld {

    /** The Kalo block or furniture drawn at this world block, native or virtual, or null if none. */
    @Nullable Block blockAt(@NotNull org.bukkit.block.Block worldBlock);

    /** Places the content at the target, replacing whatever is there. False if it cannot be placed. */
    boolean place(@NotNull Block content, @NotNull org.bukkit.block.Block target);

    /**
     * Removes the Kalo content at the target and leaves air. False if the target holds none.
     *
     * @param dropItem drop the content's item form where it stood
     */
    boolean remove(@NotNull org.bukkit.block.Block target, boolean dropItem);

    /** The vanilla state a native block is drawn with, or null for virtual blocks and unplaceable content. */
    @Nullable BlockData blockData(@NotNull Block content);
}
