package dev.lone.itemsadder.api;

import io.kalo.compat.KaloBlocks;
import io.kalo.compat.KaloItemLookup;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Subset of ItemsAdder's {@code CustomFurniture}, answered from Kalo's furniture. Kalo furniture is a
 * static, block-backed piece with no entity, so the entity side answers "none": {@code getEntity()}
 * is null (ItemsAdder documents null for "not spawned"), {@code byAlreadySpawned(Entity)} is null,
 * and {@code remove(Entity, boolean)} does nothing, because no entity is ever Kalo furniture.
 * Not implemented, so a call fails with {@code NoSuchMethodError}: precise placement, teleport,
 * replace, colour, light level and the deprecated armor stand accessor.
 */
public class CustomFurniture extends CustomStack {
    private final Location placedAt;

    private CustomFurniture(String namespacedId, @Nullable Location placedAt) {
        super(namespacedId);
        this.placedAt = placedAt;
    }

    public static Set<String> getNamespacedIdsInRegistry() {
        return new LinkedHashSet<>(KaloBlocks.furnitureKeys());
    }

    /** Kalo furniture has no entity, so no entity is furniture. */
    @Nullable
    public static CustomFurniture byAlreadySpawned(Entity entity) {
        return null;
    }

    @Nullable
    public static CustomFurniture byAlreadySpawned(Block block) {
        String key = KaloBlocks.keyAt(block);
        return key != null && KaloItemLookup.kindOf(key) == KaloItemLookup.Kind.FURNITURE
                ? new CustomFurniture(key, block.getLocation()) : null;
    }

    @Nullable
    public static CustomFurniture spawn(String namespacedId, Block blockLocation) {
        String key = KaloBlocks.resolveKey(namespacedId);
        if (key == null || KaloItemLookup.kindOf(key) != KaloItemLookup.Kind.FURNITURE
                || !KaloBlocks.place(key, blockLocation.getLocation())) {
            return null;
        }
        return new CustomFurniture(key, blockLocation.getLocation());
    }

    public static void remove(Entity entity, boolean dropItem) {
        // No Kalo furniture is an entity, so there is nothing to remove.
    }

    public void remove(boolean dropItem) {
        if (placedAt != null && getNamespacedID().equals(KaloBlocks.keyAt(placedAt.getBlock()))) {
            KaloBlocks.remove(placedAt, dropItem);
        }
    }

    @Nullable
    public Entity getEntity() {
        return null;
    }
}
