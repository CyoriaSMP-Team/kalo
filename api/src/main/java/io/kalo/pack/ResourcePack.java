package io.kalo.pack;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.util.Map;

/**
 * An in-memory resource pack under construction.
 *
 * <p>Deliberately a flat path-to-content map rather than a typed model of every asset
 * kind Minecraft supports. Resource pack formats change every release; a flat map does
 * not need updating when they do, and compilers can emit whatever the current format
 * requires without waiting on this interface to grow a matching type.</p>
 */
public interface ResourcePack {

    /**
     * Adds or replaces a file.
     *
     * @param path pack-relative, forward-slash separated, e.g.
     *             {@code assets/mypack/items/ruby_sword.json}
     */
    void file(@NotNull String path, @NotNull Writable content);

    @Nullable Writable file(@NotNull String path);

    boolean removeFile(@NotNull String path);

    @NotNull @Unmodifiable Map<String, Writable> files();

    @NotNull PackMeta meta();

    void meta(@NotNull PackMeta meta);
}
