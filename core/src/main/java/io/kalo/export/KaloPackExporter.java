package io.kalo.export;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * A portable, deterministic KaloPack ZIP with pack.yml at its root.
 * Do not follow symlinks out of an owner-controlled pack directory.
 */
public final class KaloPackExporter implements Exporter {
    @Override public String getName() { return "Kalo Pack"; }
    @Override public String getFileExtension() { return "zip"; }
    @Override public boolean canExport(Path source) {
        return Files.isDirectory(source, LinkOption.NOFOLLOW_LINKS)
                && Files.isRegularFile(source.resolve("pack.yml"), LinkOption.NOFOLLOW_LINKS);
    }

    @Override
    public ExportReport export(Path source, Path target) throws IOException {
        if (!canExport(source)) {
            throw new IOException("Kalo pack must be a directory with pack.yml");
        }
        Path sourceRoot = source.toAbsolutePath().normalize();
        Path targetFile = target.toAbsolutePath().normalize();
        if (targetFile.startsWith(sourceRoot)) {
            throw new IOException("Cannot export inside the source pack directory");
        }
        Path parent = targetFile.getParent();
        if (parent == null) throw new IOException("Missing export parent directory");
        Files.createDirectories(parent);
        if (Files.exists(targetFile)) {
            throw new FileAlreadyExistsException(targetFile.toString());
        }

        ExportReport report = new ExportReport();
        Path temp = Files.createTempFile(parent, ".kalo-export-", ".tmp");
        try {
            List<Path> paths;
            try (Stream<Path> walk = Files.walk(sourceRoot)) {
                paths = walk.filter(path ->
                                Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS))
                        .sorted(Comparator.comparing(path -> sourceRoot.relativize(path).toString()))
                        .toList();
            }
            try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(temp))) {
                for (Path path : paths) {
                    String name = sourceRoot.relativize(path).toString().replace('\\', '/');
                    // Sorted relative names and fixed timestamps keep hashes deterministic.
                    ZipEntry entry = new ZipEntry(name);
                    entry.setTime(315532800000L); // 1980-01-01 UTC
                    zip.putNextEntry(entry);
                    Files.copy(path, zip);
                    zip.closeEntry();
                    report.exported(name);
                }
            }
            Files.move(temp, targetFile);
            return report;
        } finally {
            Files.deleteIfExists(temp);
        }
    }
}
