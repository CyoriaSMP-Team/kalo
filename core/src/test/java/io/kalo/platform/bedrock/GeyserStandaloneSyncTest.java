package io.kalo.platform.bedrock;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class GeyserStandaloneSyncTest {
    @TempDir Path tmp;

    private Path createGeyser() throws IOException {
        Path geyser = tmp.resolve("geyser");
        Files.createDirectories(geyser.resolve("packs"));
        Files.writeString(geyser.resolve("config.yml"), "bedrock: ok");
        return geyser;
    }

    private Path createKalo() throws IOException {
        Path kalo = tmp.resolve("kalo");
        Files.createDirectories(kalo.resolve("geyser"));
        Files.write(kalo.resolve("generated.mcpack"), new byte[]{1, 2, 3, 4});
        Files.writeString(kalo.resolve("geyser/kalo-items.json"), "{\"format_version\":2,\"items\":{}}");
        Files.writeString(kalo.resolve("geyser/kalo-blocks.json"), "{\"format_version\":1,\"blocks\":{}}");
        return kalo;
    }

    @Test
    void syncCopiesOnlyKaloOwnedFilesAndSkipsUnchangedFiles() throws IOException {
        Path geyser = createGeyser();
        Path kalo = createKalo();
        Files.writeString(geyser.resolve("packs/custom.mcpack"), "untouched");
        Files.createDirectories(geyser.resolve("custom_mappings"));
        Files.writeString(geyser.resolve("custom_mappings/third-party.json"), "untouched");

        GeyserStandaloneSync.Result first = GeyserStandaloneSync.sync(geyser, kalo);
        assertEquals(3, first.updated().size());
        assertArrayEquals(new byte[]{1, 2, 3, 4},
                Files.readAllBytes(geyser.resolve("packs/kalo-generated.mcpack")));
        assertEquals(Files.readString(kalo.resolve("geyser/kalo-items.json")),
                Files.readString(geyser.resolve("custom_mappings/kalo-items.json")));
        assertEquals("untouched", Files.readString(geyser.resolve("packs/custom.mcpack")));
        assertEquals("untouched",
                Files.readString(geyser.resolve("custom_mappings/third-party.json")));

        GeyserStandaloneSync.Result unchanged = GeyserStandaloneSync.sync(geyser, kalo);
        assertFalse(unchanged.changed());

        Files.writeString(kalo.resolve("geyser/kalo-items.json"), "{\"format_version\":2,\"items\":{\"new\":[]}}");
        GeyserStandaloneSync.Result changed = GeyserStandaloneSync.sync(geyser, kalo);
        assertEquals(1, changed.updated().size());
        assertEquals(geyser.resolve("custom_mappings/kalo-items.json").toAbsolutePath(),
                changed.updated().getFirst());
    }

    @Test
    void refusesArbitraryFolderOrMissingSources() throws IOException {
        Path kalo = createKalo();
        Path invalidHome = tmp.resolve("anything");
        Files.createDirectories(invalidHome.resolve("packs"));
        assertThrows(IOException.class, () -> GeyserStandaloneSync.sync(invalidHome, kalo));
        Path geyser = createGeyser();
        Files.delete(kalo.resolve("geyser/kalo-blocks.json"));
        assertThrows(IOException.class, () -> GeyserStandaloneSync.sync(geyser, kalo));
        assertFalse(Files.exists(geyser.resolve("packs/kalo-generated.mcpack")),
                "all three sources are preflighted before writing any target");
    }

    @Test
    void rejectsSymlinkDestinationWithoutWritingThroughIt() throws IOException {
        Path geyser = createGeyser();
        Path kalo = createKalo();
        Path marker = tmp.resolve("outside.txt");
        Files.writeString(marker, "untouched");
        Files.createDirectories(geyser.resolve("custom_mappings"));
        try {
            Files.createSymbolicLink(geyser.resolve("custom_mappings/kalo-items.json"), marker);
        } catch (UnsupportedOperationException | java.nio.file.FileSystemException unsupported) {
            return;
        }
        assertThrows(IOException.class, () -> GeyserStandaloneSync.sync(geyser, kalo));
        assertEquals("untouched", Files.readString(marker));
        assertFalse(Files.exists(geyser.resolve("packs/kalo-generated.mcpack")));
    }
}
