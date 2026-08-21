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
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Assigns each custom block a vanilla block state to be drawn as, and remembers it.
 *
 * <p><b>Assignments must be stable forever.</b> A placed custom block is stored in the
 * world as nothing but its borrowed vanilla state. If the state behind
 * {@code mypack:ruby_block} ever changed, every ruby block already placed would silently
 * become whatever now owns it.</p>
 *
 * <p>So assignments are persisted and never reused, and the record carries <b>which
 * carrier</b> as well as which state within it. Storing a bare index would have been
 * enough while there was one carrier and ambiguous the moment there were two — index 3
 * would mean a note block on an old server and possibly tripwire on a new one, silently
 * reinterpreting every block already in the ground. The format below cannot be
 * misread that way, so adding a carrier later is additive rather than a migration.</p>
 */
public final class BlockStateAllocator {

    private static final Logger LOGGER = Logger.getLogger(BlockStateAllocator.class.getName());

    /**
     * Reserved in every carrier so an untouched vanilla block still has a state to be in.
     */
    static final int RESERVED_VANILLA_INDEX = 0;

    /**
     * The order carriers are filled in.
     *
     * <p>Append only. Reordering would not corrupt existing assignments — they name their
     * carrier — but it would scatter new ones unpredictably.</p>
     */
    private static final List<BlockCarrier> FILL_ORDER =
            List.of(BlockCarrier.NOTE_BLOCK, BlockCarrier.TRIPWIRE, BlockCarrier.SCAFFOLDING);

    /** Where a custom block is drawn: a carrier and a state within it. */
    public record Assignment(@NotNull BlockCarrier carrier, int state) {
        public Assignment {
            if (state <= RESERVED_VANILLA_INDEX || state >= carrier.stateCount()) {
                throw new IllegalArgumentException(
                        "state " + state + " is outside the usable range of " + carrier);
            }
        }

        /**
         * The complete Java block state this assignment stands for, e.g.
         * {@code minecraft:note_block[instrument=harp,note=0,powered=true]}.
         *
         * <p>The bare index means nothing without its carrier, so everything that needs to
         * name the state asks the assignment rather than rebuilding the string. Geyser
         * overrides are keyed by it, and the world scanner matches placed blocks against
         * it.</p>
         */
        public @NotNull String javaIdentifier() {
            return carrier.vanillaBlock() + "[" + carrier.variantKey(state) + "]";
        }
    }

    private final Map<String, Assignment> assignments = new LinkedHashMap<>();
    private final Map<BlockCarrier, Set<Integer>> used = new EnumMap<>(BlockCarrier.class);
    private final Map<BlockCarrier, Integer> next = new EnumMap<>(BlockCarrier.class);

    private Path file;

    public BlockStateAllocator() {
        for (BlockCarrier carrier : BlockCarrier.values()) {
            used.put(carrier, new TreeSet<>());
            next.put(carrier, RESERVED_VANILLA_INDEX + 1);
        }
    }

    /** Backward-compatible constructor; the requested carrier is supplied per definition. */
    public BlockStateAllocator(@NotNull BlockCarrier ignored) {
        this();
    }

    /**
     * Binds the allocator to a file and writes through on every new assignment.
     *
     * <p>Saving only at shutdown is not enough: assignments are made during pack
     * generation, so a crash before shutdown would lose them and the next boot would hand
     * those states to different blocks.</p>
     */
    public synchronized void attach(@NotNull Path file) {
        this.file = file;
    }

    /**
     * Returns the assignment for {@code key}, making one on first sight.
     *
     * @param preferred the carrier the block asked for; later carriers in the fill order
     *                  are used once it is full, so running out of one kind of state does
     *                  not have to mean failure
     * @throws IllegalStateException when every carrier is exhausted
     */
    public synchronized @NotNull Assignment allocate(@NotNull Key key, @NotNull BlockCarrier preferred) {
        Assignment existing = assignments.get(key.asString());
        if (existing != null) {
            return existing;
        }

        for (BlockCarrier carrier : carriersFrom(preferred)) {
            Integer index = nextFreeIn(carrier);
            if (index == null) {
                continue;
            }

            Assignment assignment = new Assignment(carrier, index);
            assignments.put(key.asString(), assignment);
            used.get(carrier).add(index);
            if (file != null) {
                try {
                    save(file);
                } catch (IOException e) {
                    assignments.remove(key.asString());
                    used.get(carrier).remove(index);
                    next.put(carrier, index);
                    throw new IllegalStateException(
                            "Could not persist the block state assigned to " + key.asString(), e);
                }
            }
            return assignment;
        }

        throw new IllegalStateException("Ran out of block states after " + assignments.size()
                + " custom blocks; carriers available: " + FILL_ORDER);
    }

    /** The preferred carrier first, then the rest of the fill order as a fallback. */
    private static @NotNull List<BlockCarrier> carriersFrom(@NotNull BlockCarrier preferred) {
        List<BlockCarrier> order = new java.util.ArrayList<>();
        order.add(preferred);
        FILL_ORDER.stream().filter(carrier -> carrier != preferred).forEach(order::add);
        return order;
    }

    private @Nullable Integer nextFreeIn(@NotNull BlockCarrier carrier) {
        int candidate = next.get(carrier);
        Set<Integer> taken = used.get(carrier);

        while (candidate < carrier.stateCount() && taken.contains(candidate)) {
            candidate++;
        }
        if (candidate >= carrier.stateCount()) {
            return null;
        }
        next.put(carrier, candidate + 1);
        return candidate;
    }

    public synchronized @Nullable Assignment assignmentOf(@NotNull Key key) {
        return assignments.get(key.asString());
    }

    public synchronized @NotNull @Unmodifiable Map<String, Assignment> assignments() {
        return Map.copyOf(assignments);
    }

    /** Backward-compatible single-carrier allocation; defaults to NOTE_BLOCK. */
    public synchronized int allocate(@NotNull Key key) {
        return allocate(key, BlockCarrier.NOTE_BLOCK).state();
    }

    /** Backward-compatible state lookup for callers that only understand NOTE_BLOCK indices. */
    public synchronized @Nullable Integer indexOf(@NotNull Key key) {
        Assignment assignment = assignmentOf(key);
        return assignment == null ? null : assignment.state();
    }

    /** Legacy note-block state decomposition retained for compatibility/tests. */
    public static @NotNull NoteBlockState decode(int index) {
        if (index < 0 || index >= BlockCarrier.NOTE_BLOCK.stateCount()) {
            throw new IllegalArgumentException("note block state index is out of range: " + index);
        }
        int powered = index % 2;
        int rest = index / 2;
        int note = rest % 25;
        int instrument = rest / 25;
        return new NoteBlockState(instrument, note, powered == 1);
    }

    public static int encode(@NotNull NoteBlockState state) {
        return (state.instrument() * 25 + state.note()) * 2 + (state.powered() ? 1 : 0);
    }

    public record NoteBlockState(int instrument, int note, boolean powered) {
        public NoteBlockState {
            int instrumentCount = BlockCarrier.NOTE_BLOCK.stateCount() / 50;
            if (instrument < 0 || instrument >= instrumentCount) {
                throw new IllegalArgumentException("instrument index is out of range: " + instrument);
            }
            if (note < 0 || note >= 25) {
                throw new IllegalArgumentException("note is out of range: " + note);
            }
        }
    }

    /** Assignments grouped by carrier, which is how the pack compiler consumes them. */
    public synchronized @NotNull Map<BlockCarrier, Map<Integer, String>> byCarrier() {
        Map<BlockCarrier, Map<Integer, String>> grouped = new EnumMap<>(BlockCarrier.class);
        assignments.forEach((key, assignment) ->
                grouped.computeIfAbsent(assignment.carrier(), ignored -> new TreeMap<>())
                        .put(assignment.state(), key));
        return grouped;
    }

    // --- persistence -------------------------------------------------------------

    /**
     * Reads previous assignments.
     *
     * <p>Accepts the older bare-integer form, which could only ever have meant a note
     * block, so a server that ran an earlier Kalo keeps its blocks looking the way they
     * did rather than being silently reshuffled.</p>
     */
    public synchronized void load(@NotNull Path file) throws IOException {
        if (!Files.exists(file)) {
            assignments.clear();
            for (BlockCarrier carrier : BlockCarrier.values()) {
                used.get(carrier).clear();
                next.put(carrier, RESERVED_VANILLA_INDEX + 1);
            }
            return;
        }

        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            JsonElement parsed = JsonParser.parseReader(reader);
            if (!parsed.isJsonObject()) {
                throw new IOException("expected a JSON object in " + file);
            }

            Map<String, Assignment> loaded = new LinkedHashMap<>();
            Map<BlockCarrier, Set<Integer>> loadedUsed = new EnumMap<>(BlockCarrier.class);
            for (BlockCarrier carrier : BlockCarrier.values()) {
                loadedUsed.put(carrier, new TreeSet<>());
            }

            for (Map.Entry<String, JsonElement> entry : parsed.getAsJsonObject().entrySet()) {
                Assignment assignment = readAssignment(entry.getValue(), entry.getKey(), file);
                if (!loadedUsed.get(assignment.carrier()).add(assignment.state())) {
                    throw new IOException("state " + assignment.state() + " of "
                            + assignment.carrier() + " is assigned twice in " + file);
                }
                loaded.put(entry.getKey(), assignment);
            }

            // Applied only once every entry parsed, so a malformed file leaves the
            // in-memory assignments untouched rather than half-replaced.
            assignments.clear();
            assignments.putAll(loaded);
            used.clear();
            used.putAll(loadedUsed);
            next.clear();
            for (BlockCarrier carrier : BlockCarrier.values()) {
                int highest = loadedUsed.get(carrier).stream().mapToInt(Integer::intValue).max()
                        .orElse(RESERVED_VANILLA_INDEX);
                next.put(carrier, highest + 1);
            }
        }
    }

    private static @NotNull Assignment readAssignment(@NotNull JsonElement value,
                                                      @NotNull String key,
                                                      @NotNull Path file) throws IOException {
        try {
            Key.key(key);
            if (value.isJsonPrimitive() && value.getAsJsonPrimitive().isNumber()) {
                int state = value.getAsBigDecimal().intValueExact();
                return new Assignment(BlockCarrier.NOTE_BLOCK, state);
            }
            JsonObject object = value.getAsJsonObject();
            int state = object.get("state").getAsBigDecimal().intValueExact();
            return new Assignment(
                    BlockCarrier.fromId(object.get("carrier").getAsString()),
                    state);
        } catch (Exception e) {
            throw new IOException("could not read the assignment for '" + key + "' in " + file, e);
        }
    }

    /** Writes atomically so an interrupted save cannot truncate the file. */
    public synchronized void save(@NotNull Path file) throws IOException {
        if (Files.isDirectory(file)) {
            throw new IOException("state file path is a directory: " + file);
        }
        Path parent = file.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }

        JsonObject root = new JsonObject();
        // Sorted so the file does not churn between runs.
        new TreeMap<>(assignments).forEach((key, assignment) -> {
            JsonObject entry = new JsonObject();
            entry.addProperty("carrier", assignment.carrier().name());
            entry.addProperty("state", assignment.state());
            root.add(key, entry);
        });

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
