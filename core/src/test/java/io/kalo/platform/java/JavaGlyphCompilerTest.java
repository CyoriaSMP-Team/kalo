package io.kalo.platform.java;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.kalo.content.glyph.definition.GlyphDefinition;
import io.kalo.content.sound.definition.SoundCategory;
import io.kalo.content.sound.definition.SoundDefinition;
import io.kalo.pack.PackFormats;
import io.kalo.pack.PackMeta;
import io.kalo.pack.ResourcePack;
import io.kalo.pack.ResourcePackImpl;
import io.kalo.pack.Writable;
import net.kyori.adventure.key.Key;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JavaGlyphCompilerTest {

    private static ResourcePack pack() {
        return new ResourcePackImpl(PackMeta.of(PackFormats.CURRENT, "test"));
    }

    private static JsonObject json(ResourcePack pack, String path) throws IOException {
        Writable content = pack.file(path);
        assertNotNull(content, "missing: " + path);
        return JsonParser.parseString(new String(content.toByteArray(), StandardCharsets.UTF_8)).getAsJsonObject();
    }

    private static GlyphDefinition coin(int character) {
        return new GlyphDefinition(Key.key("testpack", "coin"), Key.key("testpack", "font/coin"),
                character, 8, 9, Key.key("minecraft", "default"));
    }

    @Test
    void aGlyphBecomesABitmapProvider() throws IOException {
        ResourcePack pack = pack();
        JavaGlyphCompiler.compileGlyphs(pack, List.of(coin(0xE000)));

        JsonArray providers = json(pack, "assets/minecraft/font/default.json").getAsJsonArray("providers");
        JsonObject bitmap = providers.get(providers.size() - 1).getAsJsonObject();

        assertEquals("bitmap", bitmap.get("type").getAsString());
        assertEquals("testpack:font/coin.png", bitmap.get("file").getAsString());
        assertEquals(8, bitmap.get("ascent").getAsInt());
        assertEquals(9, bitmap.get("height").getAsInt());
        assertEquals("", bitmap.getAsJsonArray("chars").get(0).getAsString());
    }

    @Test
    void writingTheDefaultFontKeepsVanillasOwnProviders() throws IOException {
        // Replacing default.json outright leaves a server where nothing but the custom
        // icons is legible.
        ResourcePack pack = pack();
        JavaGlyphCompiler.compileGlyphs(pack, List.of(coin(0xE000)));

        JsonArray providers = json(pack, "assets/minecraft/font/default.json").getAsJsonArray("providers");
        assertTrue(providers.size() > 1, providers.toString());
        assertEquals("reference", providers.get(0).getAsJsonObject().get("type").getAsString());
    }

    @Test
    void glyphsAppendRatherThanReplaceEachOther() throws IOException {
        ResourcePack pack = pack();
        JavaGlyphCompiler.compileGlyphs(pack, List.of(coin(0xE000)));
        JavaGlyphCompiler.compileGlyphs(pack, List.of(
                new GlyphDefinition(Key.key("testpack", "gem"), Key.key("testpack", "font/gem"),
                        0xE001, 8, 9, Key.key("minecraft", "default"))));

        JsonArray providers = json(pack, "assets/minecraft/font/default.json").getAsJsonArray("providers");
        long bitmaps = providers.asList().stream()
                .filter(p -> p.getAsJsonObject().get("type").getAsString().equals("bitmap"))
                .count();
        assertEquals(2, bitmaps, providers.toString());
    }

    @Test
    void aCustomFontDoesNotGetVanillaIncludes() throws IOException {
        // Only the default font needs them; a dedicated font is meant to contain only
        // what the pack puts there.
        ResourcePack pack = pack();
        JavaGlyphCompiler.compileGlyphs(pack, List.of(
                new GlyphDefinition(Key.key("testpack", "coin"), Key.key("testpack", "font/coin"),
                        0xE000, 8, 9, Key.key("testpack", "icons"))));

        JsonArray providers = json(pack, "assets/testpack/font/icons.json").getAsJsonArray("providers");
        assertEquals(1, providers.size());
    }

    @Test
    void ascentAboveHeightIsRejectedWithTheGlyphNamed() {
        // Vanilla rejects the whole font file for this, taking every other glyph with it.
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> new GlyphDefinition(Key.key("testpack", "bad"), Key.key("testpack", "font/bad"),
                        0xE000, 12, 9, Key.key("minecraft", "default")));
        assertTrue(error.getMessage().contains("ascent"), error.getMessage());
    }

    @Test
    void privateUseAreaMembershipIsKnown() {
        assertTrue(coin(0xE000).usesPrivateUseArea());
        assertTrue(coin(0xF8FF).usesPrivateUseArea());
        // 'A' is a character that also means something in ordinary text.
        assertTrue(!coin('A').usesPrivateUseArea());
    }

    @Test
    void soundEventsAreWrittenPerNamespace() throws IOException {
        ResourcePack pack = pack();
        JavaSoundCompiler.compileSounds(pack, List.of(new SoundDefinition(
                Key.key("testpack", "ambient.cave_wind"),
                List.of(SoundDefinition.SoundFile.of(Key.key("testpack", "ambient/cave_wind"))),
                "subtitles.testpack.cave_wind",
                SoundCategory.AMBIENT)));

        JsonObject sounds = json(pack, "assets/testpack/sounds.json");
        JsonObject event = sounds.getAsJsonObject("ambient.cave_wind");

        assertEquals("ambient", event.get("category").getAsString());
        assertEquals("subtitles.testpack.cave_wind", event.get("subtitle").getAsString());
        assertEquals("testpack:ambient/cave_wind",
                event.getAsJsonArray("sounds").get(0).getAsJsonObject().get("name").getAsString());
    }

    @Test
    void aSecondCompilePassKeepsTheFirstPassEvents() throws IOException {
        // The same shared-file trap as note_block.json and the Bedrock mapping.
        ResourcePack pack = pack();
        JavaSoundCompiler.compileSounds(pack, List.of(new SoundDefinition(
                Key.key("testpack", "a"), List.of(SoundDefinition.SoundFile.of(Key.key("testpack", "a"))),
                null, SoundCategory.MASTER)));
        JavaSoundCompiler.compileSounds(pack, List.of(new SoundDefinition(
                Key.key("testpack", "b"), List.of(SoundDefinition.SoundFile.of(Key.key("testpack", "b"))),
                null, SoundCategory.MASTER)));

        JsonObject sounds = json(pack, "assets/testpack/sounds.json");
        assertTrue(sounds.has("a"), sounds.toString());
        assertTrue(sounds.has("b"), sounds.toString());
    }

    @Test
    void aSoundEventNeedsAtLeastOneFile() {
        assertThrows(IllegalArgumentException.class, () -> new SoundDefinition(
                Key.key("testpack", "empty"), List.of(), null, SoundCategory.MASTER));
    }

    @Test
    void soundValuesAreBounded() {
        assertThrows(IllegalArgumentException.class, () -> new SoundDefinition.SoundFile(
                Key.key("testpack", "x"), 2.0f, 1.0f, 1));
        assertThrows(IllegalArgumentException.class, () -> new SoundDefinition.SoundFile(
                Key.key("testpack", "x"), 1.0f, 0f, 1));
    }

    @Test
    void categoriesRoundTripThroughTheirVanillaNames() {
        assertEquals("ambient", SoundCategory.AMBIENT.id());
        assertEquals(SoundCategory.AMBIENT, SoundCategory.fromId("ambient"));
        assertThrows(IllegalArgumentException.class, () -> SoundCategory.fromId("nope"));
    }
}
