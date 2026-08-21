package io.kalo.platform.java;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Compact persistent index for Kalo virtual blocks.
 *
 * <p>The world is partitioned by chunk and each chunk stores integer palette ids instead
 * of repeating a namespaced content key for every block. The index, not an entity, is the
 * source of truth. Render entities can therefore be non-persistent and recreated when a
 * chunk is loaded without turning every placed custom block into permanent entity state.</p>
 *
 * <p>Mutations are O(1). Persistence is debounced and written atomically on a dedicated
 * virtual thread so placing a block never waits for disk IO. {@link #flush()} is still
 * called on plugin shutdown so the last mutations are durable.</p>
 */
public final class VirtualBlockStore implements AutoCloseable {
    private static final Logger LOGGER = Logger.getLogger(VirtualBlockStore.class.getName());
    private static final int MAGIC = 0x4B564231; // KVB1
    private static final int FORMAT_VERSION = 1;
    private static final long SAVE_DEBOUNCE_MILLIS = 250L;

    private final ConcurrentHashMap<UUID, ConcurrentHashMap<Long, ChunkData>> worlds =
            new ConcurrentHashMap<>();
    private final ScheduledExecutorService writer = Executors.newSingleThreadScheduledExecutor(
            Thread.ofVirtual().name("kalo-virtual-block-save-", 0).factory());
    private final AtomicBoolean dirty = new AtomicBoolean();
    private final AtomicBoolean saveScheduled = new AtomicBoolean();

    private volatile Path attachedFile;
    private volatile boolean closed;

    public void attach(@NotNull Path file) {
        attachedFile = file;
    }

    /** Loads a complete snapshot, replacing any data already in memory. */
    public synchronized void load(@NotNull Path file) throws IOException {
        if (!Files.exists(file)) {
            worlds.clear();
            attachedFile = file;
            return;
        }

        ConcurrentHashMap<UUID, ConcurrentHashMap<Long, ChunkData>> loaded = new ConcurrentHashMap<>();
        try (DataInputStream input = new DataInputStream(new BufferedInputStream(Files.newInputStream(file)))) {
            int magic = input.readInt();
            if (magic != MAGIC) {
                throw new IOException("Not a Kalo virtual-block index: " + file);
            }
            int version = input.readInt();
            if (version != FORMAT_VERSION) {
                throw new IOException("Unsupported virtual-block index version " + version);
            }

            int worldCount = checkedCount(input.readInt(), "world");
            for (int worldIndex = 0; worldIndex < worldCount; worldIndex++) {
                UUID worldId = new UUID(input.readLong(), input.readLong());
                int chunkCount = checkedCount(input.readInt(), "chunk");
                ConcurrentHashMap<Long, ChunkData> chunks = new ConcurrentHashMap<>(Math.max(16, chunkCount * 2));

                for (int chunkIndex = 0; chunkIndex < chunkCount; chunkIndex++) {
                    long chunkKey = input.readLong();
                    int paletteSize = checkedCount(input.readInt(), "palette");
                    List<String> palette = new ArrayList<>(paletteSize);
                    for (int i = 0; i < paletteSize; i++) {
                        palette.add(input.readUTF());
                    }

                    int positionCount = checkedCount(input.readInt(), "position");
                    ChunkData chunk = new ChunkData();
                    for (int i = 0; i < positionCount; i++) {
                        long localPosition = input.readLong();
                        int paletteId = input.readInt();
                        if (paletteId < 0 || paletteId >= palette.size()) {
                            throw new IOException("Invalid virtual-block palette id " + paletteId);
                        }
                        chunk.put(localPosition, palette.get(paletteId));
                    }
                    if (!chunk.isEmpty()) {
                        chunks.put(chunkKey, chunk);
                    }
                }
                if (!chunks.isEmpty()) {
                    loaded.put(worldId, chunks);
                }
            }

            // A valid file must end exactly after the last record. Trailing bytes often
            // mean two versions were concatenated by a broken writer.
            if (input.read() != -1) {
                throw new IOException("Trailing data in virtual-block index " + file);
            }
        } catch (EOFException e) {
            throw new IOException("Truncated virtual-block index " + file, e);
        }

        worlds.clear();
        worlds.putAll(loaded);
        attachedFile = file;
        dirty.set(false);
    }

    public void put(@NotNull UUID worldId, int x, int y, int z, @NotNull String contentId) {
        if (closed) {
            throw new IllegalStateException("VirtualBlockStore is closed");
        }
        long chunkKey = chunkKey(x >> 4, z >> 4);
        long localPosition = localPosition(x, y, z);
        ChunkData chunk = worlds
                .computeIfAbsent(worldId, ignored -> new ConcurrentHashMap<>())
                .computeIfAbsent(chunkKey, ignored -> new ChunkData());
        if (chunk.put(localPosition, contentId)) {
            markDirty();
        }
    }

    public @Nullable String get(@NotNull UUID worldId, int x, int y, int z) {
        Map<Long, ChunkData> chunks = worlds.get(worldId);
        if (chunks == null) {
            return null;
        }
        ChunkData chunk = chunks.get(chunkKey(x >> 4, z >> 4));
        return chunk != null ? chunk.get(localPosition(x, y, z)) : null;
    }

    public @Nullable String remove(@NotNull UUID worldId, int x, int y, int z) {
        ConcurrentHashMap<Long, ChunkData> chunks = worlds.get(worldId);
        if (chunks == null) {
            return null;
        }

        long chunkKey = chunkKey(x >> 4, z >> 4);
        ChunkData chunk = chunks.get(chunkKey);
        if (chunk == null) {
            return null;
        }

        String removed = chunk.remove(localPosition(x, y, z));
        if (removed != null) {
            if (chunk.isEmpty()) {
                chunks.remove(chunkKey, chunk);
                if (chunks.isEmpty()) {
                    worlds.remove(worldId, chunks);
                }
            }
            markDirty();
        }
        return removed;
    }

    /** Returns one immutable chunk view without walking any other chunks or worlds. */
    public @NotNull List<Entry> entries(@NotNull UUID worldId, int chunkX, int chunkZ) {
        Map<Long, ChunkData> chunks = worlds.get(worldId);
        if (chunks == null) {
            return List.of();
        }
        ChunkData chunk = chunks.get(chunkKey(chunkX, chunkZ));
        if (chunk == null) {
            return List.of();
        }

        List<LocalEntry> local = chunk.entries();
        List<Entry> result = new ArrayList<>(local.size());
        int originX = chunkX << 4;
        int originZ = chunkZ << 4;
        for (LocalEntry entry : local) {
            long packed = entry.position();
            result.add(new Entry(
                    originX + localX(packed),
                    localY(packed),
                    originZ + localZ(packed),
                    entry.contentId()));
        }
        return List.copyOf(result);
    }

    public int size() {
        int count = 0;
        for (Map<Long, ChunkData> chunks : worlds.values()) {
            for (ChunkData chunk : chunks.values()) {
                count += chunk.size();
            }
        }
        return count;
    }

    public int chunkCount() {
        int count = 0;
        for (Map<Long, ChunkData> chunks : worlds.values()) {
            count += chunks.size();
        }
        return count;
    }

    /** Writes the latest state immediately on the caller thread. */
    public synchronized void flush() throws IOException {
        Path file = attachedFile;
        if (file == null || !dirty.getAndSet(false)) {
            return;
        }
        writeSnapshot(file);
    }

    private void markDirty() {
        dirty.set(true);
        Path file = attachedFile;
        if (file == null || closed || !saveScheduled.compareAndSet(false, true)) {
            return;
        }
        writer.schedule(this::drainAsyncSave, SAVE_DEBOUNCE_MILLIS, TimeUnit.MILLISECONDS);
    }

    private void drainAsyncSave() {
        try {
            Path file = attachedFile;
            if (file != null && dirty.getAndSet(false)) {
                writeSnapshot(file);
            }
        } catch (Exception e) {
            dirty.set(true);
            LOGGER.log(Level.SEVERE, "Could not persist virtual blocks", e);
        } finally {
            saveScheduled.set(false);
            if (dirty.get() && !closed) {
                markDirty();
            }
        }
    }

    /**
     * Snapshot and write are one atomic step, and {@link #flush()} shares this monitor.
     *
     * <p>Without that, the debounced writer and a shutdown flush overlap: the writer takes
     * its snapshot, a player places a block, flush snapshots the newer state and moves it
     * into place, and then the writer's older snapshot lands on top. Both moves are atomic,
     * so nothing is corrupt — the last blocks placed before shutdown simply disappear.</p>
     *
     * <p>Taking the snapshot inside the lock is what makes it hold: whichever writer runs
     * second necessarily sees state at least as new as the one that ran first.</p>
     */
    // Package-private so VirtualBlockStoreTest can prove the two writers serialise.
    synchronized void writeSnapshot(@NotNull Path file) throws IOException {
        List<WorldSnapshot> snapshot = snapshot();
        Path parent = file.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Path temp = Files.createTempFile(parent, ".virtual-blocks-", ".tmp");
        try {
            try (DataOutputStream output = new DataOutputStream(new BufferedOutputStream(Files.newOutputStream(temp)))) {
                output.writeInt(MAGIC);
                output.writeInt(FORMAT_VERSION);
                output.writeInt(snapshot.size());
                for (WorldSnapshot world : snapshot) {
                    output.writeLong(world.worldId().getMostSignificantBits());
                    output.writeLong(world.worldId().getLeastSignificantBits());
                    output.writeInt(world.chunks().size());
                    for (ChunkSnapshot chunk : world.chunks()) {
                        output.writeLong(chunk.chunkKey());
                        output.writeInt(chunk.palette().size());
                        for (String contentId : chunk.palette()) {
                            output.writeUTF(contentId);
                        }
                        output.writeInt(chunk.positions().size());
                        for (PositionSnapshot position : chunk.positions()) {
                            output.writeLong(position.position());
                            output.writeInt(position.paletteId());
                        }
                    }
                }
            }
            Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (java.nio.file.AtomicMoveNotSupportedException ignored) {
            Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException | RuntimeException e) {
            Files.deleteIfExists(temp);
            throw e;
        }
    }

    private @NotNull List<WorldSnapshot> snapshot() {
        List<WorldSnapshot> result = new ArrayList<>();
        List<UUID> worldIds = new ArrayList<>(worlds.keySet());
        worldIds.sort(Comparator.comparing(UUID::toString));

        for (UUID worldId : worldIds) {
            Map<Long, ChunkData> chunks = worlds.get(worldId);
            if (chunks == null || chunks.isEmpty()) {
                continue;
            }
            List<Long> chunkKeys = new ArrayList<>(chunks.keySet());
            chunkKeys.sort(Long::compare);
            List<ChunkSnapshot> chunkSnapshots = new ArrayList<>(chunkKeys.size());
            for (long key : chunkKeys) {
                ChunkData chunk = chunks.get(key);
                if (chunk != null && !chunk.isEmpty()) {
                    chunkSnapshots.add(chunk.snapshot(key));
                }
            }
            if (!chunkSnapshots.isEmpty()) {
                result.add(new WorldSnapshot(worldId, List.copyOf(chunkSnapshots)));
            }
        }
        return List.copyOf(result);
    }

    @Override
    public void close() throws IOException {
        closed = true;
        flush();
        writer.shutdown();
        try {
            if (!writer.awaitTermination(2, TimeUnit.SECONDS)) {
                writer.shutdownNow();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            writer.shutdownNow();
        }
    }

    private static int checkedCount(int count, String what) throws IOException {
        if (count < 0 || count > 100_000_000) {
            throw new IOException("Invalid " + what + " count " + count);
        }
        return count;
    }

    static long chunkKey(int chunkX, int chunkZ) {
        return ((long) chunkX << 32) ^ (chunkZ & 0xffff_ffffL);
    }

    static long localPosition(int x, int y, int z) {
        return ((long) y << 8) | ((long) (z & 15) << 4) | (x & 15L);
    }

    static int localX(long position) {
        return (int) (position & 15L);
    }

    static int localZ(long position) {
        return (int) ((position >>> 4) & 15L);
    }

    static int localY(long position) {
        return (int) (position >> 8);
    }

    public record Entry(int x, int y, int z, @NotNull String contentId) {
    }

    private record LocalEntry(long position, @NotNull String contentId) {
    }

    private record PositionSnapshot(long position, int paletteId) {
    }

    private record ChunkSnapshot(long chunkKey,
                                 @NotNull List<String> palette,
                                 @NotNull List<PositionSnapshot> positions) {
    }

    private record WorldSnapshot(@NotNull UUID worldId, @NotNull List<ChunkSnapshot> chunks) {
    }

    /** One palette per chunk keeps repeated content ids out of per-block memory and disk. */
    private static final class ChunkData {
        private final Map<Long, Integer> positions = new HashMap<>();
        private final List<String> palette = new ArrayList<>();
        private final Map<String, Integer> paletteIds = new HashMap<>();

        synchronized boolean put(long position, @NotNull String contentId) {
            Integer paletteId = paletteIds.get(contentId);
            if (paletteId == null) {
                paletteId = palette.size();
                palette.add(contentId);
                paletteIds.put(contentId, paletteId);
            }
            Integer previous = positions.put(position, paletteId);
            return previous == null || previous.intValue() != paletteId;
        }

        synchronized @Nullable String get(long position) {
            Integer paletteId = positions.get(position);
            return paletteId == null ? null : palette.get(paletteId);
        }

        synchronized @Nullable String remove(long position) {
            Integer paletteId = positions.remove(position);
            return paletteId == null ? null : palette.get(paletteId);
        }

        synchronized boolean isEmpty() {
            return positions.isEmpty();
        }

        synchronized int size() {
            return positions.size();
        }

        synchronized @NotNull List<LocalEntry> entries() {
            List<Map.Entry<Long, Integer>> sorted = new ArrayList<>(positions.entrySet());
            sorted.sort(Map.Entry.comparingByKey());
            List<LocalEntry> result = new ArrayList<>(sorted.size());
            for (Map.Entry<Long, Integer> entry : sorted) {
                result.add(new LocalEntry(entry.getKey(), palette.get(entry.getValue())));
            }
            return List.copyOf(result);
        }

        /** Compacts unused palette values while creating a deterministic disk snapshot. */
        synchronized @NotNull ChunkSnapshot snapshot(long chunkKey) {
            List<Map.Entry<Long, Integer>> sorted = new ArrayList<>(positions.entrySet());
            sorted.sort(Map.Entry.comparingByKey());

            Map<String, Integer> compactIds = new LinkedHashMap<>();
            List<String> compactPalette = new ArrayList<>();
            List<PositionSnapshot> compactPositions = new ArrayList<>(sorted.size());
            for (Map.Entry<Long, Integer> entry : sorted) {
                String contentId = palette.get(entry.getValue());
                Integer id = compactIds.get(contentId);
                if (id == null) {
                    id = compactPalette.size();
                    compactIds.put(contentId, id);
                    compactPalette.add(contentId);
                }
                compactPositions.add(new PositionSnapshot(entry.getKey(), id));
            }
            return new ChunkSnapshot(chunkKey, List.copyOf(compactPalette), List.copyOf(compactPositions));
        }
    }
}
