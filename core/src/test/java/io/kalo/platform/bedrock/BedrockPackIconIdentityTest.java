package io.kalo.platform.bedrock;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.kalo.pack.PackMeta;
import io.kalo.pack.ResourcePack;
import io.kalo.pack.ResourcePackImpl;
import io.kalo.pack.Writable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class BedrockPackIconIdentityTest {
    @TempDir Path dir;

    @Test
    void createsSquarePngFromExistingServerBranding() throws IOException {
        Path logo = dir.resolve("logo.png");
        BufferedImage image = new BufferedImage(120, 60, BufferedImage.TYPE_INT_ARGB);
        image.setRGB(4, 4, Color.MAGENTA.getRGB());
        ImageIO.write(image, "png", logo.toFile());
        byte[] bytes = BedrockPackIcon.create(dir, "logo.png", null).toByteArray();
        BufferedImage result = ImageIO.read(new ByteArrayInputStream(bytes));
        assertEquals(256, result.getWidth());
        assertEquals(256, result.getHeight());
        assertNotEquals(0, result.getRGB(8, 8) >>> 24);
    }

    @Test
    void usesExactlyTheJavaPackIconPixelsByDefaultWithoutPauseMenuBranding() throws IOException {
        BufferedImage javaIcon = new BufferedImage(128,128,BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < 128; y++) for (int x = 0; x < 128; x++) {
            javaIcon.setRGB(x,y,x<64 ? 0xFF123456 : 0xFFFFAA00);
        }
        java.io.ByteArrayOutputStream output = new java.io.ByteArrayOutputStream();
        assertTrue(ImageIO.write(javaIcon,"PNG",output));
        byte[] converted = BedrockPackIcon.create(dir,null,Writable.bytes(output.toByteArray())).toByteArray();
        BufferedImage bedrock = ImageIO.read(new ByteArrayInputStream(converted));
        assertEquals(256,bedrock.getWidth());
        assertEquals(256,bedrock.getHeight());
        for (int y=0;y<128;y++) for (int x=0;x<128;x++) {
            assertEquals(javaIcon.getRGB(x,y),bedrock.getRGB(x*2,y*2));
            assertEquals(javaIcon.getRGB(x,y),bedrock.getRGB(x*2+1,y*2+1));
        }
    }

    @Test
    void blocksPathTraversalAndCreatesFallbackWhenBrandingMissing() throws IOException {
        byte[] icon = BedrockPackIcon.create(dir, "../missing.png", null).toByteArray();
        assertEquals(256, ImageIO.read(new ByteArrayInputStream(icon)).getWidth());
    }

    @Test
    void changedContentRotatesBedrockPackIdentityWithBoundedVersion() throws IOException {
        ResourcePack pack = new ResourcePackImpl(PackMeta.of(0, "test"));
        pack.file("manifest.json", Writable.string("{\"format_version\":2,\"header\":{\"uuid\":\"stable\",\"version\":[1,0,1102407676]},\"modules\":[{\"type\":\"resources\",\"uuid\":\"old-module\",\"version\":[1,0,1102407676]}]}"));
        pack.file("textures/items/foo.png", Writable.bytes(new byte[]{1}));
        BedrockPackIdentity.refreshVersion(pack);
        JsonObject first = manifest(pack);
        String firstUuid = first.getAsJsonObject("header").get("uuid").getAsString();
        String firstModuleUuid = first.getAsJsonArray("modules").get(0).getAsJsonObject().get("uuid").getAsString();
        assertDoesNotThrow(() -> java.util.UUID.fromString(firstUuid));
        assertDoesNotThrow(() -> java.util.UUID.fromString(firstModuleUuid));
        assertNotEquals(firstUuid, firstModuleUuid);
        assertEquals("[1,1,0]", first.getAsJsonObject("header").getAsJsonArray("version").toString());
        assertEquals(first.getAsJsonObject("header").get("version"),
                first.getAsJsonArray("modules").get(0).getAsJsonObject().get("version"));

        BedrockPackIdentity.refreshVersion(pack);
        JsonObject rerun = manifest(pack);
        assertEquals(firstUuid, rerun.getAsJsonObject("header").get("uuid").getAsString());
        assertEquals(firstModuleUuid, rerun.getAsJsonArray("modules").get(0).getAsJsonObject().get("uuid").getAsString());
        pack.file("textures/items/foo.png", Writable.bytes(new byte[]{2}));
        BedrockPackIdentity.refreshVersion(pack);
        JsonObject changed = manifest(pack);
        assertNotEquals(firstUuid, changed.getAsJsonObject("header").get("uuid").getAsString());
        assertNotEquals(firstModuleUuid,
                changed.getAsJsonArray("modules").get(0).getAsJsonObject().get("uuid").getAsString());
        assertEquals("[1,1,0]", changed.getAsJsonObject("header").getAsJsonArray("version").toString());

        // Reverting content yields exactly the first identity; no new cached pack.
        pack.file("textures/items/foo.png", Writable.bytes(new byte[]{1}));
        BedrockPackIdentity.refreshVersion(pack);
        assertEquals(firstUuid, manifest(pack).getAsJsonObject("header").get("uuid").getAsString());
    }

    private static JsonObject manifest(ResourcePack pack) throws IOException {
        return JsonParser.parseString(new String(pack.file("manifest.json").toByteArray(),
                StandardCharsets.UTF_8)).getAsJsonObject();
    }

    @Test
    void missingPackModulesFailsInsteadOfPublishingUnusablePack() {
        ResourcePack pack = new ResourcePackImpl(PackMeta.of(0, "test"));
        pack.file("manifest.json", Writable.string("{\"format_version\":2,\"header\":{\"version\":[1,0,0]}}"));
        assertThrows(IOException.class, () -> BedrockPackIdentity.refreshVersion(pack));
    }
}
