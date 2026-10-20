package io.kalo.pack.branding;

import com.google.gson.JsonParser;
import io.kalo.pack.PackFormats;
import io.kalo.pack.PackMeta;
import io.kalo.pack.ResourcePack;
import io.kalo.pack.ResourcePackImpl;
import io.kalo.pack.Writable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class NativeGlyphRegistryTest {
    private static ResourcePack blank() {
        return new ResourcePackImpl(PackMeta.of(PackFormats.CURRENT, "test"));
    }
    private static Path png(Path p, int width, int height) throws IOException {
        Files.createDirectories(p.getParent());
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        image.setRGB(width / 2, height / 2, 0xFF25D9FF);
        assertTrue(ImageIO.write(image, "png", p.toFile()));
        return p;
    }
    private static NativeGlyphRegistry.Definition def(String name, Path path, int codepoint) {
        return new NativeGlyphRegistry.Definition(name, path, codepoint, 30, 26);
    }
    @Test
    void readsNamedGlyphsFromYamlAndReplacesPlaceholders(@TempDir Path dir) throws Exception {
        png(dir.resolve("branding/logo.png"), 256, 94);
        Path file = dir.resolve("glyphs/cyoria.yml");
        Files.createDirectories(file.getParent());
        Files.writeString(file, """
                cyoria_logo:
                  texture: branding/logo.png
                  codepoint: E7C0
                  height: 30
                  ascent: 26
                """);
        var loaded = NativeGlyphRegistry.load(dir);
        assertEquals(1, loaded.size());
        var pack = blank();
        Map<String,String> symbols = NativeGlyphRegistry.compile(pack, loaded);
        String title = NativeGlyphRegistry.render("Welcome <glyph:cyoria_logo>!", symbols);
        assertTrue(title.startsWith("Welcome \uE7C0\uE7C1"));
        assertTrue(title.endsWith("!"));
        assertEquals(4, symbols.get("cyoria_logo").length());
        assertThrows(IOException.class, () -> NativeGlyphRegistry.render("<glyph:missing>", symbols));

        var font = JsonParser.parseString(new String(pack.file("assets/minecraft/font/default.json").toByteArray()))
                .getAsJsonObject();
        var first = font.getAsJsonArray("providers").get(0).getAsJsonObject();
        assertEquals("kalo:font/cyoria_logo.png", first.get("file").getAsString());
        assertEquals(symbols.get("cyoria_logo"),
                first.getAsJsonArray("chars").get(0).getAsString());
        var sprite = ImageIO.read(new ByteArrayInputStream(
                pack.file("assets/kalo/textures/font/cyoria_logo.png").toByteArray()));
        assertEquals(256, sprite.getWidth());
        assertEquals(94, sprite.getHeight());
    }

    @Test
    void keepsExistingFontsWhileAddingMultipleNamedGlyphs(@TempDir Path dir) throws Exception {
        ResourcePack pack = blank();
        pack.file("assets/minecraft/font/default.json", Writable.string(
                "{\"providers\":[{\"type\":\"reference\",\"id\":\"nexo:icons\"}]}"));
        var defs = Map.of(
                "cyoria_logo", def("cyoria_logo", png(dir.resolve("logo.png"), 256, 94), 0xE7C0),
                "shop_icon", def("shop_icon", png(dir.resolve("shop.png"), 16, 16), 0xE7D0));
        var symbols = NativeGlyphRegistry.compile(pack, defs);
        assertEquals(4, symbols.get("cyoria_logo").length());
        assertEquals("\uE7D0", symbols.get("shop_icon"));
        var font = JsonParser.parseString(new String(pack.file("assets/minecraft/font/default.json").toByteArray()))
                .getAsJsonObject().getAsJsonArray("providers");
        assertEquals(3, font.size());
        assertEquals("nexo:icons", font.get(2).getAsJsonObject().get("id").getAsString());
    }

    @Test
    void preventsCollisionsAndPartialOutputs(@TempDir Path dir) throws Exception {
        ResourcePack pack = blank();
        var defs = Map.of(
                "cyoria_logo", def("cyoria_logo",png(dir.resolve("logo.png"), 256, 94),0xE7C0),
                "duplicate", def("duplicate",png(dir.resolve("dup.png"), 16, 16),0xE7C1));
        assertThrows(IOException.class, () -> NativeGlyphRegistry.compile(pack, defs));
        assertTrue(pack.files().isEmpty());
    }

    @Test
    void invalidConfigCannotLeaveDataDirectory(@TempDir Path dir) throws Exception {
        Path glyphs=Files.createDirectories(dir.resolve("glyphs"));
        Files.writeString(glyphs.resolve("bad.yml"),"""
                sneak:
                  texture: ../../secrets.png
                  codepoint: E7D2
                """);
        assertThrows(IOException.class, () -> NativeGlyphRegistry.load(dir));
    }
}
