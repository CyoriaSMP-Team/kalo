package io.kalo.platform.java;

import io.kalo.content.block.definition.BlockCarrier;
import net.kyori.adventure.key.Key;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;

import static org.junit.jupiter.api.Assertions.*;

class BlockStateAllocatorTest {

    @Test
    void encodeAndDecodeRoundTrip() {
        for (int instrument = 0; instrument < 16; instrument++) {
            for (int note = 0; note < 25; note++) {
                for (boolean powered : new boolean[]{false, true}) {
                    var expected = new BlockStateAllocator.NoteBlockState(instrument, note, powered);
                    assertEquals(expected, BlockStateAllocator.decode(BlockStateAllocator.encode(expected)));
                }
            }
        }
    }

    @Test
    void assignmentsSurviveSaveAndNewBlocksUseNextState() throws Exception {
        var first = new BlockStateAllocator(BlockCarrier.NOTE_BLOCK);
        Key ruby = Key.key("testpack", "ruby");
        Key sapphire = Key.key("testpack", "sapphire");
        assertEquals(1, first.allocate(ruby));

        var file = Files.createTempFile("kalo-block-states", ".json");
        first.save(file);

        var second = new BlockStateAllocator(BlockCarrier.NOTE_BLOCK);
        second.load(file);
        assertEquals(1, second.allocate(ruby));
        assertEquals(2, second.allocate(sapphire));
    }

    @Test
    void malformedLoadDoesNotPartiallyReplaceAssignments() throws Exception {
        var allocator = new BlockStateAllocator(BlockCarrier.NOTE_BLOCK);
        Key existing = Key.key("testpack", "existing");
        assertEquals(1, allocator.allocate(existing));

        var file = Files.createTempFile("kalo-block-states-invalid", ".json");
        Files.writeString(file, "{\"other:block\": 2, \"duplicate:block\": 2}");
        assertThrows(Exception.class, () -> allocator.load(file));
        assertEquals(1, allocator.allocate(existing));
        assertEquals(2, allocator.allocate(Key.key("testpack", "new")));
    }
}
