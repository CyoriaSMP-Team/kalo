package io.kalo.platform.bedrock;

import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

/**
 * Opt-in standalone Geyser resource-pack and custom mapping synchronization.
 * This deliberately does not restart Geyser or claim mappings hot-reload.
 * Only three Kalo-owned filenames in Geyser's packs and custom_mappings
 * directories are ever modified; all other files belong to Geyser/the user.
 */
public final class GeyserStandaloneSync {
    private GeyserStandaloneSync() {}

    private static final long MAX_SOURCE_BYTES = 128L * 1024 * 1024;
    private static final String KALO_PACK = "kalo-generated.mcpack";
    private static final String ITEMS = "kalo-items.json";
    private static final String BLOCKS = "kalo-blocks.json";

    public record Result(@NotNull List<Path> updated) {
        public Result { updated = List.copyOf(updated); }
        public boolean changed() { return !updated.isEmpty(); }
    }

    /**
     * @param home existing standalone Geyser root containing config.yml and packs/
     * @param kaloData Kalo's own data directory containing generated.mcpack and geyser/
     */
    public static @NotNull Result sync(@NotNull Path home, @NotNull Path kaloData)
            throws IOException {
        Path target = home.toAbsolutePath().normalize();
        Path source = kaloData.toAbsolutePath().normalize();
        ensureDirectory(target);
        if (!Files.isRegularFile(target.resolve("config.yml"), LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("Not a Geyser home: missing config.yml in " + target);
        }
        Path packs = target.resolve("packs");
        ensureDirectory(packs);
        Path mappings = target.resolve("custom_mappings");
        if (Files.notExists(mappings, LinkOption.NOFOLLOW_LINKS)) {
            Files.createDirectory(mappings);
        }
        ensureDirectory(mappings);

        Path sourcePack = source.resolve("generated.mcpack");
        Path sourceItems = source.resolve("geyser").resolve(ITEMS);
        Path sourceBlocks = source.resolve("geyser").resolve(BLOCKS);

        // Preflight *all* sources and destinations before changing any file.
        preflight(sourcePack, packs.resolve(KALO_PACK));
        preflight(sourceItems, mappings.resolve(ITEMS));
        preflight(sourceBlocks, mappings.resolve(BLOCKS));

        List<Path> updated = new ArrayList<>(3);
        copyIfChanged(sourcePack, packs.resolve(KALO_PACK), updated);
        copyIfChanged(sourceItems, mappings.resolve(ITEMS), updated);
        copyIfChanged(sourceBlocks, mappings.resolve(BLOCKS), updated);
        return new Result(updated);
    }

    private static void ensureDirectory(Path directory) throws IOException {
        if (!Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("Expected real directory (not symlink): " + directory);
        }
    }

    private static void preflight(Path source, Path destination) throws IOException {
        if (!Files.isRegularFile(source, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("Missing regular Kalo source file: " + source);
        }
        long bytes = Files.size(source);
        if (bytes < 1 || bytes > MAX_SOURCE_BYTES) {
            throw new IOException("Invalid Kalo file size: " + source + " (" + bytes + " bytes)");
        }
        if (Files.exists(destination, LinkOption.NOFOLLOW_LINKS)
                && !Files.isRegularFile(destination, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("Unsafe output target: " + destination);
        }
    }

    private static void copyIfChanged(Path source, Path target, List<Path> updated)
            throws IOException {
        if (Files.exists(target, LinkOption.NOFOLLOW_LINKS)
                && Files.mismatch(source, target) == -1L) {
            return;
        }
        Path temporary = Files.createTempFile(target.getParent(), ".kalo-stage-", ".tmp");
        try {
            Files.copy(source, temporary, StandardCopyOption.REPLACE_EXISTING);
            try {
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException unsupported) {
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
            }
            updated.add(target);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }
}
