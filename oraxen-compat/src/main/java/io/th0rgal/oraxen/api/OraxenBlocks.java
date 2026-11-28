package io.th0rgal.oraxen.api;

import io.kalo.compat.CompatIds;
import io.kalo.compat.KaloBlocks;
import io.kalo.compat.KaloItemLookup;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Oraxen's block API, answered from Kalo's blocks. Only members whose signature is verified and
 * whose meaning maps onto a Kalo block exist. Not implemented, so a call fails with
 * {@code NoSuchMethodError}: the mechanic accessors and {@code getOraxenBlock} (Kalo has no Oraxen
 * mechanics), the note/string/chorus-specific ids and tests (Oraxen's kinds are not Kalo's carriers),
 * and the {@code Drop} overload of {@code remove}.
 *
 * <p>{@code remove} drops the item when {@code forceDrop} is set, or when a player who is not in
 * creative mode breaks it; with no player it does not drop. That rule is a choice made here:
 * Oraxen's own default has not been verified.</p>
 */
public class OraxenBlocks {

    /** Every Kalo block id (not furniture), flat. */
    public static Set<String> getBlockIDs() {
        Set<String> ids = new LinkedHashSet<>();
        for (String key : KaloBlocks.blockKeys()) {
            ids.add(CompatIds.fromKaloKey(key));
        }
        return ids;
    }

    public static boolean isOraxenBlock(Block block) {
        return KaloBlocks.kindAt(block) == KaloItemLookup.Kind.BLOCK;
    }

    public static boolean isOraxenBlock(String itemId) {
        return KaloBlocks.kindOfId(itemId) == KaloItemLookup.Kind.BLOCK;
    }

    /** Places the block; nothing happens if the id is not a block. Call on the thread that owns the location. */
    public static void place(String itemID, Location location) {
        if (isOraxenBlock(itemID)) {
            KaloBlocks.place(itemID, location);
        }
    }

    @Nullable
    public static BlockData getOraxenBlockData(String itemID) {
        return isOraxenBlock(itemID) ? KaloBlocks.blockData(itemID) : null;
    }

    public static boolean remove(Location location, @Nullable Player player) {
        return remove(location, player, false);
    }

    public static boolean remove(Location location, @Nullable Player player, boolean forceDrop) {
        if (KaloBlocks.kindAt(location.getBlock()) != KaloItemLookup.Kind.BLOCK) {
            return false;
        }
        boolean drop = forceDrop || (player != null && player.getGameMode() != GameMode.CREATIVE);
        return KaloBlocks.remove(location, drop);
    }
}
