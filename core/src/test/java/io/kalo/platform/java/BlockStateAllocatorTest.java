package io.kalo.platform.java;

import io.kalo.content.block.definition.BlockCarrier;
import net.kyori.adventure.key.Key;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BlockStateAllocatorTest {

    private static Key key(String name) {
        return Key.key("testpack", name);
    }

    @Test
    void theSameBlockAlwaysGetsTheSameState() {
        BlockStateAllocator allocator = new BlockStateAllocator();

        var first = allocator.allocate(key("ruby"), BlockCarrier.NOTE_BLOCK);
        assertEquals(first, allocator.allocate(key("ruby"), BlockCarrier.NOTE_BLOCK));
    }

    @Test
    void differentBlocksNeverShareAState() {
        BlockStateAllocator allocator = new BlockStateAllocator();

        assertNotEquals(allocator.allocate(key("a"), BlockCarrier.NOTE_BLOCK),
                allocator.allocate(key("b"), BlockCarrier.NOTE_BLOCK));
    }

    @Test
    void theVanillaStateIsNeverHandedOut() {
        // State 0 stays free so an untouched vanilla block still renders normally.
        BlockStateAllocator allocator = new BlockStateAllocator();

        for (int i = 0; i < 50; i++) {
            assertTrue(allocator.allocate(key("b" + i), BlockCarrier.NOTE_BLOCK).state() > 0);
        }
    }

    @Test
    void afullCarrierFallsThroughToTheNextRatherThanFailing() {
        // Running out of one kind of state should not mean running out of blocks.
        BlockStateAllocator allocator = new BlockStateAllocator();

        int noteBlockCapacity = BlockCarrier.NOTE_BLOCK.usableStateCount();
        for (int i = 0; i < noteBlockCapacity; i++) {
            allocator.allocate(key("b" + i), BlockCarrier.NOTE_BLOCK);
        }

        var overflow = allocator.allocate(key("one_too_many"), BlockCarrier.NOTE_BLOCK);
        assertEquals(BlockCarrier.TRIPWIRE, overflow.carrier());
    }

    @Test
    void exhaustingEveryCarrierIsReportedWithACount() {
        BlockStateAllocator allocator = new BlockStateAllocator();

        int total = BlockCarrier.NOTE_BLOCK.usableStateCount() + BlockCarrier.TRIPWIRE.usableStateCount();
        for (int i = 0; i < total; i++) {
            allocator.allocate(key("b" + i), BlockCarrier.NOTE_BLOCK);
        }

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> allocator.allocate(key("nowhere"), BlockCarrier.NOTE_BLOCK));
        assertTrue(error.getMessage().contains("Ran out"), error.getMessage());
    }

    @Test
    void assignmentsSurviveSaveAndLoad(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("block-states.json");

        BlockStateAllocator first = new BlockStateAllocator();
        var ruby = first.allocate(key("ruby"), BlockCarrier.NOTE_BLOCK);
        var chair = first.allocate(key("chair"), BlockCarrier.TRIPWIRE);
        first.save(file);

        BlockStateAllocator second = new BlockStateAllocator();
        second.load(file);

        assertEquals(ruby, second.assignmentOf(key("ruby")));
        assertEquals(chair, second.assignmentOf(key("chair")));
    }

    @Test
    void newBlocksTakeFreshStatesAfterAReload(@TempDir Path dir) throws IOException {
        // A state that has been used is never handed to a different block, even once the
        // block that had it is gone: a world may still be full of them.
        Path file = dir.resolve("block-states.json");

        BlockStateAllocator first = new BlockStateAllocator();
        var ruby = first.allocate(key("ruby"), BlockCarrier.NOTE_BLOCK);
        first.save(file);

        BlockStateAllocator second = new BlockStateAllocator();
        second.load(file);
        var fresh = second.allocate(key("added_later"), BlockCarrier.NOTE_BLOCK);

        assertNotEquals(ruby.state(), fresh.state());
    }

    @Test
    void thePersistedFormatNamesItsCarrier(@TempDir Path dir) throws IOException {
        // The whole point of the format: a bare index would be ambiguous the moment a
        // second carrier existed, and every already-placed block would be reinterpreted.
        Path file = dir.resolve("block-states.json");

        BlockStateAllocator allocator = new BlockStateAllocator();
        allocator.allocate(key("ruby"), BlockCarrier.TRIPWIRE);
        allocator.save(file);

        String json = Files.readString(file, StandardCharsets.UTF_8);
        assertTrue(json.contains("TRIPWIRE"), json);
        assertTrue(json.contains("\"state\""), json);
    }

    @Test
    void aFileFromBeforeCarriersStillMeansNoteBlock(@TempDir Path dir) throws IOException {
        // Servers that ran an earlier Kalo must keep their blocks looking the way they did.
        Path file = dir.resolve("block-states.json");
        Files.writeString(file, "{\"testpack:ruby\":7,\"testpack:chair\":3}", StandardCharsets.UTF_8);

        BlockStateAllocator allocator = new BlockStateAllocator();
        allocator.load(file);

        assertEquals(new BlockStateAllocator.Assignment(BlockCarrier.NOTE_BLOCK, 7),
                allocator.assignmentOf(key("ruby")));
        assertEquals(new BlockStateAllocator.Assignment(BlockCarrier.NOTE_BLOCK, 3),
                allocator.assignmentOf(key("chair")));
    }

    @Test
    void anUpgradedFileDoesNotReuseTheOldStates(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("block-states.json");
        Files.writeString(file, "{\"testpack:ruby\":7}", StandardCharsets.UTF_8);

        BlockStateAllocator allocator = new BlockStateAllocator();
        allocator.load(file);
        var fresh = allocator.allocate(key("new"), BlockCarrier.NOTE_BLOCK);

        assertNotEquals(7, fresh.state());
    }

    @Test
    void aMalformedFileLeavesExistingAssignmentsAlone(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("block-states.json");
        Files.writeString(file, "{\"testpack:ruby\":{\"carrier\":\"NOT_A_CARRIER\",\"state\":1}}",
                StandardCharsets.UTF_8);

        BlockStateAllocator allocator = new BlockStateAllocator();
        var before = allocator.allocate(key("kept"), BlockCarrier.NOTE_BLOCK);

        assertThrows(IOException.class, () -> allocator.load(file));
        assertEquals(before, allocator.assignmentOf(key("kept")));
    }

    @Test
    void twoBlocksClaimingOneStateIsRejected(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("block-states.json");
        Files.writeString(file, "{\"a\":{\"carrier\":\"NOTE_BLOCK\",\"state\":1},"
                + "\"b\":{\"carrier\":\"NOTE_BLOCK\",\"state\":1}}", StandardCharsets.UTF_8);

        assertThrows(IOException.class, () -> new BlockStateAllocator().load(file));
    }

    @Test
    void writingThroughOnAllocationSurvivesACrash(@TempDir Path dir) throws IOException {
        // Assignments are made during pack generation; only saving at shutdown would lose
        // them if the server died in between, and the next boot would reassign.
        Path file = dir.resolve("block-states.json");

        BlockStateAllocator allocator = new BlockStateAllocator();
        allocator.attach(file);
        var ruby = allocator.allocate(key("ruby"), BlockCarrier.NOTE_BLOCK);

        BlockStateAllocator afterCrash = new BlockStateAllocator();
        afterCrash.load(file);
        assertEquals(ruby, afterCrash.assignmentOf(key("ruby")));
    }

    @Test
    void groupingByCarrierIsWhatTheCompilerConsumes() {
        BlockStateAllocator allocator = new BlockStateAllocator();
        allocator.allocate(key("solid"), BlockCarrier.NOTE_BLOCK);
        allocator.allocate(key("flat"), BlockCarrier.TRIPWIRE);

        var grouped = allocator.byCarrier();
        assertNotNull(grouped.get(BlockCarrier.NOTE_BLOCK));
        assertNotNull(grouped.get(BlockCarrier.TRIPWIRE));
        assertTrue(grouped.get(BlockCarrier.TRIPWIRE).containsValue("testpack:flat"));
    }

    @Test
    void aMissingLoadFileReallyStartsFromEmpty(@TempDir Path dir) throws Exception {
        var allocator = new BlockStateAllocator(BlockCarrier.NOTE_BLOCK);
        allocator.allocate(Key.key("testpack", "old"));

        allocator.load(dir.resolve("does-not-exist.json"));

        assertTrue(allocator.assignments().isEmpty());
        assertEquals(1, allocator.allocate(Key.key("testpack", "new")));
    }

    @Test
    void anUnpersistedAssignmentIsRolledBack(@TempDir Path dir) {
        var allocator = new BlockStateAllocator(BlockCarrier.NOTE_BLOCK);
        Key ruby = Key.key("testpack", "ruby");
        // A directory cannot be replaced with the state file. This deterministically
        // exercises the failed write without depending on platform file permissions.
        allocator.attach(dir);

        assertThrows(IllegalStateException.class, () -> allocator.allocate(ruby));
        assertNull(allocator.indexOf(ruby));
        assertTrue(allocator.assignments().isEmpty());

        allocator.attach(dir.resolve("states.json"));
        assertEquals(1, allocator.allocate(ruby), "the failed index should be available again");
    }

    @Test
    void invalidStatesCannotAliasARealCarrierState() {
        assertThrows(IllegalArgumentException.class, () -> BlockStateAllocator.decode(-1));
        assertThrows(IllegalArgumentException.class,
                () -> BlockStateAllocator.decode(BlockCarrier.NOTE_BLOCK.stateCount()));
        assertThrows(IllegalArgumentException.class,
                () -> new BlockStateAllocator.NoteBlockState(16, 0, false));
        assertThrows(IllegalArgumentException.class,
                () -> new BlockStateAllocator.NoteBlockState(0, 25, false));
    }

    @Test
    void persistedAssignmentsRequireValidKeysAndIntegerIndices(@TempDir Path dir) throws Exception {
        var allocator = new BlockStateAllocator(BlockCarrier.NOTE_BLOCK);

        Path invalidKey = dir.resolve("invalid-key.json");
        Files.writeString(invalidKey, "{\"NOT A KEY\": 1}");
        assertThrows(Exception.class, () -> allocator.load(invalidKey));

        Path fractionalIndex = dir.resolve("fractional-index.json");
        Files.writeString(fractionalIndex, "{\"testpack:ruby\": 1.5}");
        assertThrows(Exception.class, () -> allocator.load(fractionalIndex));
        assertTrue(allocator.assignments().isEmpty());
    }
}
