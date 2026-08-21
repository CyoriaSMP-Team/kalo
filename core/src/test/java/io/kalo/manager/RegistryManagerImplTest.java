package io.kalo.manager;

import io.kalo.content.block.definition.BlockCarrier;
import io.kalo.platform.java.BlockStateAllocator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RegistryManagerImplTest {

    @Test
    void malformedPersistentBlockStatesAbortStartup(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("block-states.json");
        Files.writeString(file, "{\"pack:first\": 1, \"pack:second\": 1}");

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> RegistryManagerImpl.loadBlockStateAssignments(
                        new BlockStateAllocator(BlockCarrier.NOTE_BLOCK), file));

        assertTrue(error.getMessage().contains(file.toString()), error.getMessage());
    }
}
