package io.kalo.migration;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The scan's honesty rules. A migration preview that cannot distinguish "nothing to
 * migrate" from "I could not look" sends owners in opposite directions on the same output,
 * and an earlier version collapsed the second into the first — on Folia, where every
 * cross-region block read throws, it declared every world clean.
 */
class WorldMigrationReportTest {

    private static WorldMigration.WorldReport world(String name, int blocks, int scanned, int unreachable) {
        return new WorldMigration.WorldReport(name, blocks, scanned, unreachable, null);
    }

    @Test
    void aWorldWithUnreachableChunksIsNotComplete() {
        assertTrue(world("overworld", 0, 10, 0).complete());
        assertFalse(world("overworld", 0, 10, 1).complete());
    }

    @Test
    void aReportIsOnlyCompleteWhenEveryWorldIs() {
        WorldMigration.Report clean = new WorldMigration.Report(
                Map.of("a", world("a", 3, 5, 0), "b", world("b", 0, 5, 0)), 0);
        assertTrue(clean.complete());
        assertEquals(3, clean.total());

        WorldMigration.Report partial = new WorldMigration.Report(
                Map.of("a", world("a", 3, 5, 0), "b", world("b", 0, 2, 7)), 0);
        assertFalse(partial.complete());
    }

    /**
     * A state the server cannot parse is content the scan is blind to, so zero found is no
     * longer evidence of zero present.
     */
    @Test
    void anUnparsableAllocationMakesTheWholeReportIncomplete() {
        WorldMigration.Report report = new WorldMigration.Report(
                Map.of("a", world("a", 0, 5, 0)), 1);
        assertFalse(report.complete());
        assertEquals(0, report.total());
    }
}
