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

class JavaResourcePackScannerTest {
    @TempDir
    Path temporary;

    @Test
    void findsModernAndLegacyItemModelsWithoutClaimingRuntimeMappings() throws IOException {
        Map<String, String> assets = new LinkedHashMap<>();
        assets.put("assets/example/items/ruby_sword.json",
                "{\"model\":{\"type\":\"minecraft:condition\",\"property\":\"minecraft:broken\"}}");
        assets.put("assets/example/items/coin.json",
                "{\"model\":{\"type\":\"minecraft:model\",\"model\":\"example:item/coin\"}}");
        assets.put("assets/example/models/item/wand.json",
                "{\"parent\":\"minecraft:item/generated\",\"overrides\":[{\"predicate\":{\"custom_model_data\":1},\"model\":\"example:item/wand\"}]}");
        assets.put("assets/minecraft/blockstates/note_block.json", "{\"variants\":{}}");
        assets.put("assets/example/sounds.json", "{\"chime\":{\"sounds\":[\"example:chime\"]}}");
        assets.put("assets/example/lang/en_us.json", "{\"item.example.coin\":\"Coin\"}");
        assets.put("assets/example/textures/item/coin.png", "binary-placeholder");

        var report = JavaResourcePackScanner.scan(pack("fixtures.zip", assets));
        assertEquals(3, report.itemModels().size());
        assertEquals(JavaResourcePackScanner.ItemModelKind.PLAIN, report.itemModels().get(0).kind());
        assertEquals(JavaResourcePackScanner.ItemModelKind.CONDITION, report.itemModels().get(1).kind());
        assertEquals(JavaResourcePackScanner.ItemModelKind.LEGACY_OVERRIDES,
                report.itemModels().get(2).kind());
        assertEquals("example:wand", report.itemModels().get(2).id());
        assertEquals(1, report.blockStateCandidates().size());
        assertEquals(1, report.soundDefinitions().size());
        assertEquals(1, report.languageFiles().size());
        assertEquals(1, report.texturePaths().size());
        assertTrue(report.warnings().isEmpty());
    }

    @Test
    void malformedJsonIsReportedAndDoesNotKillOtherCandidates() throws IOException {
        var report = JavaResourcePackScanner.scan(pack("invalid.zip", Map.of(
                "assets/test/items/invalid.json", "{bad",
                "assets/test/items/valid.json", "{\"model\":{\"type\":\"minecraft:select\"}}"
        )));
        assertEquals(1, report.itemModels().size());
        assertEquals(JavaResourcePackScanner.ItemModelKind.SELECT,
                report.itemModels().get(0).kind());
        assertEquals(1, report.warnings().size());
        assertTrue(report.warnings().get(0).contains("invalid.json"));
    }

    @Test
    void rejectsTraversalPathsEvenThoughItNeverExtractsFiles() throws IOException {
        Path malicious = pack("unsafe.zip",
                Map.of("../outside.json", "{}", "assets/test/items/valid.json", "{}"));
        assertThrows(IOException.class, () -> JavaResourcePackScanner.scan(malicious));
        assertFalse(Files.exists(temporary.resolve("outside.json")));
    }

    @Test
    void refusesOversizedJsonBeforeParsingIt() throws IOException {
        String oversized = "x".repeat(2 * 1024 * 1024 + 1);
        Path bomb = pack("too-large.zip", Map.of("assets/test/items/large.json", oversized));
        assertThrows(IOException.class, () -> JavaResourcePackScanner.scan(bomb));
    }

    private Path pack(String name, Map<String, String> files) throws IOException {
        Path archive = temporary.resolve(name);
        try (ZipOutputStream stream = new ZipOutputStream(Files.newOutputStream(archive))) {
            for (var entry : files.entrySet()) {
                stream.putNextEntry(new ZipEntry(entry.getKey()));
                stream.write(entry.getValue().getBytes(StandardCharsets.UTF_8));
                stream.closeEntry();
            }
        }
        return archive;
    }
}
