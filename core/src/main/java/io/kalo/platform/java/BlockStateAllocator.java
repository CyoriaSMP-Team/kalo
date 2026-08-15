package io.kalo.platform.java;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.kalo.content.block.definition.BlockCarrier;
import io.kalo.pack.Json;
import net.kyori.adventure.key.Key;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Assigns each custom block a distinct vanilla block state, and remembers the assignment.
 *
 * <p><b>Assignments must be stable forever.</b> A placed custom block is stored in the
 * world as nothing more than its borrowed vanilla state. If the state assigned to
 * {@code mypack:ruby_block} changed — because a pack was added, removed, or renamed, or
 * because assignment was derived from iteration order — then every ruby block already
 * placed in the world would silently become whatever now owns that state.</p>
 *
 * <p>So assignments are persisted and never reused: new blocks take the next free index,
 * and an index whose block disappears stays reserved rather than being handed to someone
 * else. Removing a pack and putting it back gets the original states.</p>
 */
public final class BlockStateAllocator {

    /**
     * Reserved so that a note block nobody has touched still has a state to be in, and
     * renders as a normal note block.
     */
    static final int RESERVED_VANILLA_INDEX = 0;

    private static final int NOTE_COUNT = 25;
    private static final int POWERED_COUNT = 2;

    private final BlockCarrier carrier;
    /** key -> state index, ordered by index for stable serialization. */
    private final Map<String, Integer> assignments = new LinkedHashMap<>();
    private final Set<Integer> usedIndices = new TreeSet<>();
    private int nextIndex = RESERVED_VANILLA_INDEX + 1;

    /** Where to persist to, once {@link #attach} has been called. */
    private Path file;

    public BlockStateAllocator(@NotNull BlockCarrier carrier) {
        this.carrier = carrier;
    }

    /**
     * Binds the allocator to a file and writes through on every new assignment.
     *
     * <p>Saving only on clean shutdown is not enough: assignments are made during pack
     * generation, so a crash between then and shutdown would lose them, and the next boot
     * would hand those states to different blocks — turning every already-placed block of
     * that kind into something else. A write per new block is cheap because new blocks
     * only appear when a pack changes.</p>
     */
    public synchronized void attach(@NotNull Path file) {
        this.file = file;
    }

    /**
     * Returns the state index for {@code key}, assigning a new one if this is the first
     * time the block has been seen.
     *
     * @throws IllegalStateException if the carrier has no states left
     */
    public synchronized int allocate(@NotNull Key key) {
        Integer existing = assignments.get(key.asString());
        if (existing != null) {
            return existing;
        }

        while (usedIndices.contains(nextIndex)) {
            nextIndex++;
        }
        if (nextIndex >= carrier.stateCount()) {
            throw new IllegalStateException("Ran out of " + carrier + " block states after "
                    + carrier.usableStateCount() + " custom blocks");
        }

        int index = nextIndex++;
        assignments.put(key.asString(), index);
        usedIndices.add(index);

        if (file != null) {
            try {
                save(file);
            } catch (IOException e) {
                // Losing the write is worse than noisy logs: the assignment is live in
                // this session but would not survive a restart.
                Logger.getLogger(BlockStateAllocator.class.getName())
                        .log(Level.SEVERE, "Could not persist the block state assigned to " + key.asString(), e);
            }
        }
        return index;
    }

    public synchronized @Nullable Integer indexOf(@NotNull Key key) {
        return assignments.get(key.asString());
    }

    public synchronized @NotNull @Unmodifiable Map<String, Integer> assignments() {
        return Map.copyOf(assignments);
    }

    /** Decomposes a state index into the carrier's block state properties. */
    public static @NotNull NoteBlockState decode(int index) {
        int powered = index % POWERED_COUNT;
        int rest = index / POWERED_COUNT;
        int note = rest % NOTE_COUNT;
        int instrument = rest / NOTE_COUNT;
        return new NoteBlockState(instrument, note, powered == 1);
    }

    public static int encode(@NotNull NoteBlockState state) {
        return (state.instrument() * NOTE_COUNT + state.note()) * POWERED_COUNT + (state.powered() ? 1 : 0);
    }

    /**
     * A note block's three state properties.
     *
     * @param instrument index into {@link JavaBlockCompiler#INSTRUMENT_IDS}
     * @param note       0..24
     */
    public record NoteBlockState(int instrument, int note, boolean powered) {
    }

    // --- persistence -------------------------------------------------------------

    /**
     * Loads previous assignments. A missing or unreadable file starts from empty, which
     * is correct for a first run but does mean a *lost* file re-randomises a world's
     * custom blocks — hence writing it atomically below.
     */
    public synchronized void load(@NotNull Path file) throws IOException {
        if (!Files.exists(file)) {
            return;
        }

        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            JsonElement parsed = JsonParser.parseReader(reader);
            if (!parsed.isJsonObject()) {
                throw new IOException("expected a JSON object in " + file);
            }
            Map<String, Integer> loadedAssignments = new LinkedHashMap<>();
            Set<Integer> loadedIndices = new TreeSet<>();
            int loadedNextIndex = RESERVED_VANILLA_INDEX + 1;
            for (Map.Entry<String, JsonElement> entry : parsed.getAsJsonObject().entrySet()) {
                int index = entry.getValue().getAsInt();
                if (index <= RESERVED_VANILLA_INDEX || index >= carrier.stateCount()) {
                    throw new IOException("state index " + index + " for '" + entry.getKey()
                            + "' is outside the range this carrier provides");
                }
                if (!loadedIndices.add(index)) {
                    throw new IOException("state index " + index + " is assigned twice in " + file);
                }
                loadedAssignments.put(entry.getKey(), index);
                loadedNextIndex = Math.max(loadedNextIndex, index + 1);
            }
            assignments.clear();
            assignments.putAll(loadedAssignments);
            usedIndices.clear();
            usedIndices.addAll(loadedIndices);
            nextIndex = loadedNextIndex;
        }
    }

    /** Writes assignments atomically so an interrupted save cannot truncate the file. */
    public synchronized void save(@NotNull Path file) throws IOException {
        Path parent = file.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }

        JsonObject root = new JsonObject();
        // Sorted by key so the file does not churn between runs.
        new TreeMap<>(assignments).forEach(root::addProperty);

        Path temp = Files.createTempFile(parent, ".block-states-", ".json.tmp");
        try {
            Files.writeString(temp, Json.write(root), StandardCharsets.UTF_8);
            Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            Files.deleteIfExists(temp);
            throw e;
        }
    }
}
