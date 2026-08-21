package io.kalo.migration;

import io.kalo.platform.java.BlockStateAllocator;
import org.bukkit.Bukkit;
import org.bukkit.ChunkSnapshot;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Dry-run world scan that reports how many placed carrier states Kalo has allocated.
 *
 * <p>This does not modify the world — it is the safe preview. A destructive pass would
 * need a confirmed mapping from the importer, which the current importers do not produce.
 * Running this gives a server owner a count per world before they decide anything.</p>
 *
 * <p><b>A world that could not be scanned is reported as unscanned, never as zero.</b>
 * This is a migration tool: "nothing to migrate" and "I could not look" lead to opposite
 * decisions, and an earlier version collapsed the second into the first — on Folia, where
 * every cross-region block read throws, it told every owner their world was clean.</p>
 */
public final class WorldMigration {

    private static final Logger LOGGER = Logger.getLogger(WorldMigration.class.getName());
    private static final long SNAPSHOT_TIMEOUT_SECONDS = 30L;

    private WorldMigration() {
    }

    /**
     * Scans every loaded chunk of every world.
     *
     * <p>Chunk snapshots are taken on the thread that owns the chunk — the region scheduler
     * makes that correct on Folia and harmless on Paper — and the actual counting happens
     * off the server thread. Scanning inline instead meant roughly 98,000 block reads per
     * chunk on the thread handling the command, which stalls the server for minutes on any
     * world worth scanning.</p>
     */
    public static @NotNull CompletableFuture<Report> dryRun(@NotNull JavaPlugin plugin,
                                                            @NotNull BlockStateAllocator allocator) {
        Allocated allocated = allocatedStates(allocator);
        List<CompletableFuture<WorldReport>> worlds = new ArrayList<>();
        for (World world : Bukkit.getWorlds()) {
            worlds.add(scanWorld(plugin, world, allocated));
        }

        return CompletableFuture.allOf(worlds.toArray(new CompletableFuture[0]))
                .thenApply(ignored -> {
                    Map<String, WorldReport> byWorld = new LinkedHashMap<>();
                    for (CompletableFuture<WorldReport> world : worlds) {
                        WorldReport report = world.join();
                        byWorld.put(report.world(), report);
                    }
                    return new Report(byWorld, allocated.unreadable());
                });
    }

    private static @NotNull CompletableFuture<WorldReport> scanWorld(@NotNull JavaPlugin plugin,
                                                                    @NotNull World world,
                                                                    @NotNull Allocated allocated) {
        List<CompletableFuture<ChunkSnapshot>> snapshots = new ArrayList<>();
        for (org.bukkit.Chunk chunk : world.getLoadedChunks()) {
            int chunkX = chunk.getX();
            int chunkZ = chunk.getZ();
            CompletableFuture<ChunkSnapshot> pending = new CompletableFuture<>();
            snapshots.add(pending);
            try {
                Bukkit.getRegionScheduler().execute(plugin, world, chunkX, chunkZ, () -> {
                    try {
                        // Re-check: a chunk can unload between listing and this task running.
                        pending.complete(world.isChunkLoaded(chunkX, chunkZ)
                                ? world.getChunkAt(chunkX, chunkZ).getChunkSnapshot(false, false, false)
                                : null);
                    } catch (Throwable t) {
                        pending.completeExceptionally(t);
                    }
                });
            } catch (Throwable t) {
                pending.completeExceptionally(t);
            }
        }

        int minY = world.getMinHeight();
        int maxY = world.getMaxHeight();
        return CompletableFuture.allOf(snapshots.toArray(new CompletableFuture[0]))
                // A scheduler that never runs a task would otherwise leave the command
                // waiting forever with nothing to show. Time out and report the chunks as
                // unreachable, which is what they are.
                .orTimeout(SNAPSHOT_TIMEOUT_SECONDS, java.util.concurrent.TimeUnit.SECONDS)
                .handleAsync((ignored, error) -> count(world.getName(), snapshots, allocated, minY, maxY));
    }

    private static @NotNull WorldReport count(@NotNull String world,
                                              @NotNull List<CompletableFuture<ChunkSnapshot>> snapshots,
                                              @NotNull Allocated allocated,
                                              int minY,
                                              int maxY) {
        int blocks = 0;
        int scanned = 0;
        int unreachable = 0;
        String firstFailure = null;

        for (CompletableFuture<ChunkSnapshot> pending : snapshots) {
            ChunkSnapshot snapshot;
            try {
                snapshot = pending.getNow(null);
                if (snapshot == null) {
                    // Either unloaded before the task ran, or the task never ran at all.
                    unreachable++;
                    continue;
                }
            } catch (Throwable t) {
                unreachable++;
                if (firstFailure == null) {
                    firstFailure = String.valueOf(t.getMessage());
                    LOGGER.log(Level.WARNING, "Could not snapshot a chunk of " + world, t);
                }
                continue;
            }

            scanned++;
            for (int x = 0; x < 16; x++) {
                for (int z = 0; z < 16; z++) {
                    for (int y = minY; y < maxY; y++) {
                        // Comparing the full block-data string for every block allocates one
                        // string per block. Almost nothing is a carrier, so reject on the
                        // material first and only pay for the rest on a real candidate.
                        if (!allocated.materials().contains(snapshot.getBlockType(x, y, z))) {
                            continue;
                        }
                        BlockData data = snapshot.getBlockData(x, y, z);
                        if (allocated.states().contains(data.getAsString(false))) {
                            blocks++;
                        }
                    }
                }
            }
        }
        return new WorldReport(world, blocks, scanned, unreachable, firstFailure);
    }

    /**
     * Turns the allocator's assignments into the exact strings a placed block compares as.
     *
     * <p>An assignment that Bukkit refuses to parse is counted, not swallowed: it means the
     * scan is blind to that content, and a migration preview that quietly narrows what it
     * looks for is worse than one that admits it.</p>
     */
    private static @NotNull Allocated allocatedStates(@NotNull BlockStateAllocator allocator) {
        Set<String> states = new HashSet<>();
        Set<Material> materials = EnumSet.noneOf(Material.class);
        int unreadable = 0;

        for (Map.Entry<String, BlockStateAllocator.Assignment> entry : allocator.assignments().entrySet()) {
            String identifier = entry.getValue().javaIdentifier();
            try {
                BlockData data = Bukkit.createBlockData(identifier);
                states.add(data.getAsString(false));
                materials.add(data.getMaterial());
            } catch (RuntimeException e) {
                unreadable++;
                LOGGER.log(Level.WARNING,
                        "Could not read the block state allocated to " + entry.getKey()
                                + " (" + identifier + "); it will not be counted", e);
            }
        }
        return new Allocated(states, materials, unreadable);
    }

    private record Allocated(@NotNull Set<String> states,
                             @NotNull Set<Material> materials,
                             int unreadable) {
    }

    /** What one world's scan found, including what it could not reach. */
    public record WorldReport(@NotNull String world,
                              int blocks,
                              int chunksScanned,
                              int chunksUnreachable,
                              @org.jetbrains.annotations.Nullable String failure) {

        public boolean complete() {
            return chunksUnreachable == 0;
        }
    }

    public record Report(@NotNull Map<String, WorldReport> worlds, int unreadableAssignments) {
        public Report {
            worlds = Map.copyOf(worlds);
        }

        public int total() {
            return worlds.values().stream().mapToInt(WorldReport::blocks).sum();
        }

        /** False when any world was partly or wholly unreadable, so zero cannot be trusted. */
        public boolean complete() {
            return unreadableAssignments == 0 && worlds.values().stream().allMatch(WorldReport::complete);
        }
    }
}
