package io.kalo.converter;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;

class GuiCustomSkullScannerTest {
    @TempDir Path root;

    private static String profile(String hash) {
        String json = "{\"textures\":{\"SKIN\":{\"url\":\"http://textures.minecraft.net/texture/"
                + hash + "\"}}}";
        return Base64.getEncoder().encodeToString(json.getBytes(StandardCharsets.UTF_8));
    }

    private static final String A = "abcdef0123456789".repeat(4);
    private static final String B = "0123456789abcdef".repeat(4);

    @Test
    void readsOriginalDeluxeMenusCustomHeadAndDeduplicatesAcrossFiles() throws IOException {
        Path nested = Files.createDirectories(root.resolve("WARP"));
        Files.writeString(nested.resolve("Rtp.yml"), """
                menu_title: '&8RTP'
                items:
                  'rtp_wild':
                    material: basehead-%s
                    slot: 20
                  'rtp_nether':
                    material: 'basehead-%s'
                    slot: 22
                  'rtp_end':
                    material: basehead-%s
                    slot: 24
                """.formatted(profile(A), profile(B), profile(A)));
        Files.writeString(root.resolve("Guide.yaml"), "material: basehead-" + profile(B) + "\n");
        var scan = GuiCustomSkullScanner.discover(root);
        assertEquals(2, scan.skinHashes().size());
        assertEquals(A, scan.skinHashes().get(1));
        assertEquals(B, scan.skinHashes().get(0));
        assertEquals(2, scan.filesScanned());
        assertEquals(0, scan.invalidProfiles());
    }

    @Test
    void rejectsBogusDomainsBrokenBase64AndOversizeYamlWithoutBreakingValidHeads() throws IOException {
        String forged = Base64.getEncoder().encodeToString(("{\"textures\":{\"SKIN\":{\"url\":\"https://textures.minecraft.net.evil/texture/"
                + A + "\"}}}").getBytes(StandardCharsets.UTF_8));
        Files.writeString(root.resolve("broken.yml"), "material: basehead-abc\n"
                + "material: basehead-" + forged + "\n"
                + "material: basehead-" + profile(A) + "\n");
        Files.writeString(root.resolve("huge.yml"), " ".repeat(2 * 1024 * 1024 + 1));
        var scan = GuiCustomSkullScanner.discover(root);
        assertEquals(java.util.List.of(A), scan.skinHashes());
        assertEquals(1, scan.filesScanned());
        assertEquals(2, scan.invalidProfiles());
    }

    @Test
    void absentDeluxeMenusNeedsNoConfigurationOrPackRebuild() throws IOException {
        assertEquals(0, GuiCustomSkullScanner.discover(root.resolve("missing")).skinHashes().size());
        assertEquals(0, GuiCustomSkullScanner.discover(root.resolve("missing")).filesScanned());
    }
}
