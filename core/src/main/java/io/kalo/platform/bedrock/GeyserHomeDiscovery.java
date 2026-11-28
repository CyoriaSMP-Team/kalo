package io.kalo.platform.bedrock;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Finds a Geyser install that runs as a separate process.
 *
 * <p>Kalo can hand its Bedrock pack to Geyser in two ways: through the Geyser API when
 * Geyser shares this JVM, or by writing files into a Geyser home. The second used to
 * require an operator to type an absolute path, which is easy to miss and silently leaves
 * every Bedrock client on vanilla models. A proxy deployment puts that home somewhere
 * near the server directory, so look there first.</p>
 *
 * <p>Nothing is guessed: a candidate only counts when it has Geyser's {@code config.yml},
 * a {@code packs} directory and a {@code custom_mappings} directory, and is writable.
 * The search is bounded to two directory levels under three ancestors of the server
 * directory, so it stays cheap on a large disk.</p>
 */
public final class GeyserHomeDiscovery {

    /** Enough for a proxy one or two levels up, without walking a whole filesystem. */
    private static final int ANCESTORS = 3;
    private static final int MAX_CHILDREN = 128;

    private GeyserHomeDiscovery() {
    }

    /**
     * @param serverDirectory the server root, i.e. the parent of {@code plugins/}
     * @return the first valid Geyser home, in deterministic order
     */
    public static @NotNull Optional<Path> locate(@Nullable Path serverDirectory) {
        if (serverDirectory == null) {
            return Optional.empty();
        }
        Path start = serverDirectory.toAbsolutePath().normalize();
        List<Path> roots = new ArrayList<>(ANCESTORS);
        Path current = start;
        for (int depth = 0; depth < ANCESTORS && current != null; depth++) {
            roots.add(current);
            current = current.getParent();
        }

        List<Path> candidates = new ArrayList<>();
        for (Path root : roots) {
            collectNear(root, candidates);
        }
        candidates.sort(Comparator.comparing(Path::toString));
        return candidates.stream().filter(GeyserHomeDiscovery::isGeyserHome).findFirst();
    }

    /**
     * The server root implied by a plugin data folder, without assuming it is absolute.
     *
     * <p>{@code <server>/plugins/<Plugin>} is two levels up. A relative data folder
     * happens on some loaders, and calling {@code getParent()} twice blindly then returns
     * null — which is how the first auto-discovery attempt broke pack generation.</p>
     */
    public static @NotNull Path serverRootOf(@NotNull Path dataFolder) {
        Path absolute = dataFolder.toAbsolutePath().normalize();
        Path plugins = absolute.getParent();
        if (plugins == null) {
            return absolute;
        }
        Path server = plugins.getParent();
        return server != null ? server : plugins;
    }

    /** Whether this directory can receive {@code kalo-generated.mcpack} and mappings. */
    public static boolean isGeyserHome(@NotNull Path directory) {
        try {
            return Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS)
                    && Files.isRegularFile(directory.resolve("config.yml"),
                            LinkOption.NOFOLLOW_LINKS)
                    && Files.isDirectory(directory.resolve("packs"),
                            LinkOption.NOFOLLOW_LINKS)
                    && Files.isDirectory(directory.resolve("custom_mappings"),
                            LinkOption.NOFOLLOW_LINKS)
                    && Files.isWritable(directory);
        } catch (RuntimeException unreadable) {
            return false;
        }
    }

    private static void collectNear(Path root, List<Path> candidates) {
        consider(root, candidates);
        for (Path child : children(root)) {
            consider(child, candidates);
            for (Path grandchild : children(child)) {
                consider(grandchild, candidates);
            }
        }
    }

    /**
     * Adds any {@code plugins/Geyser-*} directories under {@code base}.
     *
     * <p>A proxy stores Geyser as {@code <proxy>/plugins/Geyser-Velocity}; a backend stores
     * {@code <server>/plugins/Geyser-Spigot}. Both are the same shape.</p>
     */
    private static void consider(Path base, List<Path> candidates) {
        Path plugins = base.resolve("plugins");
        if (!Files.isDirectory(plugins, LinkOption.NOFOLLOW_LINKS)) {
            return;
        }
        for (Path entry : children(plugins)) {
            String name = entry.getFileName().toString();
            if (name.startsWith("Geyser-") && Files.isDirectory(entry, LinkOption.NOFOLLOW_LINKS)) {
                candidates.add(entry);
            }
        }
    }

    private static List<Path> children(Path directory) {
        if (!Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS)) {
            return List.of();
        }
        List<Path> entries = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(directory)) {
            for (Path entry : stream) {
                if (entries.size() >= MAX_CHILDREN) {
                    break;
                }
                entries.add(entry);
            }
        } catch (IOException unreadable) {
            return List.of();
        }
        return entries;
    }
}
