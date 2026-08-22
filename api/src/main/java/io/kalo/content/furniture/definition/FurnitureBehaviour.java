package io.kalo.content.furniture.definition;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.util.List;
import java.util.Map;

/**
 * Platform-neutral furniture mechanics.
 *
 * <p>Extends basic block behaviour with furniture-specific properties like rotation,
 * seating, hitboxes, storage containers, and interactions.</p>
 *
 * @param rotatable          whether the furniture can be rotated by the player
 * @param restrictedRotation  null for full rotation, "strict" for 8 facings, "very_strict" for 4
 * @param seat               seating configuration, null if not sit-able
 * @param hitbox             barrier-based hitbox offsets, empty means single block at 0,0,0
 * @param storage            storage container configuration, null if no storage
 * @param jukebox            jukebox configuration, null if not a jukebox
 * @param waterloggable      whether the furniture can be waterlogged
 * @param light              light emission level (0-15), 0 means no light
 * @param limitedPlacing     placement restrictions
 */public record FurnitureBehaviour(
        float hardness,
        boolean requiresTool,
        boolean rotatable,
        @Nullable String restrictedRotation,
        @Nullable Seat seat,
        @NotNull @Unmodifiable List<double[]> hitbox,
        @Nullable Storage storage,
        @Nullable Jukebox jukebox,
        boolean waterloggable,
        int light,
        @Nullable LimitedPlacing limitedPlacing
    ) {
    public FurnitureBehaviour {
        hitbox = List.copyOf(hitbox);
        if (light < 0 || light > 15) {
            throw new IllegalArgumentException("light must be 0-15, got " + light);
        }
    }

    public boolean unbreakable() {
        return hardness == -1f;
    }

    /**
     * Seating configuration.
     *
     * @param height  seat height above the block bottom (0.0-1.0)
     * @param offset  [x, y, z] offset from block center
     * @param direction player facing direction when seated
     */
    public record Seat(
            double height,
            @NotNull @Unmodifiable List<Double> offset,
            @Nullable String direction
    ) {
        public Seat {
            offset = List.copyOf(offset);
        }

        public static @NotNull Seat defaults() {
            return new Seat(0.5, List.of(0.0, 0.5, 0.0), null);
        }
    }

    /**
     * Storage container configuration.
     *
     * @param type      container type: STORAGE, PERSONAL, ENDERCHEST, DISPOSAL
     * @param rows      number of inventory rows (1-6)
     * @param title     display title, null for default
     * @param openSound sound when opening, null for default
     * @param closeSound sound when closing, null for default
     */
    public record Storage(
            @NotNull String type,
            int rows,
            @Nullable String title,
            @Nullable String openSound,
            @Nullable String closeSound
    ) {
        public Storage {
            if (rows < 1 || rows > 6) {
                throw new IllegalArgumentException("rows must be 1-6, got " + rows);
            }
        }

        public static @NotNull Storage defaults() {
            return new Storage("STORAGE", 6, null, null, null);
        }
    }

    /**
     * Jukebox configuration.
     *
     * @param volume     playback volume (0.0-1.0)
     * @param pitch      playback pitch (0.5-2.0)
     * @param permission null for anyone, or a permission node
     */
    public record Jukebox(
            double volume,
            double pitch,
            @Nullable String permission
    ) {
        public static @NotNull Jukebox defaults() {
            return new Jukebox(1.0, 1.0, null);
        }
    }

    /**
     * Placement restrictions.
     *
     * @param roof    whether placement on ceiling is allowed
     * @param floor   whether placement on floor is allowed
     * @param wall    whether placement on walls is allowed
     * @param type    ALLOW (only listed blocks) or DENY (all except listed blocks)
     * @param blockTypes  list of material names
     * @param blockTags   list of block tags
     * @param nexoBlocks  list of Kalo content keys
     */
    public record LimitedPlacing(
            boolean roof,
            boolean floor,
            boolean wall,
            @Nullable String type,
            @NotNull @Unmodifiable List<String> blockTypes,
            @NotNull @Unmodifiable List<String> blockTags,
            @NotNull @Unmodifiable List<String> nexoBlocks
    ) {
        public LimitedPlacing {
            blockTypes = List.copyOf(blockTypes);
            blockTags = List.copyOf(blockTags);
            nexoBlocks = List.copyOf(nexoBlocks);
        }
    }

    public static @NotNull FurnitureBehaviour defaults() {
        return new FurnitureBehaviour(
                1.5f, true, false, null, null, List.of(), null, null, false, 0, null);
    }
}
