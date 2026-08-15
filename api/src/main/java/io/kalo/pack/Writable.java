package io.kalo.pack;

import org.jetbrains.annotations.NotNull;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * A deferred source of bytes for a single file inside a resource pack.
 *
 * <p>Content is produced lazily so that a pack can be assembled from thousands of
 * on-disk textures without holding all of them in memory at once.</p>
 */
@FunctionalInterface
public interface Writable {

    void write(@NotNull OutputStream output) throws IOException;

    default byte @NotNull [] toByteArray() throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        write(buffer);
        return buffer.toByteArray();
    }

    static @NotNull Writable bytes(byte @NotNull [] content) {
        byte[] copy = content.clone();
        return output -> output.write(copy);
    }

    static @NotNull Writable string(@NotNull String content) {
        return bytes(content.getBytes(StandardCharsets.UTF_8));
    }

    static @NotNull Writable path(@NotNull Path path) {
        return output -> {
            try (InputStream input = Files.newInputStream(path)) {
                input.transferTo(output);
            }
        };
    }

    static @NotNull Writable file(@NotNull File file) {
        return path(file.toPath());
    }
}
