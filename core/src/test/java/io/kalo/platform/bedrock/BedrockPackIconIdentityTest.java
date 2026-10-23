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
    void blocksPathTraversalAndCreatesFallbackWhenBrandingMissing() throws IOException {
        byte[] icon = BedrockPackIcon.create(dir, "../missing.png", null).toByteArray();
        assertEquals(256, ImageIO.read(new ByteArrayInputStream(icon)).getWidth());
    }

    @Test
    void contentChangesInvalidateBedrockCacheVersionWithoutChangingPackUuid() throws IOException {
        ResourcePack pack = new ResourcePackImpl(PackMeta.of(0, "test"));
        pack.file("manifest.json", Writable.string("{\"format_version\":2,\"header\":{\"uuid\":\"stable\",\"version\":[1,0,0]},\"modules\":[{\"version\":[1,0,0]}]}"));
        pack.file("textures/items/foo.png", Writable.bytes(new byte[]{1}));
        BedrockPackIdentity.refreshVersion(pack);
        JsonObject first = JsonParser.parseString(new String(pack.file("manifest.json").toByteArray(),
                StandardCharsets.UTF_8)).getAsJsonObject();
        int firstVersion = first.getAsJsonObject("header").getAsJsonArray("version").get(2).getAsInt();
        BedrockPackIdentity.refreshVersion(pack);
        JsonObject rerun = JsonParser.parseString(new String(pack.file("manifest.json").toByteArray(),
                StandardCharsets.UTF_8)).getAsJsonObject();
        assertEquals(firstVersion, rerun.getAsJsonObject("header").getAsJsonArray("version").get(2).getAsInt());
        pack.file("textures/items/foo.png", Writable.bytes(new byte[]{2}));
        BedrockPackIdentity.refreshVersion(pack);
        JsonObject changed = JsonParser.parseString(new String(pack.file("manifest.json").toByteArray(),
                StandardCharsets.UTF_8)).getAsJsonObject();
        assertNotEquals(firstVersion, changed.getAsJsonObject("header").getAsJsonArray("version").get(2).getAsInt());
        assertEquals("stable", changed.getAsJsonObject("header").get("uuid").getAsString());
        assertEquals(changed.getAsJsonObject("header").get("version"),
                changed.getAsJsonArray("modules").get(0).getAsJsonObject().get("version"));
    }
}
