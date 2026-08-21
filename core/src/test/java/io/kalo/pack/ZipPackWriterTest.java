package io.kalo.pack;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ZipPackWriterTest {

    private static ResourcePack pack() {
        return new ResourcePackImpl(PackMeta.of(PackFormats.CURRENT, "test pack"));
    }

    @Test
    void writesPackMetaFromMeta(@TempDir Path dir) throws IOException {
        ResourcePack pack = pack();

        File out = dir.resolve("pack.zip").toFile();
        ZipPackWriter.write(out, pack);

        JsonObject root = JsonParser.parseString(read(out, "pack.mcmeta")).getAsJsonObject();
        JsonObject packSection = root.getAsJsonObject("pack");

        assertEquals(PackFormats.CURRENT, packSection.get("pack_format").getAsInt());
        assertEquals("test pack", packSection.get("description").getAsString());
        assertEquals(PackFormats.CURRENT,
                packSection.getAsJsonObject("supported_formats").get("min_inclusive").getAsInt());
    }

    @Test
    void writesEachRegisteredFile(@TempDir Path dir) throws IOException {
        ResourcePack pack = pack();
        pack.file("assets/testpack/items/ruby.json", Writable.string("{\"a\":1}"));
        pack.file("assets/testpack/textures/item/ruby.png", Writable.bytes(new byte[]{1, 2, 3}));

        File out = dir.resolve("pack.zip").toFile();
        ZipPackWriter.write(out, pack);

        assertEquals("{\"a\":1}", read(out, "assets/testpack/items/ruby.json"));
        assertArrayEquals(new byte[]{1, 2, 3}, readBytes(out, "assets/testpack/textures/item/ruby.png"));
    }

    @Test
    void producesByteIdenticalOutputForUnchangedInput(@TempDir Path dir) throws IOException {
        // The pack hash is what tells a client to re-download. If regenerating an
        // unchanged pack produced a different zip, every restart would force every
        // player to download it again.
        File first = dir.resolve("first.zip").toFile();
        File second = dir.resolve("second.zip").toFile();

        ZipPackWriter.write(first, populated());
        ZipPackWriter.write(second, populated());

        assertArrayEquals(Files.readAllBytes(first.toPath()), Files.readAllBytes(second.toPath()));
    }

    @Test
    void unchangedOutputIsReportedAsANoOp(@TempDir Path dir) throws IOException {
        File out = dir.resolve("pack.zip").toFile();
        assertTrue(ZipPackWriter.writeIfChanged(out, populated()));
        assertFalse(ZipPackWriter.writeIfChanged(out, populated()));

        ResourcePack changed = populated();
        changed.file("assets/testpack/extra.json", Writable.string("changed"));
        assertTrue(ZipPackWriter.writeIfChanged(out, changed));
    }

    @Test
    void entryOrderDoesNotDependOnInsertionOrder(@TempDir Path dir) throws IOException {
        ResourcePack forward = pack();
        forward.file("assets/a.json", Writable.string("a"));
        forward.file("assets/b.json", Writable.string("b"));

        ResourcePack reversed = pack();
        reversed.file("assets/b.json", Writable.string("b"));
        reversed.file("assets/a.json", Writable.string("a"));

        File first = dir.resolve("forward.zip").toFile();
        File second = dir.resolve("reversed.zip").toFile();
        ZipPackWriter.write(first, forward);
        ZipPackWriter.write(second, reversed);

        assertEquals(entryNames(first), entryNames(second));
    }

    @Test
    void normalizesLeadingSlashesAndBackslashes() {
        ResourcePack pack = pack();
        pack.file("/assets/testpack/a.json", Writable.string("a"));
        pack.file("assets\\testpack\\b.json", Writable.string("b"));

        assertTrue(pack.files().containsKey("assets/testpack/a.json"));
        assertTrue(pack.files().containsKey("assets/testpack/b.json"));
    }

    @Test
    void rejectsTraversalAndEmptyPathSegments() {
        ResourcePack pack = pack();

        assertThrows(IllegalArgumentException.class,
                () -> pack.file("../outside.txt", Writable.string("unsafe")));
        assertThrows(IllegalArgumentException.class,
                () -> pack.file("assets/test//item.json", Writable.string("unsafe")));
        assertThrows(IllegalArgumentException.class,
                () -> pack.file("assets\\test\\..\\outside.txt", Writable.string("unsafe")));
    }

    @Test
    void replacesExistingPackWithoutLeavingATempFile(@TempDir Path dir) throws IOException {
        File out = dir.resolve("pack.zip").toFile();

        ZipPackWriter.write(out, populated());
        long firstSize = out.length();
        ZipPackWriter.write(out, pack());

        assertTrue(out.exists());
        assertTrue(out.length() < firstSize, "second write should have replaced the first");

        try (var entries = Files.list(dir)) {
            assertEquals(List.of("pack.zip"), entries.map(p -> p.getFileName().toString()).sorted().toList());
        }
    }

    private static ResourcePack populated() {
        ResourcePack pack = pack();
        pack.file("assets/testpack/items/ruby.json", Writable.string("{\"model\":1}"));
        pack.file("assets/testpack/lang/en_us.json", Writable.string("{\"item.testpack.ruby\":\"Ruby\"}"));
        return pack;
    }

    private static List<String> entryNames(File zip) throws IOException {
        List<String> names = new ArrayList<>();
        try (ZipFile file = new ZipFile(zip)) {
            var entries = file.entries();
            while (entries.hasMoreElements()) {
                names.add(entries.nextElement().getName());
            }
        }
        return names;
    }

    private static String read(File zip, String entry) throws IOException {
        return new String(readBytes(zip, entry), StandardCharsets.UTF_8);
    }

    private static byte[] readBytes(File zip, String entryName) throws IOException {
        try (ZipFile file = new ZipFile(zip)) {
            ZipEntry entry = file.getEntry(entryName);
            assertNotNull(entry, "missing zip entry: " + entryName);
            try (var input = file.getInputStream(entry)) {
                return input.readAllBytes();
            }
        }
    }
}
