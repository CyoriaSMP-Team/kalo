package io.kalo.converter;

import io.kalo.pack.PackMeta;
import io.kalo.pack.ResourcePack;
import io.kalo.pack.ResourcePackImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;

class GuiHeadIconsTest {
    @TempDir Path root;
    private static final String HASH = "8449b9318e33158e64a46ab0de121c3d40000e3332c1574932b3c849d8fa0dc2";

    private static String material(String hash) {
        String json = "{\"textures\":{\"SKIN\":{\"url\":\"http://textures.minecraft.net/texture/"
                + hash + "\"}}}";
        return "basehead-" + Base64.getEncoder().encodeToString(json.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void rendersOriginalGeyserSkullAtlasFrontFaceAndHatWithoutExposingTheAtlas() throws Exception {
        BufferedImage atlas = new BufferedImage(48, 16, BufferedImage.TYPE_INT_ARGB);
        for (int y=0; y<8; y++) for (int x=0; x<8; x++) {
            atlas.setRGB(8+x,y, 0xff22aa33); // same front face as Java
            atlas.setRGB(8+x,8+y, x<4 ? 0x88ff0000 : 0); // Java outer-layer pixels
        }
        byte[] data = GuiHeadIcons.renderPortrait(atlas);
        BufferedImage converted = ImageIO.read(new ByteArrayInputStream(data));
        assertEquals(64,converted.getWidth());
        assertEquals(64,converted.getHeight());
        assertNotEquals(0xff22aa33,converted.getRGB(0,0));
        assertEquals(0xff22aa33,converted.getRGB(63,63));
        assertEquals(converted.getRGB(0,0),converted.getRGB(7,7));
    }

    @Test
    void javaSkinUsesTheSameFaceAndOverlayAsGeyserPack() throws Exception {
        BufferedImage skin = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
        BufferedImage atlas = new BufferedImage(48, 16, BufferedImage.TYPE_INT_ARGB);
        for (int y=0;y<8;y++) for (int x=0;x<8;x++) {
            int base = x<4 ? 0xff543210 : 0xff128800;
            int layer = y<4 ? 0x88ff00ff : 0;
            skin.setRGB(8+x,8+y,base);
            skin.setRGB(40+x,8+y,layer);
            atlas.setRGB(8+x,y,base);
            atlas.setRGB(8+x,8+y,layer);
        }
        assertArrayEquals(GuiHeadIcons.renderPortrait(skin), GuiHeadIcons.renderPortrait(atlas));
        assertNull(GuiHeadIcons.renderPortrait(new BufferedImage(3,3,BufferedImage.TYPE_INT_ARGB)));
    }

    @Test
    void publishesHeadTextureFromDeluxeMenusIntoActualBedrockResourcePack() throws Exception {
        Path menus=Files.createDirectories(root.resolve("DeluxeMenus/gui_menus/WARP"));
        Files.writeString(menus.resolve("Rtp.yml"),"menu_title: RTP\nitems:\n  '1':\n    material: " + material(HASH) + "\n");
        Path geyser=Files.createDirectories(root.resolve("Geyser-Spigot/cache/player_skulls"));
        BufferedImage atlas = new BufferedImage(48,16,BufferedImage.TYPE_INT_ARGB);
        for (int y=0;y<8;y++) for (int x=0;x<8;x++) atlas.setRGB(8+x,y,0xffffaa00);
        ImageIO.write(atlas,"PNG",geyser.resolve(HASH+".png").toFile());
        ResourcePack output = new ResourcePackImpl(PackMeta.of(0,"Bedrock GUI"));
        int count=GuiHeadIcons.addToPack(output,root,Logger.getLogger("test"));
        assertEquals(1,count);
        assertEquals("textures/ui/kalo_gui_heads/" + HASH.substring(0, 40),
                GuiHeadIcons.iconPathForMaterial(material(HASH)));
        assertTrue((GuiHeadIcons.iconPath(HASH) + ".png").length() < 80,
                "Geyser rejects resource-pack paths with 80 or more characters");
        assertNotNull(output.file(GuiHeadIcons.iconPath(HASH)+".png"));
        assertTrue(Files.isRegularFile(root.resolve("Kalo/gui-icons/"
                + HASH.substring(0, 40) + ".png")));
        assertEquals(0,GuiHeadIcons.addToPack(output,root,Logger.getLogger("test")) - 1);
    }

    @Test
    void reusesExistingFullHashIconWithoutDownloadingOrChangingPixels() throws Exception {
        Path menuDir = Files.createDirectories(root.resolve("DeluxeMenus/gui_menus/PORTAL"));
        Files.writeString(menuDir.resolve("Portal.yml"),
                "items:\n  'head':\n    material: " + material(HASH) + "\n");
        Path cacheDir = Files.createDirectories(root.resolve("Kalo/gui-icons"));
        BufferedImage portrait = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
        portrait.setRGB(0, 0, 0xff123456);
        Path oldCache = cacheDir.resolve(HASH + ".png");
        ImageIO.write(portrait, "PNG", oldCache.toFile());
        byte[] original = Files.readAllBytes(oldCache);

        ResourcePack output = new ResourcePackImpl(PackMeta.of(0, "Bedrock GUI"));
        assertEquals(1, GuiHeadIcons.addToPack(output, root, Logger.getLogger("test")));
        Path compactCache = cacheDir.resolve(HASH.substring(0, 40) + ".png");
        assertArrayEquals(original, Files.readAllBytes(compactCache));
        assertArrayEquals(original, Files.readAllBytes(oldCache),
                "Keep old cache intact so rollback works");
        assertNotNull(output.file(GuiHeadIcons.iconPath(HASH) + ".png"));
        assertTrue((GuiHeadIcons.iconPath(HASH) + ".png").length() < 80);
    }
}
