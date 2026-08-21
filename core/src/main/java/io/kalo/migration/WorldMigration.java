package io.kalo.migration;

import io.kalo.platform.java.BlockStateAllocator;
import io.kalo.utils.Constants;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Dry-run world migration that reports how many placed NoteBlock/Tripwire
 * states would change if a previous pack's allocation were remapped to Kalo's.
 *
 * <p>This does not modify the world yet — it is the safe preview that the
 * roadmap calls "placed-world migration pending". A destructive pass would
 * require a confirmed mapping file from the importer, which the current
 * importers do not yet produce. Running this gives a server owner a count
 * per world before they decide to replace blocks by hand or via a later
 * migrator that does have that mapping.</p>
 */
public final class WorldMigration {

    private static final Logger LOGGER = Logger.getLogger(WorldMigration.class.getName());

    private WorldMigration() {
    }

    public static @NotNull Report dryRun(@NotNull BlockStateAllocator allocator) {
        // Pre-build allocated data strings for O(1) lookup
        java.util.Set<String> allocated = new java.util.HashSet<>();
        try {
            for (var entry : allocator.assignments().entrySet()) {
                var carrier = entry.getValue().carrier();
                String expected = carrier.vanillaBlock() + "[" + carrier.variantKey(entry.getValue().state()) + "]";
                try {
                    BlockData expectedData = Bukkit.createBlockData(expected);
                    allocated.add(expectedData.getAsString(false));
                } catch (Exception ignored) {
                }
            }
        } catch (Exception ignored) {
        }

        Map<String, Integer> perWorld = new HashMap<>();
        int total = 0;

        for (World world : Bukkit.getWorlds()) {
            int count = 0;
            try {
                for (var chunk : world.getLoadedChunks()) {
                    int minY = world.getMinHeight();
                    int maxY = world.getMaxHeight();
                    for (int x = 0; x < 16; x++) {
                        for (int z = 0; z < 16; z++) {
                            for (int y = minY; y < maxY; y++) {
                                Block block = chunk.getBlock(x, y, z);
                                if (allocated.contains(block.getBlockData().getAsString(false))) {
                                    count++;
                                }
                            }
                        }
                    }
                }
            } catch (Throwable t) {
                LOGGER.log(Level.WARNING, "World migration dry-run failed for world " + world.getName(), t);
            }
            perWorld.put(world.getName(), count);
            total += count;
        }
        return new Report(perWorld, total);
    }

    public record Report(@NotNull Map<String, Integer> perWorld, int total) {
        public Report {
            perWorld = Map.copyOf(perWorld);
        }
    }
}
