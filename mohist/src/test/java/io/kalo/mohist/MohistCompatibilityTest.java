package io.kalo.mohist;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Base64;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.*;

class MohistCompatibilityTest {
    @TempDir Path temp;
    private static final byte[] PNG = Base64.getDecoder().decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVQIHWP4z8DwHwAFgAI/ScL/nwAAAABJRU5ErkJggg==");

    private Path pack(Path path, boolean valid, boolean dangerous) throws IOException {
        Files.createDirectories(path.getParent());
        try (var zip = new ZipOutputStream(Files.newOutputStream(path))) {
            Map<String,byte[]> data = new LinkedHashMap<>();
            if (valid) data.put("pack.mcmeta","{\"pack\":{\"pack_format\":15}}".getBytes(StandardCharsets.UTF_8));
            data.put("assets/minecraft/models/item/paper.json",("""
                    {"parent":"minecraft:item/generated","overrides":[
                      {"predicate":{"custom_model_data":2},"model":"deadzone:item/coin"},
                      {"predicate":{"custom_model_data":4},"model":"deadzone:item/other"}
                    ]}""").getBytes(StandardCharsets.UTF_8));
            data.put("assets/deadzone/models/item/coin.json",
                    "{\"parent\":\"minecraft:item/generated\",\"textures\":{\"layer0\":\"deadzone:item/coin\"}}"
                        .getBytes(StandardCharsets.UTF_8));
            data.put("assets/deadzone/models/item/other.json",
                    "{\"parent\":\"minecraft:item/generated\",\"textures\":{\"layer0\":\"deadzone:item/other\"}}"
                        .getBytes(StandardCharsets.UTF_8));
            data.put("assets/deadzone/textures/item/coin.png",PNG);
            data.put("assets/deadzone/textures/item/other.png",PNG);
            if (dangerous) data.put("../escape",new byte[]{1});
            for (var entry : data.entrySet()) {
                zip.putNextEntry(new ZipEntry(entry.getKey()));
                zip.write(entry.getValue());
                zip.closeEntry();
            }
        }
        return path;
    }

    @Test void discoversExistingPackAndForgeModWithoutSelectingBackup() throws IOException {
        Path good=pack(temp.resolve("plugins/ItemsAdder/output/resourcepack.zip"),true,false);
        Path mod=pack(temp.resolve("mods/TestMod.jar"),false,false);
        pack(temp.resolve("plugins/Other/backups/resourcepack.zip"),true,false);
        var report=PackSources.discover(temp);
        assertEquals(good,report.uniquePack().path());
        assertEquals(1,report.mods().size());
        assertEquals(mod,report.mods().get(0).path());
        assertTrue(report.warnings().isEmpty());
    }

    @Test void neverGuessesAmongMultiplePacksAndRejectsUnsafeZips() throws IOException {
        pack(temp.resolve("plugins/ItemsAdder/resourcepack.zip"),true,false);
        pack(temp.resolve("plugins/Oraxen/resourcepack.zip"),true,false);
        pack(temp.resolve("plugins/Unsafe/resourcepack.zip"),true,true);
        var report=PackSources.discover(temp);
        assertEquals(2,report.packs().size());
        assertNull(report.uniquePack());
        assertEquals(1,report.warnings().size());
        assertTrue(report.warnings().get(0).contains("Unsafe ZIP path"));
    }

    @Test void producesLegacyCmd2DAssetsAndMappingWithoutTouchingSource() throws IOException {
        Path input=pack(temp.resolve("plugins/ItemsAdder/resourcepack.zip"),true,false);
        var result=LegacyBedrockExporter.convert(List.of(input),
                List.of(new LegacyBedrockExporter.Observed("paper",2)));
        assertEquals(1,result.converted());
        assertEquals(0,result.skipped().size());
        assertTrue(result.packEntries().containsKey("manifest.json"));
        assertTrue(result.packEntries().containsKey("textures/item_texture.json"));
        assertTrue(result.packEntries().containsKey("textures/items/kalo_mohist_paper_2.png"));
        var mapping=result.mapping().getAsJsonObject("items").getAsJsonArray("minecraft:paper")
                .get(0).getAsJsonObject();
        assertEquals("legacy",mapping.get("type").getAsString());
        assertEquals(2,mapping.get("custom_model_data").getAsInt());
        Path exports=temp.resolve("exports");
        LegacyBedrockExporter.write(exports,result);
        assertTrue(Files.size(exports.resolve("kalo-mohist-generated.mcpack"))>100);
        assertEquals(2,JsonParser.parseString(Files.readString(
                exports.resolve("kalo-mohist-items.json")))
                .getAsJsonObject().get("format_version").getAsInt());
        try (var zip=new ZipFile(input.toFile())) {
            assertNotNull(zip.getEntry("pack.mcmeta"));
        }
    }

    @Test void skips3DModelsAndDoesNotOverwriteLastValidExport() throws IOException {
        Path input=pack(temp.resolve("plugins/ItemsAdder/resourcepack.zip"),true,false);
        var first=LegacyBedrockExporter.convert(List.of(input),
                List.of(new LegacyBedrockExporter.Observed("paper",2)));
        Path dir=temp.resolve("exports");
        LegacyBedrockExporter.write(dir,first);
        byte[] existing=Files.readAllBytes(dir.resolve("kalo-mohist-items.json"));
        var invalid=LegacyBedrockExporter.convert(List.of(input),
                List.of(new LegacyBedrockExporter.Observed("paper",1)));
        assertEquals(0,invalid.converted());
        assertThrows(IOException.class,()->LegacyBedrockExporter.write(dir,invalid));
        assertArrayEquals(existing,Files.readAllBytes(dir.resolve("kalo-mohist-items.json")));
    }

    @Test void persistsAndDeduplicatesObservedCmd() throws IOException {
        Path store=temp.resolve("Kalo-Mohist/legacy-observed.properties");
        var item=new LegacyBedrockExporter.Observed("paper",42);
        assertTrue(ObservedStore.load(store).isEmpty());
        assertTrue(ObservedStore.remember(store,item));
        assertFalse(ObservedStore.remember(store,item));
        assertEquals(List.of(item),ObservedStore.load(store));
        Files.writeString(store,"paper:abc=true");
        assertThrows(IOException.class,()->ObservedStore.load(store));
    }
}
