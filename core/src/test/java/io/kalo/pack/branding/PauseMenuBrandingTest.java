package io.kalo.pack.branding;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.kalo.pack.PackFormats;
import io.kalo.pack.PackMeta;
import io.kalo.pack.ResourcePack;
import io.kalo.pack.ResourcePackImpl;
import io.kalo.pack.Writable;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class PauseMenuBrandingTest {
    private static ResourcePack pack() {
        return new ResourcePackImpl(PackMeta.of(PackFormats.CURRENT, "native"));
    }

    private static Path logo(Path dir) throws IOException {
        var folder = Files.createDirectories(dir.resolve("branding"));
        var path = folder.resolve("pause-logo.png");
        var buffered = new BufferedImage(72, 30, BufferedImage.TYPE_INT_ARGB);
        assertTrue(ImageIO.write(buffered, "png", path.toFile()));
        return path;
    }

    private static PauseMenuBranding.Settings settings(Path file) {
        return new PauseMenuBranding.Settings(file, 0xE7C0, 30, 26,
                Map.of("en_us", new PauseMenuBranding.Labels("Back to CyoriaSMP", "Leave CyoriaSMP"),
                       "th_th", new PauseMenuBranding.Labels("กลับสู่ CyoriaSMP", "ออกจาก CyoriaSMP")));
    }

    private static JsonObject json(ResourcePack pack, String path) throws Exception {
        assertNotNull(pack.file(path), path);
        return JsonParser.parseString(new String(pack.file(path).toByteArray(),
                StandardCharsets.UTF_8)).getAsJsonObject();
    }

    @Test
    void generatesNativeMenuWithoutDroppingFontsOrTranslations(@TempDir Path folder) throws Exception {
        ResourcePack p = pack();
        p.file("assets/minecraft/font/default.json", Writable.string(
                "{\"providers\":[{\"type\":\"ttf\",\"file\":\"itim.ttf\"},{\"type\":\"reference\",\"id\":\"nexo:shift\"}]}"));
        p.file("assets/minecraft/lang/en_us.json", Writable.string(
                "{\"item.cyoria.ruby\":\"Ruby\",\"menu.game\":\"Old title\"}"));
        p.file("assets/nexo/models/awesome.json", Writable.string("{\"parent\":\"item/generated\"}"));

        byte[] png = Files.readAllBytes(logo(folder));
        PauseMenuBranding.apply(p, settings(folder.resolve("branding/pause-logo.png")));
        JsonObject font = json(p, "assets/minecraft/font/default.json");
        assertEquals(3, font.getAsJsonArray("providers").size());
        assertEquals("kalo:font/pause_logo.png",
                font.getAsJsonArray("providers").get(0).getAsJsonObject().get("file").getAsString());
        assertEquals("itim.ttf",
                font.getAsJsonArray("providers").get(1).getAsJsonObject().get("file").getAsString());
        assertEquals("nexo:shift",
                font.getAsJsonArray("providers").get(2).getAsJsonObject().get("id").getAsString());

        JsonObject en = json(p, "assets/minecraft/lang/en_us.json");
        assertEquals("\uE7C0", en.get("menu.game").getAsString());
        assertEquals("Ruby", en.get("item.cyoria.ruby").getAsString());
        assertEquals("Back to CyoriaSMP", en.get("menu.returnToGame").getAsString());
        assertEquals("กลับสู่ CyoriaSMP",
                json(p,"assets/minecraft/lang/th_th.json").get("menu.returnToGame").getAsString());
        assertArrayEquals(png,p.file("assets/kalo/textures/font/pause_logo.png").toByteArray());
        assertNotNull(p.file("assets/nexo/models/awesome.json"));
    }

    @Test
    void downscalesOversizedLogoWithoutChangingOriginalFile(@TempDir Path folder) throws Exception {
        Path source = Files.createDirectories(folder.resolve("branding")).resolve("large.png");
        BufferedImage original = new BufferedImage(512, 188, BufferedImage.TYPE_INT_ARGB);
        original.setRGB(20, 20, 0xFF30A0F0);
        assertTrue(ImageIO.write(original, "png", source.toFile()));
        byte[] originalBytes = Files.readAllBytes(source);

        ResourcePack pack = pack();
        PauseMenuBranding.apply(pack, settings(source));
        byte[] generated = pack.file("assets/kalo/textures/font/pause_logo.png").toByteArray();
        BufferedImage decoded = ImageIO.read(new java.io.ByteArrayInputStream(generated));
        assertNotNull(decoded);
        assertEquals(256, decoded.getWidth());
        assertEquals(94, decoded.getHeight());
        assertEquals(0, decoded.getRGB(0, 0) >>> 24, "Transparent background preserved");
        assertTrue((decoded.getRGB(10, 10) >>> 24) > 0, "Visible pixels survive resampling");
        assertArrayEquals(originalBytes, Files.readAllBytes(source), "Source file must remain untouched");
        assertEquals("\uE7C0", json(pack, "assets/minecraft/lang/en_us.json")
                .get("menu.game").getAsString());
    }

    @Test
    void handlesTallOversizedLogo(@TempDir Path folder) throws Exception {
        Path source = folder.resolve("tall.png");
        assertTrue(ImageIO.write(new BufferedImage(120, 600, BufferedImage.TYPE_INT_ARGB), "png", source.toFile()));
        ResourcePack pack = pack();
        PauseMenuBranding.apply(pack, settings(source));
        BufferedImage image = ImageIO.read(new java.io.ByteArrayInputStream(
                pack.file("assets/kalo/textures/font/pause_logo.png").toByteArray()));
        assertEquals(51, image.getWidth());
        assertEquals(256, image.getHeight());
    }

    @Test
    void startsWithVanillaFallbackIfNoFontExists(@TempDir Path dir) throws Exception {
        ResourcePack p = pack();
        PauseMenuBranding.apply(p, settings(logo(dir)));
        JsonObject font = json(p, "assets/minecraft/font/default.json");
        assertEquals(4, font.getAsJsonArray("providers").size());
        assertEquals("minecraft:include/default",
                font.getAsJsonArray("providers").get(2).getAsJsonObject().get("id").getAsString());
    }

    @Test
    void refusesCollisionWithoutPartialPackChanges(@TempDir Path dir) throws Exception {
        ResourcePack p = pack();
        String font = "{\"providers\":[{\"type\":\"bitmap\",\"file\":\"other:font/icon.png\","
                + "\"height\":8,\"ascent\":8,\"chars\":[\"\\uE7C0\"]}]}";
        p.file("assets/minecraft/font/default.json", Writable.string(font));
        assertThrows(IOException.class, () -> PauseMenuBranding.apply(p, settings(logo(dir))));
        assertEquals(font, new String(p.file("assets/minecraft/font/default.json").toByteArray(),
                StandardCharsets.UTF_8));
        assertNull(p.file("assets/kalo/textures/font/pause_logo.png"));
        assertNull(p.file("assets/minecraft/lang/th_th.json"));
    }

    @Test
    void invalidTranslationPreventsAnyChange(@TempDir Path dir) throws Exception {
        ResourcePack p = pack();
        p.file("assets/minecraft/lang/en_us.json", Writable.string("NOT JSON"));
        assertThrows(IOException.class, () -> PauseMenuBranding.apply(p, settings(logo(dir))));
        assertNull(p.file("assets/kalo/textures/font/pause_logo.png"));
    }

    @Test
    void readsNativeConfigAndProtectsLocalPaths(@TempDir Path dir) throws Exception {
        Path png = logo(dir);
        var yml = new YamlConfiguration();
        yml.set("logo", "branding/pause-logo.png");
        yml.set("glyph-codepoint", "E7C0");
        yml.set("height", 30);
        yml.set("ascent", 26);
        yml.set("translations.en_us.return-to-game", "Back to CyoriaSMP");
        yml.set("translations.en_us.disconnect", "Exit");
        var parsed = PauseMenuBranding.settings(yml, dir);
        assertEquals(png.toRealPath(),parsed.logo());
        assertEquals(0xE7C0,parsed.codepoint());
        assertEquals("Exit",parsed.locales().get("en_us").disconnect());
        yml.set("logo", "../not-allowed.png");
        assertThrows(IOException.class, () -> PauseMenuBranding.settings(yml, dir));
    }

    @Test
    void refusesInvalidImageAndKeepsPackUnchanged(@TempDir Path dir) throws Exception {
        Path invalid = Files.createFile(dir.resolve("bad.png"));
        Files.writeString(invalid, "garbage");
        ResourcePack p = pack();
        assertThrows(IOException.class, () -> PauseMenuBranding.apply(p, settings(invalid)));
        assertTrue(p.files().isEmpty());
    }
}
