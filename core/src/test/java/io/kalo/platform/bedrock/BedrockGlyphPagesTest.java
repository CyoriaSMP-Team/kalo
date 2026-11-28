package io.kalo.platform.bedrock;

import io.kalo.content.glyph.definition.GlyphDefinition;
import io.kalo.pack.PackFormats;
import io.kalo.pack.PackMeta;
import io.kalo.pack.ResourcePack;
import io.kalo.pack.ResourcePackImpl;
import io.kalo.pack.Writable;
import net.kyori.adventure.key.Key;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BedrockGlyphPagesTest {

    private static byte[] solidPng(int width, int height, int argb) throws IOException {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                image.setRGB(x, y, argb);
            }
        }
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ImageIO.write(image, "png", output);
        return output.toByteArray();
    }

    @Test
    void aJavaGlyphBecomesABedrockPageImage() throws Exception {
        ResourcePack java = new ResourcePackImpl(PackMeta.of(PackFormats.CURRENT, "java"));
        java.file("assets/testpack/textures/font/coin.png",
                Writable.bytes(solidPng(16, 16, 0xFFFF0000)));
        ResourcePack bedrock = new ResourcePackImpl(PackMeta.of(0, "bedrock"));

        GlyphDefinition glyph = new GlyphDefinition(Key.key("testpack", "coin"),
                Key.key("testpack", "font/coin"), 0xE000, 8, 16,
                Key.key("minecraft", "default"));
        int pages = BedrockGlyphPages.write(bedrock, java, List.of(glyph));

        assertEquals(1, pages);
        assertNotNull(bedrock.file("font/glyph_E0.png"));
        BufferedImage page = ImageIO.read(new ByteArrayInputStream(
                bedrock.file("font/glyph_E0.png").toByteArray()));
        assertEquals(256, page.getWidth());
        assertEquals(256, page.getHeight());
        assertEquals(0xFFFF0000, page.getRGB(0, 0));
        assertTrue((page.getRGB(15, 15) >>> 24) != 0);
    }

    @Test
    void charactersOutsideThePrivateUseAreaAreNotWritten() throws Exception {
        ResourcePack java = new ResourcePackImpl(PackMeta.of(PackFormats.CURRENT, "java"));
        ResourcePack bedrock = new ResourcePackImpl(PackMeta.of(0, "bedrock"));
        GlyphDefinition glyph = new GlyphDefinition(Key.key("testpack", "plain"),
                Key.key("testpack", "font/plain"), 'A', 8, 16,
                Key.key("minecraft", "default"));

        assertEquals(0, BedrockGlyphPages.write(bedrock, java, List.of(glyph)));
        assertTrue(bedrock.files().isEmpty());
    }
}
