package io.kalo.converter;

import io.kalo.content.item.definition.ModelDefinition;
import io.kalo.pack.PackMeta;
import io.kalo.pack.ResourcePack;
import io.kalo.pack.ResourcePackImpl;
import io.kalo.pack.Writable;
import io.kalo.platform.bedrock.BedrockPackCompiler;
import net.kyori.adventure.key.Key;
import org.bukkit.Material;
import org.junit.jupiter.api.Test;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import java.io.ByteArrayOutputStream;
import java.io.IOException;


import static org.junit.jupiter.api.Assertions.*;

class ExternalItemModelResolverTest {
    private ResourcePack javaPack() {
        ResourcePack pack = new ResourcePackImpl(PackMeta.of(46, "test"));
        pack.file("assets/demo/items/coin.json", Writable.string("""
                {"model":{"type":"minecraft:model","model":"demo:item/coin"}}
                """));
        pack.file("assets/demo/models/item/coin.json", Writable.string("""
                {"parent":"minecraft:item/generated","textures":{"layer0":"demo:item/coin"}}
                """));
        pack.file("assets/demo/textures/item/coin.png", Writable.bytes(new byte[]{1, 2, 3}));
        pack.file("assets/demo/items/wrench.json", Writable.string("""
                {"model":{"type":"minecraft:model","model":"demo:item/wrench"}}
                """));
        pack.file("assets/demo/models/item/wrench.json", Writable.string("""
                {"textures":{"0":"demo:item/wrench"},"elements":[{
                 "from":[0,0,0],"to":[8,8,8],
                 "faces":{"north":{"texture":"#0","uv":[0,0,8,8]}}
                }]}
                """));
        pack.file("assets/demo/textures/item/wrench.png", Writable.bytes(new byte[]{1, 2, 3}));
        pack.file("assets/demo/items/complex.json", Writable.string("""
                {"model":{"type":"minecraft:range_dispatch","property":"minecraft:damage"}}
                """));
        return pack;
    }

    @Test
    void bindsRealMaterialToExistingSpriteWithoutCreatingNativeContent() {
        ResourcePack java = javaPack();
        var binding = new ExternalItemBindings.Binding(Key.key("demo:coin"), Material.PAPER, 16);
        var definition = ExternalItemModelResolver.resolve(java, binding);
        assertNotNull(definition);
        assertEquals(Material.PAPER, definition.java().baseMaterial());
        assertEquals(16, definition.behaviour().maxStackSize());
        assertEquals("demo:coin", definition.key().asString());
        assertInstanceOf(ModelDefinition.Sprite.class, definition.model());
        ResourcePack output = new ResourcePackImpl(PackMeta.of(0, "test"));
        BedrockPackCompiler compiler = new BedrockPackCompiler(java, output);
        assertTrue(compiler.addExternalItem(definition));
        assertFalse(compiler.addExternalItem(definition), "Duplicate model must not create two mappings");
        var result = compiler.finish();
        assertEquals(1, result.itemCount());
        assertEquals("demo:coin", result.itemRegistrations().getFirst().model());
        assertNotNull(result.pack().file("textures/items/demo_coin.png"));
    }

    @Test
    void resolvesStaticThreeDimensionalModelViaExistingCompiler() throws IOException {
        ResourcePack java = javaPack();
        // Real PNG bytes: Bedrock geometry and atlas tests must not pass because
        // they merely found an invalid placeholder texture.
        BufferedImage image = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        ByteArrayOutputStream png = new ByteArrayOutputStream();
        assertTrue(ImageIO.write(image, "png", png));
        java.file("assets/demo/textures/item/wrench.png", Writable.bytes(png.toByteArray()));
        var definition = ExternalItemModelResolver.resolve(java,
                new ExternalItemBindings.Binding(Key.key("demo:wrench"), Material.STICK, 1));
        assertNotNull(definition);
        assertInstanceOf(ModelDefinition.Custom.class, definition.model());
        ResourcePack output = new ResourcePackImpl(PackMeta.of(0, "test"));
        BedrockPackCompiler compiler = new BedrockPackCompiler(java, output);
        assertTrue(compiler.addExternalItem(definition));
        var result = compiler.finish();
        assertEquals(1, result.itemCount());
        assertNotNull(output.file("models/entity/demo_wrench.geo.json"));
        assertNotNull(output.file("attachables/demo_wrench.json"));
        assertEquals("minecraft:stick", result.itemRegistrations().getFirst().javaItem());
    }

    @Test
    void refusesNonRenderableVanillaParentButAllowsAuthoredInheritedGeometry() {
        ResourcePack java = javaPack();
        java.file("assets/demo/models/item/vanilla_rod.json", Writable.string("""
                {"parent":"item/handheld_rod","overrides":[{"predicate":{"cast":1},
                  "model":"demo:item/other"}]}
                """));
        java.file("assets/demo/items/vanilla_rod.json", Writable.string("""
                {"model":{"type":"minecraft:model","model":"demo:item/vanilla_rod"}}
                """));
        assertNull(ExternalItemModelResolver.resolve(java,
                new ExternalItemBindings.Binding(Key.key("demo:vanilla_rod"), Material.FISHING_ROD, 1)));

        java.file("assets/demo/models/item/wrench_child.json", Writable.string("""
                {"parent":"demo:item/wrench","textures":{"0":"demo:item/wrench"}}
                """));
        java.file("assets/demo/items/wrench_child.json", Writable.string("""
                {"model":{"type":"minecraft:model","model":"demo:item/wrench_child"}}
                """));
        var inherited = ExternalItemModelResolver.resolve(java,
                new ExternalItemBindings.Binding(Key.key("demo:wrench_child"), Material.STICK, 1));
        assertNotNull(inherited);
        assertInstanceOf(ModelDefinition.Custom.class, inherited.model());
    }

    @Test
    void refusesPredicatesMissingTexturesAndUnknownModels() {
        var java = javaPack();
        assertNull(ExternalItemModelResolver.resolve(java,
                new ExternalItemBindings.Binding(Key.key("demo:complex"), Material.PAPER, 64)));
        assertNull(ExternalItemModelResolver.resolve(java,
                new ExternalItemBindings.Binding(Key.key("demo:missing"), Material.PAPER, 64)));
        java.removeFile("assets/demo/textures/item/coin.png");
        assertNull(ExternalItemModelResolver.resolve(java,
                new ExternalItemBindings.Binding(Key.key("demo:coin"), Material.PAPER, 64)));
    }
}
