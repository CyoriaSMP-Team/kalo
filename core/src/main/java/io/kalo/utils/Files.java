package io.kalo.utils;

import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

public final class Files {

    private Files() {
    }

    /**
     * Lists files below {@code root}, optionally filtered by extension.
     *
     * <p>Returns a list rather than a stream: {@code Files.walk} holds an open directory
     * handle that has to be closed, and handing an unclosed stream to a caller is a leak
     * waiting to happen. Symlinks are not followed, so a symlink cycle inside a pack
     * folder cannot hang pack loading.</p>
     *
     * @param extensions lowercase extensions including the dot, e.g. {@code ".yml"};
     *                   empty matches every file
     */
    public static @NotNull List<File> listFilesRecursively(@NotNull File root, @NotNull String... extensions) {
        Path rootPath = root.toPath();
        try (Stream<Path> walk = java.nio.file.Files.walk(rootPath)) {
            return walk.filter(java.nio.file.Files::isRegularFile)
                    .filter(path -> matches(path, extensions))
                    .map(Path::toFile)
                    .toList();
        } catch (IOException e) {
            Plugins.logger().warning("Could not list files under " + root + ": " + e.getMessage());
            return List.of();
        }
    }

    private static boolean matches(@NotNull Path path, @NotNull String[] extensions) {
        if (extensions.length == 0) {
            return true;
        }
        String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
        for (String extension : extensions) {
            if (name.endsWith(extension)) {
                return true;
            }
        }
        return false;
    }
}
