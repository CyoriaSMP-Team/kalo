package io.kalo.platform.bedrock;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.kalo.content.item.definition.ModelDefinition;
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
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The regression this exists for: a 3D item's texture is a sheet of UV islands, so using
 * it as the icon showed the whole sheet — a krathong looked like a circuit board.
 */
class JavaModelIconTest {

    @Test
    void rendersTheCubeInsteadOfTheTextureSheet() throws Exception {
        ResourcePack source = new ResourcePackImpl(PackMeta.of(0, "java"));
        source.file("assets/testpack/textures/item/sheet.png", Writable.bytes(sheet()));

        byte[] png = JavaModelIcon.render(cubeModel(), custom(), source);

        assertNotNull(png, "a textured cube must produce an icon");
        BufferedImage icon = ImageIO.read(new ByteArrayInputStream(png));
        assertEquals(64, icon.getWidth());
        assertEquals(64, icon.getHeight());

        int painted = 0;
        for (int y = 0; y < icon.getHeight(); y++) {
            for (int x = 0; x < icon.getWidth(); x++) {
                if ((icon.getRGB(x, y) >>> 24) > 0) {
                    painted++;
                }
            }
        }
        assertTrue(painted > 500, "the projected cube should cover a real area, got " + painted);
        assertTrue(painted < 64 * 64, "the corners must stay transparent");
    }

    @Test
    void missingTextureRendersNothing() throws Exception {
        ResourcePack source = new ResourcePackImpl(PackMeta.of(0, "java"));
        assertNull(JavaModelIcon.render(cubeModel(), custom(), source));
    }

    @Test
    void modelWithoutElementsRendersNothing() throws Exception {
        ResourcePack source = new ResourcePackImpl(PackMeta.of(0, "java"));
        source.file("assets/testpack/textures/item/sheet.png", Writable.bytes(sheet()));
        JsonObject empty = JsonParser.parseString(
                "{\"textures\":{\"0\":\"testpack:item/sheet\"}}").getAsJsonObject();

        assertNull(JavaModelIcon.render(empty, custom(), source));
    }

    private static ModelDefinition.Custom custom() {
        return new ModelDefinition.Custom(Key.key("testpack", "item/thing"), Map.of());
    }

    private static JsonObject cubeModel() {
        return JsonParser.parseString("""
                {"textures":{"0":"testpack:item/sheet"},
                 "elements":[{"from":[0,0,0],"to":[16,16,16],
                   "faces":{
                     "north":{"uv":[0,0,16,16],"texture":"#0"},
                     "south":{"uv":[0,0,16,16],"texture":"#0"},
                     "east":{"uv":[0,0,16,16],"texture":"#0"},
                     "west":{"uv":[0,0,16,16],"texture":"#0"},
                     "up":{"uv":[0,0,16,16],"texture":"#0"},
                     "down":{"uv":[0,0,16,16],"texture":"#0"}}}]}
                """).getAsJsonObject();
    }

    private static byte[] sheet() throws Exception {
        BufferedImage image = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < 16; y++) {
            for (int x = 0; x < 16; x++) {
                image.setRGB(x, y, 0xFF3366CC);
            }
        }
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ImageIO.write(image, "png", output);
        return output.toByteArray();
    }
}
