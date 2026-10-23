package io.kalo.platform.bedrock;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.kalo.pack.PackMeta;
import io.kalo.pack.ResourcePack;
import io.kalo.pack.ResourcePackImpl;
import io.kalo.pack.Writable;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

class JavaAssetsBedrockTranslatorTest {
    private static ResourcePack pack() { return new ResourcePackImpl(PackMeta.of(0, "unit-test")); }

    @Test
    void convertsVanillaOverridesAndNamespacedItemBlockTextures() throws IOException {
        ResourcePack java = pack();
        byte[] stone = {1, 2, 3};
        byte[] sword = {4, 5, 6};
        java.file("assets/minecraft/textures/block/stone.png", Writable.bytes(stone));
        java.file("assets/minecraft/textures/item/diamond_sword.png", Writable.bytes(sword));
        java.file("assets/cyoria/textures/weapon/katana.png", Writable.bytes(new byte[]{7}));
        ResourcePack bedrock = pack();
        JsonObject items = new JsonObject();
        JsonObject terrain = new JsonObject();
        var report = JavaAssetsBedrockTranslator.translate(java, bedrock, items, terrain);
        assertEquals(3, report.textures());
        assertEquals(2, report.vanillaTextures());
        assertArrayEquals(stone, bedrock.file("textures/blocks/stone.png").toByteArray());
        assertArrayEquals(sword, bedrock.file("textures/items/diamond_sword.png").toByteArray());
        assertNotNull(bedrock.file("textures/kalo/java/cyoria/weapon/katana.png"));
        assertTrue(items.has("java_cyoria_weapon_katana"));
        assertEquals("textures/kalo/java/cyoria/weapon/katana", items
                .getAsJsonObject("java_cyoria_weapon_katana").get("textures").getAsString());
        assertTrue(terrain.has("java_minecraft_block_stone"));
        assertTrue(bedrock.files().keySet().stream().noneMatch(name -> name.startsWith("assets/")));
    }

    @Test
    void longJavaTexturePathsAreNotDroppedAndBedrockZipPathsRemainShort() throws IOException {
        ResourcePack java = pack();
        String longName = "assets/nexo/textures/item/nexo_furniture/connectable/"
                + "connectable_inner_dyeable.png";
        java.file(longName, Writable.bytes(new byte[]{10, 11, 12}));
        ResourcePack bedrock = pack();
        JsonObject items = new JsonObject();
        var report = JavaAssetsBedrockTranslator.translate(java, bedrock, items, new JsonObject());
        assertEquals(1, report.textures());
        String atlasKey = "java_nexo_item_nexo_furniture_connectable_connectable_inner_dyeable";
        String target = items.getAsJsonObject(atlasKey).get("textures").getAsString() + ".png";
        assertTrue(target.startsWith("textures/kalo/converted/"));
        assertTrue(target.length() < 80);
        assertArrayEquals(new byte[]{10, 11, 12}, bedrock.file(target).toByteArray());
        assertTrue(bedrock.files().keySet().stream().allMatch(name -> name.length() < 80));
    }

    @Test
    void translatesLanguageJsonAndSoundDefinitions() throws IOException {
        ResourcePack java = pack();
        java.file("assets/cyoria/lang/th_th.json", Writable.string("{\"item.cyoria.axe\":\"ขวานแห่งดาว\",\"message\":\"หนึ่ง\\nสอง\"}"));
        java.file("assets/cyoria/sounds/login.ogg", Writable.bytes(new byte[]{4, 5, 6}));
        ResourcePack bedrock = pack();
        var report = JavaAssetsBedrockTranslator.translate(java, bedrock, new JsonObject(), new JsonObject());
        assertEquals(1, report.locales());
        assertEquals(2, report.languageEntries());
        assertEquals(1, report.soundFiles());
        String lang = new String(bedrock.file("texts/th_TH.lang").toByteArray(), StandardCharsets.UTF_8);
        assertTrue(lang.contains("item.cyoria.axe=ขวานแห่งดาว"));
        assertTrue(lang.contains("message=หนึ่ง\\nสอง"));
        assertEquals("th_TH", JsonParser.parseString(new String(
                bedrock.file("texts/languages.json").toByteArray(), StandardCharsets.UTF_8))
                .getAsJsonArray().get(0).getAsString());
        JsonObject defs = JsonParser.parseString(new String(bedrock.file("sounds/sound_definitions.json")
                .toByteArray(), StandardCharsets.UTF_8)).getAsJsonObject();
        assertTrue(defs.getAsJsonObject("sound_definitions").has("kalo.java.cyoria.login"));
    }

    @Test
    void doesNotInventRuntimeMappingsOrOverwriteAlreadyGeneratedTextures() throws IOException {
        ResourcePack java = pack();
        java.file("assets/cyoria/models/item/3d_sword.json", Writable.string("{}"));
        java.file("assets/minecraft/textures/item/diamond_sword.png", Writable.bytes(new byte[]{3}));
        ResourcePack bedrock = pack();
        bedrock.file("textures/items/diamond_sword.png", Writable.bytes(new byte[]{9}));
        var report = JavaAssetsBedrockTranslator.translate(java, bedrock, new JsonObject(), new JsonObject());
        assertArrayEquals(new byte[]{9}, bedrock.file("textures/items/diamond_sword.png").toByteArray());
        assertEquals(1, report.unsupportedAssets());
        assertTrue(report.unsupportedExamples().getFirst().contains("3d_sword"));
    }
}
