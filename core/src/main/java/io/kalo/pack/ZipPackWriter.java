package io.kalo.pack;

import com.google.gson.JsonObject;
import org.jetbrains.annotations.NotNull;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/** Serializes a {@link ResourcePack} to a deterministic zip file on disk. */
public final class ZipPackWriter {

    /** Fixed timestamps make unchanged input produce byte-identical output. */
    private static final long FIXED_ENTRY_TIME = 0L;

    private ZipPackWriter() {
    }

    /**
     * Compatibility entry point. Prefer {@link #writeIfChanged(File, ResourcePack)} when
     * the caller needs to know whether clients actually need a new pack.
     */
    public static void write(@NotNull File destination, @NotNull ResourcePack pack) throws IOException {
        writeIfChanged(destination, pack);
    }

    /**
     * Atomically writes the pack only when its bytes differ from the existing file.
     *
     * <p>The pack is deterministic, so {@link Files#mismatch(Path, Path)} is a reliable
     * no-op detector. Avoiding a replacement keeps file watchers quiet and, more
     * importantly, lets the caller keep the same pack URL/hash so Minecraft clients do
     * not re-download content that did not change.</p>
     *
     * @return {@code true} when the destination changed, {@code false} for byte-identical output
     */
    public static boolean writeIfChanged(@NotNull File destination, @NotNull ResourcePack pack) throws IOException {
        Path target = destination.toPath();
        Path parent = target.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }

        Path temp = parent != null
                ? Files.createTempFile(parent, ".pack-", ".zip.tmp")
                : Files.createTempFile(".pack-", ".zip.tmp");
        try {
            writeTo(temp, pack);

            if (Files.exists(target)
                    && Files.size(target) == Files.size(temp)
                    && Files.mismatch(target, temp) == -1L) {
                Files.deleteIfExists(temp);
                return false;
            }

            try {
                Files.move(temp, target,
                        StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE);
            } catch (java.nio.file.AtomicMoveNotSupportedException ignored) {
                Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
            }
            return true;
        } catch (IOException | RuntimeException e) {
            Files.deleteIfExists(temp);
            throw e;
        }
    }

    private static void writeTo(@NotNull Path destination, @NotNull ResourcePack pack) throws IOException {
        try (OutputStream fileOut = Files.newOutputStream(destination);
             ZipOutputStream zip = new ZipOutputStream(new BufferedOutputStream(fileOut))) {

            writeEntry(zip, "pack.mcmeta", Writable.string(packMetaJson(pack.meta())));

            // Sorted so entry order does not depend on map iteration order, for the same
            // reproducibility reason as the fixed timestamps.
            List<Map.Entry<String, Writable>> entries = new ArrayList<>(pack.files().entrySet());
            entries.sort(Map.Entry.comparingByKey());

            for (Map.Entry<String, Writable> entry : entries) {
                if (entry.getKey().equals("pack.mcmeta")) {
                    continue; // generated from PackMeta above
                }
                writeEntry(zip, entry.getKey(), entry.getValue());
            }
        }
    }

    private static void writeEntry(@NotNull ZipOutputStream zip,
                                   @NotNull String path,
                                   @NotNull Writable content) throws IOException {
        byte[] bytes = content.toByteArray();

        ZipEntry entry = new ZipEntry(path);
        entry.setTime(FIXED_ENTRY_TIME);
        entry.setSize(bytes.length);

        CRC32 crc = new CRC32();
        crc.update(bytes);
        entry.setCrc(crc.getValue());

        zip.putNextEntry(entry);
        zip.write(bytes);
        zip.closeEntry();
    }

    static @NotNull String packMetaJson(@NotNull PackMeta meta) {
        JsonObject supportedFormats = new JsonObject();
        supportedFormats.addProperty("min_inclusive", meta.minFormat());
        supportedFormats.addProperty("max_inclusive", meta.maxFormat());

        JsonObject packSection = new JsonObject();
        packSection.addProperty("pack_format", meta.format());
        packSection.addProperty("description", meta.description());
        packSection.add("supported_formats", supportedFormats);

        JsonObject root = new JsonObject();
        root.add("pack", packSection);
        return Json.write(root);
    }
}
