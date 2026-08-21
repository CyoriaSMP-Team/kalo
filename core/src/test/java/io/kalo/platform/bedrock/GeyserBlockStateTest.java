package io.kalo.platform.bedrock;

import io.kalo.content.block.definition.BlockCarrier;
import io.kalo.platform.java.BlockStateAllocator;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class GeyserBlockStateTest {

    @Test
    void convertsThePersistedIndexToGeysersExactJavaIdentifier() {
        assertEquals("minecraft:note_block[instrument=harp,note=0,powered=true]",
                GeyserBlockState.javaIdentifier(BlockCarrier.NOTE_BLOCK, 1));
        assertEquals("minecraft:note_block[instrument=harp,note=1,powered=false]",
                GeyserBlockState.javaIdentifier(BlockCarrier.NOTE_BLOCK, 2));
        assertEquals("minecraft:note_block[instrument=basedrum,note=0,powered=false]",
                GeyserBlockState.javaIdentifier(BlockCarrier.NOTE_BLOCK, 50));
        assertEquals("minecraft:note_block[instrument=pling,note=24,powered=true]",
                GeyserBlockState.javaIdentifier(BlockCarrier.NOTE_BLOCK, 799));
    }

    /**
     * The allocator spills onto the later carriers once note blocks fill up. Naming those
     * states after a note block would point Bedrock at a block the Java side never placed,
     * so every carrier has to describe itself.
     */
    @Test
    void namesTheCarrierTheStateActuallyBelongsTo() {
        assertEquals("minecraft:tripwire[attached=false,disarmed=false,east=false,"
                        + "north=false,powered=false,south=false,west=true]",
                GeyserBlockState.javaIdentifier(BlockCarrier.TRIPWIRE, 1));
        assertEquals("minecraft:scaffolding[bottom=false,distance=0,waterlogged=true]",
                GeyserBlockState.javaIdentifier(BlockCarrier.SCAFFOLDING, 1));
    }

    @Test
    void readsTheCarrierStraightOffAnAssignment() {
        assertEquals("minecraft:scaffolding[bottom=false,distance=1,waterlogged=false]",
                GeyserBlockState.javaIdentifier(
                        new BlockStateAllocator.Assignment(BlockCarrier.SCAFFOLDING, 2)));
    }

    @Test
    void rejectsTheReservedVanillaStateAndOutOfRangeIndexes() {
        assertThrows(IllegalArgumentException.class,
                () -> GeyserBlockState.javaIdentifier(BlockCarrier.NOTE_BLOCK, 0));
        assertThrows(IllegalArgumentException.class,
                () -> GeyserBlockState.javaIdentifier(BlockCarrier.NOTE_BLOCK, -1));
        assertThrows(IllegalArgumentException.class,
                () -> GeyserBlockState.javaIdentifier(BlockCarrier.NOTE_BLOCK, 800));
        assertThrows(IllegalArgumentException.class,
                () -> GeyserBlockState.javaIdentifier(BlockCarrier.SCAFFOLDING, 32));
    }
}
