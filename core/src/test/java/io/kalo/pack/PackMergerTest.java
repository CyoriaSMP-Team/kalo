package io.kalo.pack;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PackMergerTest {

    private static ResourcePack pack() {
        return new ResourcePackImpl(PackMeta.of(PackFormats.CURRENT, "test"));
    }

    private static Path basePack(Path dir, Map<String, String> entries) throws IOException {
        Path zip = dir.resolve("base.zip");
        try (OutputStream out = Files.newOutputStream(zip);
             ZipOutputStream zos = new ZipOutputStream(out)) {
            for (Map.Entry<String, String> entry : entries.entrySet()) {
                zos.putNextEntry(new ZipEntry(entry.getKey()));
                zos.write(entry.getValue().getBytes(StandardCharsets.UTF_8));
                zos.closeEntry();
            }
        }
        return zip;
    }

    private static String read(ResourcePack pack, String path) throws IOException {
        Writable content = pack.file(path);
        assertNotNull(content, "missing: " + path);
        return new String(content.toByteArray(), StandardCharsets.UTF_8);
    }

    @Test
    void filesOnlyInTheBasePackCarryOver(@TempDir Path dir) throws IOException {
        // The whole point: a server's existing font or HUD must survive.
        ResourcePack pack = pack();
        Path base = basePack(dir, Map.of("assets/minecraft/font/default.json", "{\"providers\":[]}"));

        assertEquals(1, PackMerger.merge(pack, base.toFile()));
        assertEquals("{\"providers\":[]}", read(pack, "assets/minecraft/font/default.json"));
    }

    @Test
    void kalosGeneratedFileWinsACollision(@TempDir Path dir) throws IOException {
        // Kalo's item definition has to agree with the item_model component the server
        // sends; a base pack overriding it renders as missing texture.
        ResourcePack pack = pack();
        pack.file("assets/testpack/items/ruby.json", Writable.string("{\"kalo\":true}"));
        Path base = basePack(dir, Map.of("assets/testpack/items/ruby.json", "{\"base\":true}"));

        PackMerger.merge(pack, base.toFile());
        assertEquals("{\"kalo\":true}", read(pack, "assets/testpack/items/ruby.json"));
    }

    @Test
    void languageFilesMergeEntryByEntry(@TempDir Path dir) throws IOException {
        // Taking one whole would silently drop the other pack's translations.
        ResourcePack pack = pack();
        pack.file("assets/minecraft/lang/en_us.json", Writable.string("{\"item.kalo.ruby\":\"Ruby\"}"));
        Path base = basePack(dir, Map.of("assets/minecraft/lang/en_us.json",
                "{\"menu.custom\":\"Custom\",\"item.kalo.ruby\":\"Base wins? no\"}"));

        PackMerger.merge(pack, base.toFile());

        JsonObject merged = JsonParser.parseString(read(pack, "assets/minecraft/lang/en_us.json")).getAsJsonObject();
        assertEquals("Custom", merged.get("menu.custom").getAsString(), "base translations must survive");
        assertEquals("Ruby", merged.get("item.kalo.ruby").getAsString(), "Kalo keeps its own key");
    }

    @Test
    void blockStatesMergeSoNeitherPackLosesItsBlocks(@TempDir Path dir) throws IOException {
        // A base pack must not take over a state Kalo has allocated to a custom block.
        ResourcePack pack = pack();
        pack.file("assets/minecraft/blockstates/note_block.json", Writable.string(
                "{\"variants\":{\"instrument=harp,note=0,powered=true\":{\"model\":\"kalo:block/ruby\"}}}"));
        Path base = basePack(dir, Map.of("assets/minecraft/blockstates/note_block.json",
                "{\"variants\":{\"instrument=harp,note=5,powered=false\":{\"model\":\"base:block/x\"},"
                        + "\"instrument=harp,note=0,powered=true\":{\"model\":\"base:should_not_win\"}}}"));

        PackMerger.merge(pack, base.toFile());

        JsonObject variants = JsonParser.parseString(read(pack, "assets/minecraft/blockstates/note_block.json"))
                .getAsJsonObject().getAsJsonObject("variants");
        assertEquals("base:block/x",
                variants.getAsJsonObject("instrument=harp,note=5,powered=false").get("model").getAsString());
        assertEquals("kalo:block/ruby",
                variants.getAsJsonObject("instrument=harp,note=0,powered=true").get("model").getAsString());
    }

    @Test
    void theBasePackMcmetaIsIgnored(@TempDir Path dir) throws IOException {
        // pack.mcmeta describes the merged result and is generated from PackMeta, not
        // taken from whichever pack happened to supply one.
        ResourcePack pack = pack();
        Path base = basePack(dir, Map.of("pack.mcmeta", "{\"pack\":{\"pack_format\":1}}"));

        PackMerger.merge(pack, base.toFile());
        assertNull(pack.file("pack.mcmeta"));
    }

    @Test
    void aMissingBasePackIsNotFatal(@TempDir Path dir) {
        // Losing custom content because a path was mistyped would be a poor trade.
        ResourcePack pack = pack();
        pack.file("assets/testpack/items/ruby.json", Writable.string("{}"));

        assertEquals(0, PackMerger.merge(pack, dir.resolve("absent.zip").toFile()));
        assertNotNull(pack.file("assets/testpack/items/ruby.json"));
    }

    @Test
    void aCorruptBasePackIsNotFatal(@TempDir Path dir) throws IOException {
        Path notAZip = dir.resolve("broken.zip");
        Files.writeString(notAZip, "this is not a zip file");

        ResourcePack pack = pack();
        pack.file("assets/testpack/items/ruby.json", Writable.string("{}"));

        assertEquals(0, PackMerger.merge(pack, notAZip.toFile()));
        assertNotNull(pack.file("assets/testpack/items/ruby.json"));
    }

    @Test
    void unmergeableJsonKeepsTheGeneratedVersion(@TempDir Path dir) throws IOException {
        ResourcePack pack = pack();
        pack.file("assets/minecraft/lang/en_us.json", Writable.string("{\"a\":\"kalo\"}"));
        Path base = basePack(dir, Map.of("assets/minecraft/lang/en_us.json", "not json at all"));

        PackMerger.merge(pack, base.toFile());
        assertEquals("{\"a\":\"kalo\"}", read(pack, "assets/minecraft/lang/en_us.json"));
    }

    @Test
    void directoriesInTheBaseArchiveAreSkipped(@TempDir Path dir) throws IOException {
        Path zip = dir.resolve("base.zip");
        try (OutputStream out = Files.newOutputStream(zip);
             ZipOutputStream zos = new ZipOutputStream(out)) {
            zos.putNextEntry(new ZipEntry("assets/"));
            zos.closeEntry();
            zos.putNextEntry(new ZipEntry("assets/x.json"));
            zos.write("{}".getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();
        }

        ResourcePack pack = pack();
        assertEquals(1, PackMerger.merge(pack, zip.toFile()));
        assertFalse(pack.files().containsKey("assets/"));
    }
}
