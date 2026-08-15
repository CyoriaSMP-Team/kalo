package io.kalo.migration;

import org.jetbrains.annotations.NotNull;

/**
 * What is lost when entity-backed furniture becomes block-backed furniture.
 *
 * <p>Oraxen and ItemsAdder both build furniture out of entities — an item display or item
 * frame, with barrier blocks standing in for a hitbox. That buys free rotation, hitboxes
 * of any shape, seats, and models larger than one block.</p>
 *
 * <p>Kalo's furniture is a block borrowing a note block state. It is far cheaper at
 * runtime and survives a restart with no entity bookkeeping, but it occupies exactly one
 * block and faces exactly one way.</p>
 *
 * <p>So a furniture import is a genuine downgrade in capability, not a format change.
 * Producing something that merely looks converted would be the worst outcome: the server
 * owner sees chairs in their config, and their players see chairs that cannot be sat on
 * and will not turn.</p>
 */
public final class FurnitureImportNotice {

    public static final String STATIC_ONLY =
            "Kalo furniture is a single static block, not an entity. Rotation, custom hitboxes, "
                    + "seats and multi-block models do NOT carry over — the shape and name come "
                    + "across, the behaviour does not. Check every piece before going live.";

    private FurnitureImportNotice() {
    }

    public static void addTo(@NotNull ImportReport report, int furnitureCount) {
        if (furnitureCount > 0) {
            report.warn(STATIC_ONLY);
        }
    }
}
