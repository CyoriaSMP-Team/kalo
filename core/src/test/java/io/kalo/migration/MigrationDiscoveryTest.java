package io.kalo.migration;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MigrationDiscoveryTest {

    @Test
    void findsRecognisedVendorFilesAndIgnoresOtherYaml(@TempDir Path directory) throws Exception {
        Path contents = Files.createDirectories(directory.resolve("contents"));
        Files.writeString(contents.resolve("items.yml"), """
                info:
                  namespace: weapons
                items:
                  ruby:
                    resource:
                      material: PAPER
                """);
        Files.writeString(directory.resolve("config.yml"), """
                language: en
                debug: false
                """);
        Files.writeString(directory.resolve("already.kalo.yml"), """
                ruby:
                  type: item
                """);

        List<MigrationDiscovery.Candidate> candidates = MigrationDiscovery.scan(directory.toFile());

        assertEquals(1, candidates.size());
        assertEquals("ItemsAdder", candidates.get(0).importer().name());
        assertTrue(candidates.get(0).file().getName().equals("items.yml"));
    }
}
