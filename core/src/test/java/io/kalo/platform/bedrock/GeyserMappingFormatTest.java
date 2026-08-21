package io.kalo.platform.bedrock;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.kalo.pack.PackMeta;
import io.kalo.pack.ResourcePack;
import io.kalo.pack.ResourcePackImpl;
import io.kalo.pack.Writable;
import io.kalo.content.block.definition.BlockCarrier;
import io.kalo.platform.java.BlockStateAllocator;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Conformance to Geyser's documented {@code custom_mappings} format.
 *
 * <p>These two files are the entire standalone-Geyser story now that no Kalo code runs
 * inside Geyser, and Geyser is the one that decides whether they parse. It reports a
 * rejected mapping into its own log on a machine Kalo cannot see, so a structural mistake
 * here is invisible from this side — pinning the shape is the only feedback available
 * short of a live Bedrock client.</p>
 *
 * <p>Reference: <a href="https://geysermc.org/wiki/geyser/custom-items/">custom items</a>
 * (format_version 2) and <a href="https://geysermc.org/wiki/geyser/custom-blocks/">custom
 * blocks</a> (format_version 1).</p>
 */
class GeyserMappingFormatTest {

    private static JsonObject json(Writable writable) throws IOException {
        return JsonParser.parseString(new String(writable.toByteArray(), StandardCharsets.UTF_8))
                .getAsJsonObject();
    }

    private static BedrockPackCompiler.Result compileRubyBlock() throws IOException {
        ResourcePack java = new ResourcePackImpl(PackMeta.of(0, "java"));
        java.file("assets/testpack/textures/block/ruby_block.png", Writable.bytes(new byte[]{1}));
        ResourcePack bedrock = new ResourcePackImpl(PackMeta.of(0, "bedrock"));

        BedrockPackCompiler compiler = new BedrockPackCompiler(java, bedrock);
        compiler.addBlocks(
                List.of(BedrockPackCompilerTest.stubCubeAllBlock("ruby_block")),
                key -> new BlockStateAllocator.Assignment(BlockCarrier.NOTE_BLOCK, 7),
                Set.of());
        return compiler.finish();
    }

    @Test
    void theBlockFileIsFormatVersionOneKeyedByJavaBlockState() throws IOException {
        JsonObject blockFile = json(compileRubyBlock().blockMappings());

        assertEquals(1, blockFile.get("format_version").getAsInt());
        assertTrue(blockFile.has("blocks"));
        assertFalse(blockFile.has("items"),
                "items version separately, so they cannot share a file with blocks");

        JsonObject blocks = blockFile.getAsJsonObject("blocks");
        assertEquals(Set.of("minecraft:note_block[instrument=harp,note=3,powered=true]"),
                blocks.keySet(),
                "Geyser keys a block mapping by the Java state it overrides");

        JsonObject entry = blocks.getAsJsonObject(blocks.keySet().iterator().next());
        // "name" is strictly required and must be bare: Geyser namespaces it itself.
        assertTrue(entry.has("name"));
        assertFalse(entry.get("name").getAsString().contains(":"),
                "a mapping names a block, it does not namespace it");

        JsonObject material = entry.getAsJsonObject("material_instances").getAsJsonObject("*");
        assertEquals(Set.of("texture", "render_method", "face_dimming", "ambient_occlusion"),
                material.keySet());
    }

    @Test
    void theItemFileIsFormatVersionTwoKeyedByVanillaItem() throws IOException {
        ResourcePack java = new ResourcePackImpl(PackMeta.of(0, "java"));
        java.file("assets/testpack/textures/item/ruby_sword.png", Writable.bytes(new byte[]{1}));
        ResourcePack bedrock = new ResourcePackImpl(PackMeta.of(0, "bedrock"));

        BedrockPackCompiler compiler = new BedrockPackCompiler(java, bedrock);
        compiler.add(List.of(BedrockPackCompilerTest.stubSpriteItem("ruby_sword")));
        JsonObject itemFile = json(compiler.finish().itemMappings());

        assertEquals(2, itemFile.get("format_version").getAsInt());
        assertFalse(itemFile.has("blocks"));

        JsonObject items = itemFile.getAsJsonObject("items");
        // Keyed by the vanilla item the custom one rides on, valued by an array: several
        // custom items commonly share one base material.
        assertTrue(items.has("minecraft:paper"));
        JsonObject definition = items.getAsJsonArray("minecraft:paper").get(0).getAsJsonObject();

        assertEquals("definition", definition.get("type").getAsString());
        assertTrue(definition.has("model"));
        assertTrue(definition.has("bedrock_identifier"));
        assertTrue(definition.getAsJsonObject("bedrock_options").has("icon"));
    }

    /**
     * Nothing Kalo invented may survive in these files. {@code kalo:blocks} and
     * {@code kalo:geometries} were keys only Kalo's own extension could read, which is
     * what made the extension necessary in the first place.
     */
    @Test
    void neitherFileCarriesAKaloSpecificKey() throws IOException {
        BedrockPackCompiler.Result result = compileRubyBlock();
        for (Writable file : List.of(result.blockMappings(), result.itemMappings())) {
            json(file).keySet().forEach(key ->
                    assertFalse(key.startsWith("kalo:"), "Geyser cannot read " + key));
        }
    }
}
