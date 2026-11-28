package net.momirealms.craftengine.bukkit.api;

import org.bukkit.entity.Entity;
import org.jetbrains.annotations.NotNull;

/**
 * The entity tests of CraftEngine's furniture API. Kalo furniture is a static, block-backed piece
 * with no entity, so no entity is ever furniture, a collider or a seat, and {@code remove(Entity)}
 * never removes anything. Everything that returns or takes CraftEngine's furniture types
 * ({@code place}, {@code byId}, {@code rayTrace}, {@code loadedFurniture}, the {@code getLoaded...}
 * lookups) is not implemented and fails with {@code NoSuchMethodError}.
 */
public final class CraftEngineFurniture {
    private CraftEngineFurniture() {
    }

    public static boolean isFurniture(@NotNull Entity entity) {
        return false;
    }

    public static boolean isCollisionEntity(@NotNull Entity entity) {
        return false;
    }

    public static boolean isSeat(@NotNull Entity entity) {
        return false;
    }

    public static boolean remove(@NotNull Entity entity) {
        return false;
    }
}
