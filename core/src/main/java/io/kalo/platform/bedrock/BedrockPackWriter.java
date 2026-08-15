package io.kalo.platform.bedrock;

import io.kalo.pack.Writable;
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

/** Writes a Bedrock archive without Java's pack.mcmeta entry. */
public final class BedrockPackWriter {
    private BedrockPackWriter() {}

    public static void write(@NotNull File destination, @NotNull Map<String, Writable> files) throws IOException {
        Path target = destination.toPath();
        Path parent = target.getParent();
        if (parent != null) Files.createDirectories(parent);
        Path temp = Files.createTempFile(parent, ".mcpack-", ".tmp");
        try (OutputStream output = Files.newOutputStream(temp);
             ZipOutputStream zip = new ZipOutputStream(new BufferedOutputStream(output))) {
            List<Map.Entry<String, Writable>> entries = new ArrayList<>(files.entrySet());
            entries.removeIf(entry -> entry.getKey().equals("kalo-mappings.json"));
            entries.sort(Map.Entry.comparingByKey());
            for (Map.Entry<String, Writable> entry : entries) {
                byte[] bytes = entry.getValue().toByteArray();
                ZipEntry zipEntry = new ZipEntry(entry.getKey());
                zipEntry.setTime(0L);
                zipEntry.setSize(bytes.length);
                CRC32 crc = new CRC32(); crc.update(bytes); zipEntry.setCrc(crc.getValue());
                zip.putNextEntry(zipEntry); zip.write(bytes); zip.closeEntry();
            }
        } catch (IOException | RuntimeException e) {
            Files.deleteIfExists(temp);
            throw e;
        }
        Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
    }
}
