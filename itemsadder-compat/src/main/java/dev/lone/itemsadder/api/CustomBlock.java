package dev.lone.itemsadder.api;

import io.kalo.compat.KaloBlocks;
import io.kalo.compat.KaloItemLookup;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Subset of ItemsAdder's {@code CustomBlock}, answered from Kalo's blocks. Placing and removing do
 * not fire block events, and must run on the thread that owns the location. Not implemented, so a
 * call fails with {@code NoSuchMethodError}: break/place effects and sounds, loot, and light levels
 * (Kalo has no equivalent), and the nested {@code Advanced} class.
 */
public class CustomBlock extends CustomStack {
    private Location placedAt;

    private CustomBlock(String namespacedId, @Nullable Location placedAt) {
        super(namespacedId);
        this.placedAt = placedAt;
    }

    private static @Nullable String blockKey(String namespacedId) {
        String key = KaloBlocks.resolveKey(namespacedId);
        return key != null && KaloBlocks.kindOfId(key) == KaloItemLookup.Kind.BLOCK ? key : null;
    }

    public static Set<String> getNamespacedIdsInRegistry() {
        return new LinkedHashSet<>(KaloBlocks.blockKeys());
    }

    public static boolean isInRegistry(String namespacedId) {
        return blockKey(namespacedId) != null;
    }

    @Nullable
    public static CustomBlock getInstance(String namespacedID) {
        String key = blockKey(namespacedID);
        return key == null ? null : new CustomBlock(key, null);
    }

    @Nullable
    public static CustomBlock byItemStack(ItemStack itemStack) {
        return KaloBlocks.kindOfStack(itemStack) == KaloItemLookup.Kind.BLOCK
                ? new CustomBlock(KaloItemLookup.keyOf(itemStack), null) : null;
    }

    @Nullable
    public static CustomBlock place(String namespacedId, Location location) {
        String key = blockKey(namespacedId);
        if (key == null || !KaloBlocks.place(key, location)) {
            return null;
        }
        return new CustomBlock(key, location.getBlock().getLocation());
    }

    @Nullable
    public static CustomBlock byAlreadyPlaced(Block block) {
        String key = KaloBlocks.keyAt(block);
        return key != null && KaloItemLookup.kindOf(key) == KaloItemLookup.Kind.BLOCK
                ? new CustomBlock(key, block.getLocation()) : null;
    }

    /** Removes the block without dropping its item; ItemsAdder's own default is not verified. */
    public static boolean remove(Location location) {
        return KaloBlocks.kindAt(location.getBlock()) == KaloItemLookup.Kind.BLOCK
                && KaloBlocks.remove(location, false);
    }

    @Nullable
    public static BlockData getBaseBlockData(String namespacedID) {
        String key = blockKey(namespacedID);
        return key == null ? null : KaloBlocks.blockData(key);
    }

    @Nullable
    public BlockData getBaseBlockData() {
        return KaloBlocks.blockData(getNamespacedID());
    }

    @Nullable
    public Block getBlock() {
        return placedAt == null ? null : placedAt.getBlock();
    }

    public boolean isPlaced() {
        Block block = getBlock();
        return block != null && getNamespacedID().equals(KaloBlocks.keyAt(block));
    }

    public CustomBlock place(Location location) {
        if (!KaloBlocks.place(getNamespacedID(), location)) {
            return null;
        }
        this.placedAt = location.getBlock().getLocation();
        return this;
    }

    public boolean remove() {
        return isPlaced() && KaloBlocks.remove(placedAt, false);
    }
}
