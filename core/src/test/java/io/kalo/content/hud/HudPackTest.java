package io.kalo.content.hud;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.kalo.content.hud.definition.HudDefinition;
import io.kalo.pack.PackFormats;
import io.kalo.pack.PackMeta;
import io.kalo.pack.ResourcePack;
import io.kalo.pack.ResourcePackImpl;
import io.kalo.pack.Writable;
import io.kalo.platform.bedrock.BedrockGlyphPages;
import net.kyori.adventure.key.Key;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HudPackTest {

    private static byte[] png() throws Exception {
        BufferedImage image = new BufferedImage(64, 32, BufferedImage.TYPE_INT_ARGB);
        image.setRGB(1, 1, 0xFF00FF00);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ImageIO.write(image, "png", output);
        return output.toByteArray();
    }

    private static byte[] offsetPng() throws Exception {
        BufferedImage image = new BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB);
        for (int y = 14; y < 18; y++) {
            for (int x = 14; x < 18; x++) {
                image.setRGB(x, y, 0xFFFF0000);
            }
        }
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ImageIO.write(image, "png", output);
        return output.toByteArray();
    }

    private static HudDefinition definition() {
        return new HudDefinition(Key.key("testpack", "mana"),
                Key.key("testpack", "hud/mana"), HudDefinition.Slot.ACTION_BAR,
                0xE100, 32, 24, Key.key("minecraft", "default"));
    }

    private static JsonObject json(Writable writable) throws Exception {
        return JsonParser.parseString(new String(writable.toByteArray(),
                StandardCharsets.UTF_8)).getAsJsonObject();
    }

    @Test
    void aHudCompilesAJavaFontProviderAndABedrockGlyphPage() throws Exception {
        HudType type = new HudType();
        type.put(definition());
        ResourcePack java = new ResourcePackImpl(PackMeta.of(PackFormats.CURRENT, "java"));
        java.file("assets/testpack/textures/hud/mana.png", Writable.bytes(png()));

        type.compile(java);

        JsonObject font = json(java.file("assets/minecraft/font/default.json"));
        var providers = font.getAsJsonArray("providers");
        JsonObject provider = null;
        for (var element : providers) {
            JsonObject candidate = element.getAsJsonObject();
            if (candidate.has("file")
                    && candidate.get("file").getAsString().equals("testpack:hud/mana.png")) {
                provider = candidate;
            }
        }
        assertNotNull(provider, "HUD file provider missing: " + font);
        assertEquals(24, provider.get("ascent").getAsInt());
        assertEquals(32, provider.get("height").getAsInt());
        assertEquals("\uE100", provider.getAsJsonArray("chars").get(0).getAsString());

        ResourcePack bedrock = new ResourcePackImpl(PackMeta.of(0, "bedrock"));
        int pages = BedrockGlyphPages.write(bedrock, java, type.glyphs().values());

        assertEquals(1, pages);
        assertNotNull(bedrock.file("font/glyph_E1.png"));

        Path artifacts = Path.of("build", "reports", "hud");
        Files.createDirectories(artifacts);
        Files.write(artifacts.resolve("java-font-default.json"),
                java.file("assets/minecraft/font/default.json").toByteArray());
        Files.write(artifacts.resolve("bedrock-glyph_E1.png"),
                bedrock.file("font/glyph_E1.png").toByteArray());
    }

    @Test
    void aHudOffsetAddsASpaceProviderAndShiftsTheBedrockCell() throws Exception {
        HudDefinition offset = new HudDefinition(Key.key("testpack", "offset"),
                Key.key("testpack", "hud/offset"), HudDefinition.Slot.ACTION_BAR,
                0xE100, 32, 24, Key.key("minecraft", "default"), 8);
        HudType type = new HudType();
        type.put(offset);
        ResourcePack java = new ResourcePackImpl(PackMeta.of(PackFormats.CURRENT, "java"));
        java.file("assets/testpack/textures/hud/offset.png",
                Writable.bytes(offsetPng()));

        type.compile(java);

        JsonObject font = json(java.file("assets/minecraft/font/default.json"));
        JsonObject space = null;
        for (var element : font.getAsJsonArray("providers")) {
            JsonObject candidate = element.getAsJsonObject();
            if ("space".equals(candidate.get("type").getAsString())) {
                space = candidate;
            }
        }
        assertNotNull(space, "offset space provider missing: " + font);
        assertEquals(8.0, space.getAsJsonObject("advances")
                .get(offset.offsetGlyph()).getAsDouble());

        ResourcePack bedrock = new ResourcePackImpl(PackMeta.of(0, "bedrock"));
        int pages = BedrockGlyphPages.write(bedrock, java, type.glyphs().values(),
                Map.of(offset.key(), offset.offset()));
        assertEquals(1, pages);
        BufferedImage page = ImageIO.read(new ByteArrayInputStream(
                bedrock.file("font/glyph_E1.png").toByteArray()));
        assertTrue(((page.getRGB(24, 16) >>> 24) & 0xFF) > 0,
                "the Bedrock glyph should shift right by eight pixels");
        assertEquals(0, (page.getRGB(16, 16) >>> 24) & 0xFF,
                "the original glyph position should be empty after the shift");

        Path artifacts = Path.of("build", "reports", "hud");
        Files.createDirectories(artifacts);
        Files.write(artifacts.resolve("java-font-offset.json"),
                java.file("assets/minecraft/font/default.json").toByteArray());
        Files.write(artifacts.resolve("bedrock-glyph_E1-offset.png"),
                bedrock.file("font/glyph_E1.png").toByteArray());
    }
}
