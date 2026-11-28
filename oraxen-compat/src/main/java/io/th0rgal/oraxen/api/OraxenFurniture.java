package io.th0rgal.oraxen.api;

import io.kalo.compat.CompatIds;
import io.kalo.compat.KaloBlocks;
import io.kalo.compat.KaloItemLookup;
import org.bukkit.block.Block;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Oraxen's furniture API, answered from Kalo's furniture. Kalo furniture is a static, block-backed
 * piece, not an entity, so everything that returns or takes a furniture entity ({@code place},
 * {@code remove}, {@code isFurniture(Entity)}, the base/interaction/orphan entity tests,
 * {@code getFurnitureMechanic}, {@code updateFurniture}) has nothing to work with and is not
 * implemented: a call fails with {@code NoSuchMethodError}.
 */
public class OraxenFurniture {

    public static Set<String> getFurnitureIDs() {
        Set<String> ids = new LinkedHashSet<>();
        for (String key : KaloBlocks.furnitureKeys()) {
            ids.add(CompatIds.fromKaloKey(key));
        }
        return ids;
    }

    public static boolean isFurniture(Block block) {
        return KaloBlocks.kindAt(block) == KaloItemLookup.Kind.FURNITURE;
    }

    public static boolean isFurniture(String itemID) {
        return KaloBlocks.kindOfId(itemID) == KaloItemLookup.Kind.FURNITURE;
    }
}
