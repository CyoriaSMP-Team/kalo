package io.kalo.export;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.*;
import java.util.zip.ZipFile;
import static org.junit.jupiter.api.Assertions.*;

class KaloPackExporterTest {
    @TempDir Path directory;

    @Test void createsPortableZipWithPackMetadataAssetsAndStableBytes() throws Exception {
        Path pack = directory.resolve("mypack");
        Files.createDirectories(pack.resolve("assets/textures/item"));
        Files.writeString(pack.resolve("pack.yml"), "id: mypack");
        Files.writeString(pack.resolve("configs.yml"), "item: true");
        Files.write(pack.resolve("assets/textures/item/sword.png"), new byte[]{1, 2, 3});
        KaloPackExporter exporter = new KaloPackExporter();
        Path first = directory.resolve("out1.zip"), second = directory.resolve("out2.zip");
        ExportReport report = exporter.export(pack, first);
        exporter.export(pack, second);

        assertEquals(3, report.getExportedCount());
        assertArrayEquals(Files.readAllBytes(first), Files.readAllBytes(second));
        try (ZipFile zip = new ZipFile(first.toFile())) {
            assertNotNull(zip.getEntry("pack.yml"));
            assertNotNull(zip.getEntry("assets/textures/item/sword.png"));
            assertNotNull(zip.getEntry("configs.yml"));
        }
    }

    @Test void refusesOverwriteOrExportInsidePack() throws Exception {
        Path pack = directory.resolve("mypack");
        Files.createDirectories(pack);
        Files.writeString(pack.resolve("pack.yml"), "id: mypack");
        KaloPackExporter exporter = new KaloPackExporter();
        Path output = directory.resolve("mypack.zip");
        exporter.export(pack, output);
        assertThrows(FileAlreadyExistsException.class, () -> exporter.export(pack, output));
        assertThrows(Exception.class, () -> exporter.export(pack, pack.resolve("export.zip")));
    }

    @Test void rejectsDirectoryWithoutPackMetadata() throws Exception {
        Path pack = directory.resolve("invalid");
        Files.createDirectories(pack);
        assertFalse(new KaloPackExporter().canExport(pack));
    }
}
