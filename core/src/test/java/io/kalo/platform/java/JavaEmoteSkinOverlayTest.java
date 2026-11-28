package io.kalo.platform.java;

import io.kalo.pack.ResourcePack;
import io.kalo.pack.ZipPackWriter;
import net.kyori.adventure.key.Key;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class JavaEmoteSkinOverlayTest {

    private static byte[] png() {
        byte[] bytes = new byte[32];
        bytes[0] = (byte) 0x89;
        bytes[1] = 'P';
        bytes[2] = 'N';
        bytes[3] = 'G';
        return bytes;
    }

    private static Map<String, byte[]> unzip(byte[] archive) throws IOException {
        Map<String, byte[]> entries = new LinkedHashMap<>();
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(archive))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                entries.put(entry.getName(), zip.readAllBytes());
            }
        }
        return entries;
    }

    @Test
    void anOverlayContainsOnlyTheEmotesUniqueTexture() throws IOException {
        byte[] skin = png();

        ResourcePack overlay = JavaEmoteSkinOverlay.pack(
                Key.key("testpack", "body_wave"), skin);
        byte[] archive = ZipPackWriter.toBytes(overlay);
        Path artifacts = Path.of("build", "reports", "emote-overlay");
        Files.createDirectories(artifacts);
        Files.write(artifacts.resolve("body_wave-overlay.zip"), archive);
        Map<String, byte[]> entries = unzip(archive);

        assertArrayEquals(skin, entries.get(
                "assets/testpack/textures/emote/body_wave_skin.png"));
        assertNotNull(entries.get("pack.mcmeta"));
        assertEquals(2, entries.size(),
                "the per-player pack must not duplicate the global content");
    }

    @Test
    void theOverlayIdIsStablePerPlayerAndDiffersBetweenPlayers() {
        UUID one = UUID.randomUUID();
        UUID two = UUID.randomUUID();

        assertEquals(JavaEmoteSkinOverlay.id(one), JavaEmoteSkinOverlay.id(one));
        assertNotEquals(JavaEmoteSkinOverlay.id(one), JavaEmoteSkinOverlay.id(two));
    }
}
