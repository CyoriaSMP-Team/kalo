package io.kalo.pack;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Pack generation runs off the main thread and third-party listeners contribute to it via
 * {@code AsyncResourcePackGenerationEvent}, so the file map is synchronized rather than
 * assuming single-threaded assembly.
 */
public final class ResourcePackImpl implements ResourcePack {
    private final Map<String, Writable> files = Collections.synchronizedMap(new LinkedHashMap<>());
    private volatile PackMeta meta;

    public ResourcePackImpl(@NotNull PackMeta meta) {
        this.meta = meta;
    }

    @Override
    public void file(@NotNull String path, @NotNull Writable content) {
        files.put(normalize(path), content);
    }

    @Override
    public @Nullable Writable file(@NotNull String path) {
        return files.get(normalize(path));
    }

    @Override
    public boolean removeFile(@NotNull String path) {
        return files.remove(normalize(path)) != null;
    }

    @Override
    public @NotNull @Unmodifiable Map<String, Writable> files() {
        synchronized (files) {
            return Map.copyOf(files);
        }
    }

    @Override
    public @NotNull PackMeta meta() {
        return meta;
    }

    @Override
    public void meta(@NotNull PackMeta meta) {
        this.meta = meta;
    }

    /** Zip entries use forward slashes and no leading separator regardless of host OS. */
    private static @NotNull String normalize(@NotNull String path) {
        String normalized = path.replace('\\', '/');
        while (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("Pack file path cannot be empty");
        }
        return normalized;
    }
}
