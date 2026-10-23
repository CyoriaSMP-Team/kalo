package io.kalo.converter;

import io.kalo.pack.PackMeta;
import io.kalo.pack.ResourcePack;
import io.kalo.pack.ResourcePackImpl;
import io.kalo.pack.Writable;
import io.kalo.platform.bedrock.BedrockPackCompiler;
import org.bukkit.Material;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** A Java authored icon must be the exact PNG Bedrock receives, not a lookalike. */
class JavaPackAutoBindingsTest {
    private static ResourcePack source() {
        ResourcePack source = new ResourcePackImpl(PackMeta.of(46, "Java images"));
        source.file("assets/minecraft/items/diamond_sword.json", Writable.string("""
                {"model":{"type":"minecraft:range_dispatch","property":"minecraft:custom_model_data",
                  "entries":[{"threshold":17641,"model":{"type":"minecraft:model","model":"my_pack:item/sword"}}],
                  "fallback":{"type":"minecraft:model","model":"minecraft:item/diamond_sword"}}}
                """));
        source.file("assets/my_pack/models/item/sword.json", Writable.string("""
                {"parent":"minecraft:item/handheld","textures":{"layer0":"my_pack:item/sword"}}
                """));
        // The vanilla fallback's files deliberately do not appear in Java packs.
        source.file("assets/my_pack/textures/item/sword.png", Writable.bytes(new byte[]{2, 5, 8, 13, 21}));
        source.file("assets/something/items/not_a_material.json", Writable.string("{}"));
        source.file("assets/minecraft/items/../../untrusted.json", Writable.string("{}"));
        return source;
    }

    @Test
    void discoversOnlyKnownMinecraftItemCarriers() {
        var bindings = JavaPackAutoBindings.discover(source());
        assertEquals(1, bindings.size());
        assertEquals(Material.DIAMOND_SWORD, bindings.getFirst().material());
        assertEquals("minecraft:diamond_sword", bindings.getFirst().itemModel().asString());
    }

    @Test
    void convertsTheExactJavaIconWithoutRequiringVanillaFallbackAssets() throws IOException {
        ResourcePack java = source();
        var bindings = JavaPackAutoBindings.discover(java);
        var variants = ExternalModelVariants.resolveAuto(java, bindings.getFirst());
        assertEquals(1, variants.size());
        assertEquals("custom_model_data", variants.getFirst().predicates().getFirst().property());
        assertEquals(17641.0, variants.getFirst().predicates().getFirst().threshold());
        ResourcePack bedrock = new ResourcePackImpl(PackMeta.of(0, "Bedrock"));
        BedrockPackCompiler compiler = new BedrockPackCompiler(java, bedrock);
        assertTrue(compiler.addExternalVariant(variants.getFirst()));
        var result = compiler.finish();
        assertEquals(1, result.itemCount());
        assertEquals("minecraft:diamond_sword", result.itemRegistrations().getFirst().javaItem());
        String name = variants.getFirst().definition().key().namespace() + "_"
                + variants.getFirst().definition().key().value();
        assertNotNull(bedrock.file("textures/items/" + name + ".png"));
        assertArrayEquals(java.file("assets/my_pack/textures/item/sword.png").toByteArray(),
                bedrock.file("textures/items/" + name + ".png").toByteArray());
    }

    @Test
    void failsClosedForAnUnrecognizedSelectorRatherThanMappingWrongItem() {
        ResourcePack java = source();
        java.file("assets/minecraft/items/diamond_sword.json", Writable.string("""
                {"model":{"type":"minecraft:special","base":"minecraft:item/diamond_sword"}}
                """));
        var binding = JavaPackAutoBindings.discover(java).getFirst();
        assertThrows(IllegalArgumentException.class, () -> ExternalModelVariants.resolveAuto(java, binding));
    }
}
