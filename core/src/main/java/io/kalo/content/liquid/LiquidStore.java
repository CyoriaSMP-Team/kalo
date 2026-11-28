package io.kalo.content.liquid;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.kalo.pack.Json;
import net.kyori.adventure.key.Key;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.UUID;

/**
 * Persistent marked volumes, stored beside the other Kalo stores.
 *
 * <p>A volume is a box, not a record of every block: liquids flow and a lake changes shape.
 * The first volume containing a point wins, and adjacent bucket placements of the same
 * liquid expand an existing volume rather than creating one box per click.</p>
 */
public final class LiquidStore {

    public static final int MAX_VOLUMES = 4096;
    private static final long MAX_BLOCKS = 1_000_000L;

    private final Path file;
    private final List<Volume> volumes = new ArrayList<>();

    public LiquidStore(@NotNull Path file) {
        this.file = file.toAbsolutePath().normalize();
    }

    public @NotNull Path file() {
        return file;
    }

    public static @NotNull LiquidStore load(@NotNull Path file) throws IOException {
        LiquidStore store = new LiquidStore(file);
        if (!Files.isRegularFile(file)) {
            return store;
        }
        String text = Files.readString(file, StandardCharsets.UTF_8);
        JsonElement parsed = JsonParser.parseString(text);
        if (!parsed.isJsonObject()) {
            throw new IOException("liquids.json must be a JSON object");
        }
        JsonElement rawVolumes = parsed.getAsJsonObject().get("volumes");
        if (rawVolumes == null || !rawVolumes.isJsonArray()) {
            throw new IOException("liquids.json has no volumes array");
        }
        for (JsonElement element : rawVolumes.getAsJsonArray()) {
            if (!element.isJsonObject()) {
                continue;
            }
            try {
                store.volumes.add(volume(element.getAsJsonObject()));
            } catch (RuntimeException malformed) {
                throw new IOException("Invalid liquid volume: " + malformed.getMessage(),
                        malformed);
            }
        }
        return store;
    }

    public synchronized void save() throws IOException {
        JsonArray array = new JsonArray();
        for (Volume volume : volumes) {
            array.add(json(volume));
        }
        JsonObject root = new JsonObject();
        root.add("volumes", array);
        Path parent = file.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Path temp = Files.createTempFile(parent, ".kalo-liquids-", ".tmp");
        try {
            Files.writeString(temp, Json.write(root), StandardCharsets.UTF_8);
            Files.move(temp, file, StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(temp);
        }
    }

    /**
     * Adds one block, expanding an adjacent volume of the same liquid when possible.
     *
     * @return the volume that now contains the block
     */
    public synchronized @NotNull Volume addBlock(@NotNull String world, @NotNull Key liquid,
                                                 int x, int y, int z,
                                                 @Nullable String originalBiome) {
        for (int index = 0; index < volumes.size(); index++) {
            Volume volume = volumes.get(index);
            if (!volume.world().equals(world) || !volume.liquid().equals(liquid)) {
                continue;
            }
            if (volume.contains(x, y, z)) {
                return volume;
            }
            if (volume.adjacent(x, y, z)) {
                Volume expanded = new Volume(volume.id(), liquid, world,
                        Math.min(volume.minX(), x), Math.min(volume.minY(), y),
                        Math.min(volume.minZ(), z), Math.max(volume.maxX(), x),
                        Math.max(volume.maxY(), y), Math.max(volume.maxZ(), z),
                        volume.originalBiome());
                volumes.set(index, expanded);
                return expanded;
            }
        }
        Volume volume = new Volume(UUID.randomUUID(), liquid, world, x, y, z, x, y, z,
                originalBiome);
        add(volume);
        return volume;
    }

    public synchronized @NotNull Volume addVolume(@NotNull String world, @NotNull Key liquid,
                                                  int x1, int y1, int z1,
                                                  int x2, int y2, int z2,
                                                  @Nullable String originalBiome) {
        Volume volume = new Volume(UUID.randomUUID(), liquid, world,
                Math.min(x1, x2), Math.min(y1, y2), Math.min(z1, z2),
                Math.max(x1, x2), Math.max(y1, y2), Math.max(z1, z2), originalBiome);
        add(volume);
        return volume;
    }

    private void add(@NotNull Volume volume) {
        if (volumes.size() >= MAX_VOLUMES) {
            throw new IllegalArgumentException("too many liquid volumes (max "
                    + MAX_VOLUMES + ")");
        }
        volumes.add(volume);
    }

    /** The first volume containing the point, in creation order. */
    public synchronized @Nullable Volume containing(@NotNull String world,
                                                    int x, int y, int z) {
        for (Volume volume : volumes) {
            if (volume.world().equals(world) && volume.contains(x, y, z)) {
                return volume;
            }
        }
        return null;
    }

    public synchronized boolean remove(@NotNull Volume volume) {
        return volumes.remove(volume);
    }

    /**
     * Removes one block from the first volume containing it and splits the remaining box
     * into at most six smaller boxes. Used by empty-bucket pickup and receding flow.
     *
     * @return the original volume, or null when no volume contains the block
     */
    public synchronized @Nullable Volume removeBlock(@NotNull String world,
                                                     int x, int y, int z) {
        for (int index = 0; index < volumes.size(); index++) {
            Volume original = volumes.get(index);
            if (!original.world().equals(world) || !original.contains(x, y, z)) {
                continue;
            }
            List<Volume> pieces = split(original, x, y, z);
            if (volumes.size() - 1 + pieces.size() > MAX_VOLUMES) {
                throw new IllegalArgumentException("too many liquid volumes after split");
            }
            volumes.remove(index);
            int target = index;
            for (Volume piece : pieces) {
                volumes.add(target++, piece);
            }
            return original;
        }
        return null;
    }

    /**
     * Removes one block and returns just that cell, so a caller can restore its biome
     * without touching the rest of the original volume.
     */
    public synchronized @Nullable Volume removeCell(@NotNull String world,
                                                     int x, int y, int z) {
        Volume original = removeBlock(world, x, y, z);
        if (original == null) {
            return null;
        }
        return new Volume(UUID.randomUUID(), original.liquid(), world,
                x, y, z, x, y, z, original.originalBiome());
    }

    /**
     * Removes every block in an axis-aligned box from the volumes that intersect it.
     *
     * <p>This is the geometric update used when a sponge absorbs a region, a fluid
     * physics update removes a cell or a player deliberately takes a block out of a
     * marked bank. Each intersecting volume is split into at most six boxes, exactly as
     * {@link #removeBlock(String, int, int, int)} does for one block.</p>
     *
     * @return the removed intersections, carrying their original biome for restoration
     */
    public synchronized @NotNull List<Volume> removeBox(@NotNull String world,
                                                         int minX, int minY, int minZ,
                                                         int maxX, int maxY, int maxZ) {
        if (minX > maxX || minY > maxY || minZ > maxZ) {
            return List.of();
        }
        List<Volume> remaining = new ArrayList<>(volumes.size());
        List<Volume> removed = new ArrayList<>();
        for (Volume source : volumes) {
            if (!source.world().equals(world)
                    || !source.intersects(minX, minY, minZ, maxX, maxY, maxZ)) {
                remaining.add(source);
                continue;
            }
            int ix1 = Math.max(source.minX(), minX);
            int iy1 = Math.max(source.minY(), minY);
            int iz1 = Math.max(source.minZ(), minZ);
            int ix2 = Math.min(source.maxX(), maxX);
            int iy2 = Math.min(source.maxY(), maxY);
            int iz2 = Math.min(source.maxZ(), maxZ);
            removed.add(new Volume(UUID.randomUUID(), source.liquid(), world,
                    ix1, iy1, iz1, ix2, iy2, iz2, source.originalBiome()));
            addPiece(remaining, source, source.minX(), source.minY(), source.minZ(),
                    ix1 - 1, source.maxY(), source.maxZ());
            addPiece(remaining, source, ix2 + 1, source.minY(), source.minZ(),
                    source.maxX(), source.maxY(), source.maxZ());
            addPiece(remaining, source, ix1, source.minY(), source.minZ(),
                    ix2, iy1 - 1, source.maxZ());
            addPiece(remaining, source, ix1, iy2 + 1, source.minZ(),
                    ix2, source.maxY(), source.maxZ());
            addPiece(remaining, source, ix1, iy1, source.minZ(),
                    ix2, iy2, iz1 - 1);
            addPiece(remaining, source, ix1, iy1, iz2 + 1,
                    ix2, iy2, source.maxZ());
        }
        if (remaining.size() > MAX_VOLUMES) {
            throw new IllegalArgumentException("too many liquid volumes after box removal");
        }
        if (!removed.isEmpty()) {
            volumes.clear();
            volumes.addAll(remaining);
        }
        return List.copyOf(removed);
    }

    /**
     * Removes an arbitrary set of blocks, grouping consecutive X runs so a sponge-shaped
     * region does not become one box per block.
     *
     * @return the removed intersections, in run order
     */
    public synchronized @NotNull List<Volume> removeBlocks(@NotNull String world,
                                                           @NotNull List<BlockPos> blocks) {
        if (blocks.isEmpty()) {
            return List.of();
        }
        List<BlockPos> unique = new ArrayList<>(new LinkedHashSet<>(blocks));
        int minX = Integer.MAX_VALUE;
        int minY = Integer.MAX_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxY = Integer.MIN_VALUE;
        int maxZ = Integer.MIN_VALUE;
        for (BlockPos block : unique) {
            minX = Math.min(minX, block.x());
            minY = Math.min(minY, block.y());
            minZ = Math.min(minZ, block.z());
            maxX = Math.max(maxX, block.x());
            maxY = Math.max(maxY, block.y());
            maxZ = Math.max(maxZ, block.z());
        }
        long width = (long) maxX - minX + 1;
        long height = (long) maxY - minY + 1;
        long depth = (long) maxZ - minZ + 1;
        if (width * height * depth == unique.size()) {
            return removeBox(world, minX, minY, minZ, maxX, maxY, maxZ);
        }
        unique.sort(Comparator.comparingInt(BlockPos::y)
                .thenComparingInt(BlockPos::z)
                .thenComparingInt(BlockPos::x));
        List<Volume> removed = new ArrayList<>();
        int runStart = 0;
        for (int index = 1; index <= unique.size(); index++) {
            boolean runEnds = index == unique.size();
            if (!runEnds) {
                BlockPos previous = unique.get(index - 1);
                BlockPos current = unique.get(index);
                runEnds = current.y() != previous.y()
                        || current.z() != previous.z()
                        || current.x() != previous.x() + 1;
            }
            if (!runEnds) {
                continue;
            }
            BlockPos first = unique.get(runStart);
            BlockPos last = unique.get(index - 1);
            removed.addAll(removeBox(world, first.x(), first.y(), first.z(),
                    last.x(), last.y(), last.z()));
            runStart = index;
        }
        return List.copyOf(removed);
    }

    private static @NotNull List<Volume> split(@NotNull Volume source,
                                               int x, int y, int z) {
        List<Volume> pieces = new ArrayList<>();
        addPiece(pieces, source, source.minX(), source.minY(), source.minZ(),
                x - 1, source.maxY(), source.maxZ());
        addPiece(pieces, source, x + 1, source.minY(), source.minZ(),
                source.maxX(), source.maxY(), source.maxZ());
        addPiece(pieces, source, x, source.minY(), source.minZ(),
                x, y - 1, source.maxZ());
        addPiece(pieces, source, x, y + 1, source.minZ(),
                x, source.maxY(), source.maxZ());
        addPiece(pieces, source, x, y, source.minZ(),
                x, y, z - 1);
        addPiece(pieces, source, x, y, z + 1,
                x, y, source.maxZ());
        return List.copyOf(pieces);
    }

    private static void addPiece(@NotNull List<Volume> pieces, @NotNull Volume source,
                                 int minX, int minY, int minZ,
                                 int maxX, int maxY, int maxZ) {
        if (minX > maxX || minY > maxY || minZ > maxZ) {
            return;
        }
        pieces.add(new Volume(UUID.randomUUID(), source.liquid(), source.world(),
                minX, minY, minZ, maxX, maxY, maxZ, source.originalBiome()));
    }

    public synchronized @NotNull List<Volume> volumes() {
        return List.copyOf(volumes);
    }

    public synchronized int size() {
        return volumes.size();
    }

    private static @NotNull JsonObject json(@NotNull Volume volume) {
        JsonObject object = new JsonObject();
        object.addProperty("id", volume.id().toString());
        object.addProperty("liquid", volume.liquid().asString());
        object.addProperty("world", volume.world());
        object.add("min", xyz(volume.minX(), volume.minY(), volume.minZ()));
        object.add("max", xyz(volume.maxX(), volume.maxY(), volume.maxZ()));
        if (volume.originalBiome() != null) {
            object.addProperty("originalBiome", volume.originalBiome());
        }
        return object;
    }

    private static @NotNull JsonArray xyz(int x, int y, int z) {
        JsonArray array = new JsonArray();
        array.add(x);
        array.add(y);
        array.add(z);
        return array;
    }

    private static @NotNull Volume volume(@NotNull JsonObject object) {
        UUID id = UUID.fromString(required(object, "id"));
        Key liquid = Key.key(required(object, "liquid"));
        String world = required(object, "world");
        JsonArray min = requiredArray(object, "min");
        JsonArray max = requiredArray(object, "max");
        if (min.size() != 3 || max.size() != 3) {
            throw new IllegalArgumentException("min/max must contain three coordinates");
        }
        String originalBiome = object.has("originalBiome")
                ? object.get("originalBiome").getAsString() : null;
        return new Volume(id, liquid, world,
                min.get(0).getAsInt(), min.get(1).getAsInt(), min.get(2).getAsInt(),
                max.get(0).getAsInt(), max.get(1).getAsInt(), max.get(2).getAsInt(),
                originalBiome);
    }

    private static @NotNull String required(@NotNull JsonObject object,
                                            @NotNull String key) {
        if (!object.has(key)) {
            throw new IllegalArgumentException("missing " + key);
        }
        return object.get(key).getAsString();
    }

    private static @NotNull JsonArray requiredArray(@NotNull JsonObject object,
                                                    @NotNull String key) {
        if (!object.has(key) || !object.get(key).isJsonArray()) {
            throw new IllegalArgumentException("missing " + key + " array");
        }
        return object.getAsJsonArray(key);
    }

    /** One block coordinate passed to {@link #removeBlocks(String, List)}. */
    public record BlockPos(int x, int y, int z) {
    }

    public record Volume(
            @NotNull UUID id,
            @NotNull Key liquid,
            @NotNull String world,
            int minX, int minY, int minZ,
            int maxX, int maxY, int maxZ,
            @Nullable String originalBiome
    ) {
        public Volume {
            if (minX > maxX || minY > maxY || minZ > maxZ) {
                throw new IllegalArgumentException("liquid volume min must be <= max");
            }
            long x = (long) maxX - minX + 1;
            long y = (long) maxY - minY + 1;
            long z = (long) maxZ - minZ + 1;
            if (x * y * z > MAX_BLOCKS) {
                throw new IllegalArgumentException("liquid volume is larger than "
                        + MAX_BLOCKS + " blocks");
            }
        }

        public boolean contains(int x, int y, int z) {
            return x >= minX && x <= maxX && y >= minY && y <= maxY
                    && z >= minZ && z <= maxZ;
        }

        public boolean intersects(int minX, int minY, int minZ,
                                  int maxX, int maxY, int maxZ) {
            return this.minX <= maxX && this.maxX >= minX
                    && this.minY <= maxY && this.maxY >= minY
                    && this.minZ <= maxZ && this.maxZ >= minZ;
        }

        public boolean adjacent(int x, int y, int z) {
            return x >= minX - 1 && x <= maxX + 1
                    && y >= minY - 1 && y <= maxY + 1
                    && z >= minZ - 1 && z <= maxZ + 1;
        }
    }
}
