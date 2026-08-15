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

/** Serializes a {@link ResourcePack} to a zip file on disk. */
public final class ZipPackWriter {

    /**
     * Fixed timestamp for every entry so that regenerating an unchanged pack produces a
     * byte-identical zip. That makes the pack's hash stable, which matters because
     * clients re-download a pack whenever its hash changes.
     */
    private static final long FIXED_ENTRY_TIME = 0L;

    private ZipPackWriter() {
    }

    /**
     * Writes the pack to {@code destination}, replacing any existing file.
     *
     * <p>Written to a temporary file and moved into place so that a failure partway
     * through cannot leave a truncated pack where a working one used to be.</p>
     */
    public static void write(@NotNull File destination, @NotNull ResourcePack pack) throws IOException {
        Path target = destination.toPath();
        Path parent = target.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }

        Path temp = Files.createTempFile(parent, ".pack-", ".zip.tmp");
        try {
            try (OutputStream fileOut = Files.newOutputStream(temp);
                 ZipOutputStream zip = new ZipOutputStream(new BufferedOutputStream(fileOut))) {

                writeEntry(zip, "pack.mcmeta", Writable.string(packMetaJson(pack.meta())));

                // Sorted so entry order does not depend on map iteration order, for the
                // same reproducibility reason as the fixed timestamps.
                List<Map.Entry<String, Writable>> entries = new ArrayList<>(pack.files().entrySet());
                entries.sort(Map.Entry.comparingByKey());

                for (Map.Entry<String, Writable> entry : entries) {
                    if (entry.getKey().equals("pack.mcmeta")) {
                        continue; // generated from PackMeta above
                    }
                    writeEntry(zip, entry.getKey(), entry.getValue());
                }
            }
            Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException | RuntimeException e) {
            Files.deleteIfExists(temp);
            throw e;
        }
    }

    private static void writeEntry(@NotNull ZipOutputStream zip, @NotNull String path, @NotNull Writable content)
            throws IOException {
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
