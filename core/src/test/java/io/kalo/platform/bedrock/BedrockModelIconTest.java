package io.kalo.platform.bedrock;

import io.kalo.model.ModelIR;
import io.kalo.model.ModelTextureResolver;
import io.kalo.model.blockbench.BlockbenchImporter;
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
import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BedrockModelIconTest {

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
    void aBlockbenchCubeRendersANonEmptyIsometricPreview() throws Exception {
        ModelIR model;
        try (var stream = BedrockModelIconTest.class.getResourceAsStream(
                "/blockbench/simple_cube.bbmodel")) {
            assertNotNull(stream);
            model = BlockbenchImporter.importJson(
                    new String(stream.readAllBytes(), StandardCharsets.UTF_8),
                    Key.key("testpack", "models/item/simple_cube.bbmodel"), "fixture");
        }
        ResourcePack java = new ResourcePackImpl(PackMeta.of(PackFormats.CURRENT, "java"));
        java.file("assets/testpack/textures/item/ruby.png",
                Writable.bytes(solidPng(16, 16, 0xFFFF0000)));

        ModelTextureResolver.Result textures = ModelTextureResolver.resolve(model,
                Map.of("0", Key.key("testpack", "item/ruby")), "testpack", "testpack_icon");
        byte[] png = BedrockModelIcon.render(model, textures.textures().values().stream()
                .toList(), java);

        assertNotNull(png);
        BufferedImage icon = ImageIO.read(new ByteArrayInputStream(png));
        assertEquals(64, icon.getWidth());
        assertEquals(64, icon.getHeight());
        long opaque = 0;
        for (int y = 0; y < icon.getHeight(); y++) {
            for (int x = 0; x < icon.getWidth(); x++) {
                if ((icon.getRGB(x, y) >>> 24) != 0) {
                    opaque++;
                }
            }
        }
        assertTrue(opaque > 200, "the preview should draw the cube, got " + opaque);
    }
}
