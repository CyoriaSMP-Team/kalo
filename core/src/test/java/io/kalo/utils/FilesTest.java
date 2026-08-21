package io.kalo.utils;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class FilesTest {

    @Test
    void recursiveListingsAreStableAndFilterCaseInsensitively(@TempDir Path root) throws Exception {
        java.nio.file.Files.createDirectories(root.resolve("nested"));
        java.nio.file.Files.writeString(root.resolve("z.yml"), "z");
        java.nio.file.Files.writeString(root.resolve("nested/a.YAML"), "a");
        java.nio.file.Files.writeString(root.resolve("ignored.txt"), "ignored");

        List<String> relative = Files.listFilesRecursively(root.toFile(), ".yml", ".yaml")
                .stream()
                .map(file -> root.relativize(file.toPath()).toString().replace('\\', '/'))
                .toList();

        assertEquals(List.of("nested/a.YAML", "z.yml"), relative);
    }
}
