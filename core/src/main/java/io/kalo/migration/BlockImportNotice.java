package io.kalo.migration;

import org.jetbrains.annotations.NotNull;

/**
 * The warning that matters most in a block migration.
 *
 * <p>Oraxen, ItemsAdder and Kalo all store a placed custom block as nothing more than a
 * vanilla note block in some state, and each of them decides on its own which state means
 * which block. Converting a <em>config</em> therefore does not convert a <em>world</em>:
 * the definitions come across, but every block already placed still sits in the state the
 * old plugin assigned, which Kalo will read as a different block or as no block at all.</p>
 *
 * <p>This is the single most expensive thing to discover after the fact, so the importer
 * says it every time rather than burying it in documentation.</p>
 */
public final class BlockImportNotice {

    public static final String WORLD_NOT_MIGRATED =
            "Blocks already placed in the world are NOT migrated. Kalo assigns its own note "
                    + "block states, so existing blocks will read as the wrong block until they are "
                    + "replaced. Migrate a copy of the world, or re-place them, before going live.";

    private BlockImportNotice() {
    }

    /** Adds the notice once, however many blocks were imported. */
    public static void addTo(@NotNull ImportReport report, int blockCount) {
        if (blockCount > 0) {
            report.warn(WORLD_NOT_MIGRATED);
        }
    }
}
