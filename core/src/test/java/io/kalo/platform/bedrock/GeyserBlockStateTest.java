package io.kalo.platform.bedrock;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class GeyserBlockStateTest {

    @Test
    void convertsThePersistedIndexToGeysersExactJavaIdentifier() {
        assertEquals("minecraft:note_block[instrument=harp,note=0,powered=true]",
                GeyserBlockState.javaIdentifier(1));
        assertEquals("minecraft:note_block[instrument=harp,note=1,powered=false]",
                GeyserBlockState.javaIdentifier(2));
        assertEquals("minecraft:note_block[instrument=basedrum,note=0,powered=false]",
                GeyserBlockState.javaIdentifier(50));
        assertEquals("minecraft:note_block[instrument=pling,note=24,powered=true]",
                GeyserBlockState.javaIdentifier(799));
    }

    @Test
    void rejectsTheReservedVanillaStateAndOutOfRangeIndexes() {
        assertThrows(IllegalArgumentException.class, () -> GeyserBlockState.javaIdentifier(0));
        assertThrows(IllegalArgumentException.class, () -> GeyserBlockState.javaIdentifier(-1));
        assertThrows(IllegalArgumentException.class, () -> GeyserBlockState.javaIdentifier(800));
    }
}
