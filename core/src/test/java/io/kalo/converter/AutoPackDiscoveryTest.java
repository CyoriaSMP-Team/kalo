package io.kalo.converter;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.*;

class AutoPackDiscoveryTest {
    @TempDir Path temp;

    private Path pluginData() throws IOException {
        Path folder = temp.resolve("plugins/Kalo");
        Files.createDirectories(folder);
        return folder;
    }

    private Path archive(String relativePath, Map<String, String> entries) throws IOException {
        Path path = temp.resolve("plugins").resolve(relativePath);
        Files.createDirectories(path.getParent());
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(path))) {
            for (var entry : entries.entrySet()) {
                zip.putNextEntry(new ZipEntry(entry.getKey()));
                zip.write(entry.getValue().getBytes(StandardCharsets.UTF_8));
                zip.closeEntry();
            }
        }
        return path;
    }

    private Map<String, String> validPack() {
        Map<String, String> entries = new LinkedHashMap<>();
        entries.put("pack.mcmeta", """
                {"pack":{"pack_format":46,"description":"test"}}""");
        entries.put("assets/example/items/sword.json", """
                {"model":{"type":"minecraft:model","model":"example:item/sword"}}""");
        entries.put("assets/example/textures/item/sword.png", "fake-test-texture");
        return entries;
    }

    @Test
    void discoversRealPackInOtherPluginWithoutSelectingKalogeneratedOutput() throws IOException {
        Path data = pluginData();
        Path pack = archive("ItemsAdder/output/resourcepack.zip", validPack());
        archive("Kalo/generated.zip", validPack());
        archive("Something/backups/old-pack.zip", validPack());
        archive("Other/output/random-archive.zip", validPack());

        var report = AutoPackDiscovery.discover(data);
        assertEquals(1, report.candidates().size());
        assertEquals(pack, report.unique().path());
        assertEquals(2, report.unique().assetCount());
        assertTrue(report.warnings().isEmpty());
    }

    @Test
    void ambiguousPacksNeverChooseOneByGuessing() throws IOException {
        Path data = pluginData();
        archive("ItemsAdder/output/pack.zip", validPack());
        archive("Oraxen/pack/pack.zip", validPack());
        var report = AutoPackDiscovery.discover(data);
        assertEquals(2, report.candidates().size());
        assertNull(report.unique());
    }

    @Test
    void rejectsUnsafeZipButReportsGoodCandidates() throws IOException {
        Path data = pluginData();
        archive("SomePlugin/output/pack.zip", Map.of(
                "pack.mcmeta", "{\"pack\":{}}",
                "../assets/example/illegal.txt", "bad",
                "assets/example/items/a.json", "{}"
        ));
        Path good = archive("OtherPlugin/pack.zip", validPack());
        var report = AutoPackDiscovery.discover(data);
        assertEquals(good, report.unique().path());
        assertEquals(1, report.warnings().size());
        assertTrue(report.warnings().getFirst().contains("Unsafe ZIP path"));
    }

    @Test
    void rejectsPackWithMalformedMetadata() throws IOException {
        Path data = pluginData();
        archive("Test/output/resourcepack.zip", Map.of(
                "pack.mcmeta", "{broken-json",
                "assets/example/items/a.json", "{}"
        ));
        var report = AutoPackDiscovery.discover(data);
        assertTrue(report.candidates().isEmpty());
        assertTrue(report.warnings().getFirst().contains("Malformed pack.mcmeta"));
    }

    @Test
    void ignoresFakeZipsWithoutResourcePackMetadata() throws IOException {
        Path data = pluginData();
        archive("BadPlugin/output/pack.zip", Map.of("assets/test/foo.json","{}"));
        archive("BadPlugin/cache/not-a-pack.zip", validPack());
        var report = AutoPackDiscovery.discover(data);
        assertTrue(report.candidates().isEmpty());
    }

    @Test
    void doesNotFollowSymlinkToPackOutsidePluginsFolder() throws IOException {
        Path data = pluginData();
        Path external = temp.resolve("private-pack.zip");
        try (ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(external))) {
            output.putNextEntry(new ZipEntry("pack.mcmeta"));
            output.write("{\"pack\":{}}".getBytes(StandardCharsets.UTF_8));
            output.closeEntry();
            output.putNextEntry(new ZipEntry("assets/test/foo.json"));
            output.write("{}".getBytes(StandardCharsets.UTF_8));
            output.closeEntry();
        }
        Path link = temp.resolve("plugins/TestPlugin/output/resourcepack.zip");
        Files.createDirectories(link.getParent());
        Files.createSymbolicLink(link, external);
        var report = AutoPackDiscovery.discover(data);
        assertTrue(report.candidates().isEmpty());
    }
}
