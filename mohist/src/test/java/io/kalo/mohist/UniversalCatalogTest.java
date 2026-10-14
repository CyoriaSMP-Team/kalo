package io.kalo.mohist;

import com.google.gson.JsonParser;
import io.kalo.mohist.content.BridgeHandoff;
import io.kalo.mohist.content.ContentIndex;
import io.kalo.mohist.content.ContentProvider;
import io.kalo.mohist.content.ForgeRegistrySnapshot;
import io.kalo.mohist.content.MinecraftAssetsProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import static org.junit.jupiter.api.Assertions.*;

class UniversalCatalogTest {
    @TempDir Path temp;

    private Path zip(String relative, Map<String,String> entries) throws IOException {
        Path path = temp.resolve(relative);
        Files.createDirectories(path.getParent());
        try (ZipOutputStream archive=new ZipOutputStream(Files.newOutputStream(path))) {
            for(var entry:entries.entrySet()){
                archive.putNextEntry(new ZipEntry(entry.getKey()));
                archive.write(entry.getValue().getBytes(StandardCharsets.UTF_8));
                archive.closeEntry();
            }
        }
        return path;
    }
    private Map<String,String> modAssets() {
        Map<String,String> content=new LinkedHashMap<>();
        content.put("assets/example/models/item/widget.json","{}");
        content.put("assets/example/blockstates/marble.json","{}");
        content.put("assets/example/textures/item/widget.png","fake");
        content.put("assets/example/textures/item/widget.png.mcmeta","{\"animation\":{}}");
        content.put("assets/example/animations/weapon.animation.json","{}");
        content.put("assets/example/sounds.json","{}");
        content.put("assets/example/lang/en_us.json","{}");
        content.put("data/example/recipes/test.json","{}");
        content.put("META-INF/mods.toml","modLoader=\"javafml\"");
        return content;
    }

    @Test void inventoriesMixedModsForgeIdsAndProviderCategories() throws IOException {
        zip("mods/mod-a.jar",modAssets());
        zip("plugins/Other/output/pack.zip",Map.of(
                "pack.mcmeta","{\"pack\":{\"pack_format\":15}}",
                "assets/example/models/block/marble.json","{}"));
        var source=PackSources.discover(temp);
        var runtime=ForgeRegistrySnapshot.probe(getClass().getClassLoader());
        assertTrue(runtime.warnings().isEmpty(),runtime.warnings().toString());
        var index=ContentIndex.build(temp,source,runtime,getClass().getClassLoader());
        assertTrue(index.complete(),index.warnings().toString());
        assertTrue(index.providers().contains("minecraft-standard-assets"));
        assertTrue(index.counts().get(ContentProvider.Kind.ITEM_MODEL)>0);
        assertTrue(index.counts().get(ContentProvider.Kind.BLOCK_MODEL)>0);
        assertTrue(index.counts().get(ContentProvider.Kind.ANIMATION_METADATA)>0);
        assertTrue(index.counts().get(ContentProvider.Kind.ENTITY_ANIMATION)>0);
        assertTrue(index.counts().get(ContentProvider.Kind.MOD_METADATA)>0);
        assertTrue(index.counts().get(ContentProvider.Kind.RECIPE)>0);
        assertTrue(index.registryLinks().stream().anyMatch(x ->
                x.entry().id().equals("example:widget") && x.hasJavaModel()));
        assertTrue(index.registryLinks().stream().anyMatch(x ->
                x.entry().id().equals("example:marble") && x.hasBlockState()));
        assertTrue(index.registryLinks().stream().anyMatch(x ->
                x.entry().id().equals("example:widget") && x.hasTexture()));
        assertEquals("kalo.content-index/1",index.json().get("schema").getAsString());
    }

    @Test void pluginHandoffNeverClaimsAutomaticDeploymentOrFullProtocolSupport() throws IOException {
        zip("mods/mod-a.jar",modAssets());
        var index=ContentIndex.build(temp,PackSources.discover(temp),
                ForgeRegistrySnapshot.probe(getClass().getClassLoader()),getClass().getClassLoader());
        var out=temp.resolve("output");
        ContentIndex.write(out.resolve("kalo-content-index.json"),index);
        BridgeHandoff.write(out.resolve("kalo-bridge-handoff.json"),index,false);
        var json=JsonParser.parseString(
                Files.readString(out.resolve("kalo-bridge-handoff.json"))).getAsJsonObject();
        assertFalse(json.get("automatic_deployment").getAsBoolean());
        assertFalse(json.get("protocol_support").getAsBoolean());
        assertFalse(json.has("legacy_2d"));
        assertTrue(json.get("index_complete").getAsBoolean());
        assertEquals(index.assets().size(),json.get("indexed_assets").getAsInt());
        assertEquals("kalo.content-index/1",JsonParser.parseString(
                Files.readString(out.resolve("kalo-content-index.json")))
                .getAsJsonObject().get("schema").getAsString());
    }

    @Test void malformedArchiveCannotReplaceAnExistingHandoff() throws IOException {
        Path bad=zip("mods/mod-a.jar",Map.of(
                "assets/example/models/item/widget.json","{}",
                "../escape","bad"));
        var report=PackSources.discover(temp);
        assertTrue(report.mods().isEmpty());
        assertFalse(report.warnings().isEmpty());
        var index=ContentIndex.build(temp,report,new ForgeRegistrySnapshot.Result(List.of(),List.of()),
                getClass().getClassLoader());
        assertTrue(index.assets().isEmpty());
        assertFalse(index.warnings().isEmpty());
    }

    @Test void convertsSingleSheet3DCuboidButRejectsMultiTextureSheets() throws IOException {
        String cube = """
                {"textures":{"0":"demo:item/shape"},"elements":[{
                    "from":[2,1,3],"to":[10,11,14],
                    "faces":{"north":{"texture":"#0","uv":[1,2,9,12]},
                             "south":{"texture":"#0","uv":[1,2,9,12]}}
                }]}""";
        var parsed=com.google.gson.JsonParser.parseString(cube).getAsJsonObject();
        var converted=io.kalo.mohist.content.StaticItemAdapter.adapt(
                parsed,"kalo_mohist:paper_7","kalo_mohist_paper_7");
        assertNotNull(converted);
        assertEquals("demo:item/shape",converted.texture());
        var geometry=converted.geometry().getAsJsonArray("minecraft:geometry")
                .get(0).getAsJsonObject().getAsJsonArray("bones").get(0).getAsJsonObject();
        assertEquals(-2.0,geometry.getAsJsonArray("cubes").get(0)
                .getAsJsonObject().getAsJsonArray("origin").get(0).getAsDouble());
        assertEquals("q.item_slot_to_bone_name(context.item_slot)",
                geometry.get("binding").getAsString());
        assertEquals("kalo_mohist:paper_7",converted.attachable()
                .getAsJsonObject("minecraft:attachable").getAsJsonObject("description")
                .get("identifier").getAsString());
        parsed.getAsJsonObject("textures").addProperty("1","demo:item/alternate");
        parsed.getAsJsonArray("elements").get(0).getAsJsonObject()
                .getAsJsonObject("faces").getAsJsonObject("south")
                .addProperty("texture","#1");
        assertNull(io.kalo.mohist.content.StaticItemAdapter.adapt(
                parsed,"kalo_mohist:paper_7","kalo_mohist_paper_7"));
    }

    @Test void legacyExporterPackagesStatic3DAlongsideLegacyMapping() throws IOException {
        String model = """
                {"textures":{"0":"demo:item/shape"},"elements":[{
                    "from":[1,1,1],"to":[8,12,8],
                    "faces":{"north":{"texture":"#0"}}
                }]}""";
        Map<String,String> entries=new LinkedHashMap<>();
        entries.put("pack.mcmeta","{\"pack\":{\"pack_format\":15}}");
        entries.put("assets/minecraft/models/item/paper.json",
                "{\"overrides\":[{\"predicate\":{\"custom_model_data\":7},\"model\":\"demo:item/shape\"}]}");
        entries.put("assets/demo/models/item/shape.json",model);
        Path pack=zip("plugins/Other/pack.zip",entries);
        // Write a minimal valid PNG header and IHDR fields into the archive fixture.
        byte[] image=java.util.Base64.getDecoder().decode(
                "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVQIHWP4z8DwHwAFgAI/ScL/nwAAAABJRU5ErkJggg==");
        Path assets=temp.resolve("plugins/Other/pack3d.zip");
        try(var stream=new java.util.zip.ZipOutputStream(Files.newOutputStream(assets))){
            for(var entry:entries.entrySet()){
                stream.putNextEntry(new java.util.zip.ZipEntry(entry.getKey()));
                stream.write(entry.getValue().getBytes(java.nio.charset.StandardCharsets.UTF_8));
                stream.closeEntry();
            }
            stream.putNextEntry(new java.util.zip.ZipEntry("assets/demo/textures/item/shape.png"));
            stream.write(image);
            stream.closeEntry();
        }
        var result=LegacyBedrockExporter.convert(List.of(assets),
                List.of(new LegacyBedrockExporter.Observed("paper",7)));
        assertEquals(1,result.converted(),result.skipped().toString());
        assertTrue(result.packEntries().containsKey("models/entity/kalo_mohist_paper_7.geo.json"));
        assertTrue(result.packEntries().containsKey("attachables/kalo_mohist_paper_7.json"));
    }

    @Test void incompleteDiscoveryCannotReplaceExistingContentIndex() throws IOException {
        Path destination=temp.resolve("output/kalo-content-index.json");
        Files.createDirectories(destination.getParent());
        Files.writeString(destination,"previous-good-snapshot");
        var incomplete=new PackSources.Report(List.of(),List.of(),
                List.of("Too many archives; no automatic selection"));
        var index=ContentIndex.build(temp,incomplete,
                new ForgeRegistrySnapshot.Result(List.of(),List.of()),getClass().getClassLoader());
        assertFalse(index.complete());
        assertThrows(IOException.class,()->ContentIndex.write(destination,index));
        assertEquals("previous-good-snapshot",Files.readString(destination));
    }

    @Test void builtinProviderDoesNotPretendUnknownModAssetIsARenderableItem() {
        var standard=new MinecraftAssetsProvider();
        assertEquals(ContentProvider.Kind.ENTITY_MODEL,
                standard.classify("assets/example/models/entity/custom.json"));
        assertEquals(ContentProvider.Kind.OTHER_ASSET,
                standard.classify("assets/example/custom/opaque.meta"));
        assertNull(standard.classify("opaque.bin"));
    }
}
