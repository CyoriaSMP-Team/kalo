package io.kalo.migration;

import org.jetbrains.annotations.NotNull;

/**
 * The warning that matters most in a block migration.
 *
 * <p>Oraxen and ItemsAdder store a placed custom block in their own carrier/entity format.
 * Converting a <em>config</em> therefore does not convert a <em>world</em>: the definitions
 * come across, but every block already placed still belongs to the old plugin and must be
 * reviewed or replaced before the old plugin is removed.</p>
 *
 * <p>This is the single most expensive thing to discover after the fact, so the importer
 * says it every time rather than burying it in documentation.</p>
 */
public final class BlockImportNotice {

    public static final String WORLD_NOT_MIGRATED =
            "Blocks already placed in the world are NOT migrated. Kalo cannot safely infer "
                    + "another plugin's carrier/entity records, so review or replace them before "
                    + "removing the old plugin. Migrate a copy of the world, or re-place them, "
                    + "before going live.";

    private BlockImportNotice() {
    }

    /** Adds the notice once, however many blocks were imported. */
    public static void addTo(@NotNull ImportReport report, int blockCount) {
        if (blockCount > 0) {
            report.warn(WORLD_NOT_MIGRATED);
        }
    }
}
