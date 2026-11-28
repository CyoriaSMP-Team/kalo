package io.kalo.content.vfx;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.kalo.content.vfx.definition.VfxDefinition;
import io.kalo.content.vfx.definition.VfxRecipe;
import io.kalo.pack.PackFormats;
import io.kalo.pack.PackMeta;
import io.kalo.pack.ResourcePack;
import io.kalo.pack.ResourcePackImpl;
import io.kalo.platform.java.VfxSpritePainter;
import net.kyori.adventure.key.Key;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VfxPackTest {

    private static VfxDefinition definition() {
        return new VfxDefinition(Key.key("testpack", "frost"), "Frost",
                new VfxRecipe(20, null, List.of(
                        new VfxRecipe.Layer("glow", 0, 20, 20, VfxRecipe.Vec3.ZERO,
                                VfxRecipe.Shape.SPHERE, 1.5, 0.1, 0.3, 0.3, 10, 0,
                                0xFF0000),
                        new VfxRecipe.Layer("streak", 0, 20, 20, VfxRecipe.Vec3.ZERO,
                                VfxRecipe.Shape.RING, 2.0, 0.2, 0.4, 0.25, 12, 0,
                                0x00FF00)), List.of()), "frost_nova");
    }

    @Test
    void cameraLayersWriteTheirOwnSpritesAndModels() throws Exception {
        VfxDefinition definition = new VfxDefinition(Key.key("testpack", "frost"), "Frost",
                new VfxRecipe(20, null, List.of(),
                        List.of(new VfxRecipe.Camera(new VfxRecipe.Layer("spark",
                                0, 20, 4, VfxRecipe.Vec3.ZERO, VfxRecipe.Shape.RING,
                                0.5, 0, 0, 0.2, 10, 0, 0x00FF00), 1.5, 0.1)),
                        List.of(), List.of(), List.of()), null);
        VfxType type = new VfxType();
        type.put(definition);
        ResourcePack pack = new ResourcePackImpl(PackMeta.of(PackFormats.CURRENT, "java"));

        assertEquals(1, type.compile(pack));
        assertEquals("assets/testpack/textures/vfx/frost/camera_0.png",
                VfxSpritePainter.texturePath(definition, -1));
        assertEquals("assets/testpack/models/vfx/frost/camera_0.json",
                VfxSpritePainter.modelPath(definition, -1));
        assertNotNull(pack.file("assets/testpack/textures/vfx/frost/camera_0.png"));
        assertNotNull(pack.file("assets/testpack/models/vfx/frost/camera_0.json"));

        JsonObject model = JsonParser.parseString(new String(
                pack.file("assets/testpack/models/vfx/frost/camera_0.json").toByteArray(),
                StandardCharsets.UTF_8)).getAsJsonObject();
        assertEquals("testpack:vfx/frost/camera_0",
                model.getAsJsonObject("textures").get("layer0").getAsString());

        BufferedImage image = ImageIO.read(new ByteArrayInputStream(
                pack.file("assets/testpack/textures/vfx/frost/camera_0.png").toByteArray()));
        int center = image.getRGB(16, 16);
        assertTrue(((center >>> 24) & 0xFF) > 200, "center must be nearly opaque");
        assertTrue(((center >>> 8) & 0xFF) > 200, "green camera layer must be green");

        Path artifacts = Path.of("build", "reports", "vfx");
        Files.createDirectories(artifacts);
        Files.write(artifacts.resolve("frost-camera-0.png"),
                pack.file("assets/testpack/textures/vfx/frost/camera_0.png").toByteArray());
        Files.writeString(artifacts.resolve("frost-camera-0.json"),
                new String(pack.file("assets/testpack/models/vfx/frost/camera_0.json")
                        .toByteArray(), StandardCharsets.UTF_8));
    }

    @Test
    void compilingAVfxWritesColoredSpritesAndModels() throws Exception {
        VfxType type = new VfxType();
        type.put(definition());
        ResourcePack pack = new ResourcePackImpl(PackMeta.of(PackFormats.CURRENT, "java"));

        assertEquals(2, type.compile(pack));

        String texturePath = "assets/testpack/textures/vfx/frost/0.png";
        String modelPath = "assets/testpack/models/vfx/frost/0.json";
        assertNotNull(pack.file(texturePath));
        assertNotNull(pack.file(modelPath));
        assertEquals(texturePath,
                VfxSpritePainter.texturePath(definition(), 0));
        assertEquals(modelPath,
                VfxSpritePainter.modelPath(definition(), 0));
        assertEquals(Key.key("testpack", "vfx/frost/0"),
                definition().spriteModel(0));

        JsonObject model = JsonParser.parseString(new String(
                pack.file(modelPath).toByteArray(), StandardCharsets.UTF_8))
                .getAsJsonObject();
        assertEquals("minecraft:item/generated", model.get("parent").getAsString());
        assertEquals("testpack:vfx/frost/0",
                model.getAsJsonObject("textures").get("layer0").getAsString());

        BufferedImage image = ImageIO.read(new ByteArrayInputStream(
                pack.file(texturePath).toByteArray()));
        assertEquals(32, image.getWidth());
        assertEquals(32, image.getHeight());
        int center = image.getRGB(16, 16);
        assertEquals(0xFF, (center >>> 24) & 0xFF, "center must be opaque");
        assertTrue(((center >>> 16) & 0xFF) > 200, "red layer must be red");
        assertEquals(0, (image.getRGB(0, 0) >>> 24) & 0xFF,
                "corners must be transparent");

        Path artifacts = Path.of("build", "reports", "vfx");
        Files.createDirectories(artifacts);
        Files.write(artifacts.resolve("frost-0.png"), pack.file(texturePath).toByteArray());
        Files.writeString(artifacts.resolve("frost-0.json"),
                new String(pack.file(modelPath).toByteArray(), StandardCharsets.UTF_8));
    }
}
