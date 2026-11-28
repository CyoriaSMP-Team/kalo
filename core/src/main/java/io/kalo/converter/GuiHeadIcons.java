package io.kalo.converter;

import io.kalo.pack.ResourcePack;
import io.kalo.pack.Writable;
import org.jetbrains.annotations.NotNull;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URL;
import java.net.URLConnection;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * One source of truth for Java DeluxeMenus head images in Bedrock Forms.
 *
 * Geyser caches an unwrapped 48x16 skull atlas with front face at (8,0)
 * and the front hat/overlay at (8,8). Bedrock Forms cannot render that atlas
 * as a head icon, so produce a nearest-neighbor front portrait in Kalo's pack.
 * On a cache miss, read the same original Java skin from textures.minecraft.net
 * with strict bounds; failures simply omit a decorative icon.
 */
public final class GuiHeadIcons {
    private static final int ICON_SIZE = 64;
    private static final int MAX_DOWNLOAD_BYTES = 512 * 1024;
    private static final int MAX_NEW_FETCHES_PER_BUILD = 16;
    private static final String ICON_DIR = "textures/ui/kalo_gui_heads/";
    // Bedrock resource packs warn at 80+ characters per path. The 40-digit
    // prefix keeps the full path (including .png) at 71 characters while
    // retaining 160 bits of skin-hash identity. Cache source lookups still
    // use the original complete Minecraft texture hash.
    private static final int PACK_HASH_LENGTH = 40;

    private GuiHeadIcons() {}

    public static String iconPath(String hash) {
        if (hash == null || !hash.matches("[a-f0-9]{40,128}")) return null;
        return ICON_DIR + hash.substring(0, PACK_HASH_LENGTH);
    }

    public static String iconPathForMaterial(String material) {
        String hash = GuiCustomSkullScanner.decodeMaterialHash(material);
        return iconPath(hash);
    }

    /** Add converted icons before BedrockPackIdentity is refreshed. */
    public static int addToPack(@NotNull ResourcePack pack, @NotNull Path pluginsDirectory,
                                @NotNull Logger logger) {
        Path menuDir = pluginsDirectory.resolve("DeluxeMenus/gui_menus");
        Path outputDir = pluginsDirectory.resolve("Kalo/gui-icons");
        Path geyserCache = pluginsDirectory.resolve("Geyser-Spigot/cache/player_skulls");
        try {
            var scan = GuiCustomSkullScanner.discover(menuDir);
            Files.createDirectories(outputDir);
            int count = 0;
            int attemptedDownloads = 0;
            for (String hash : scan.skinHashes()) {
                try {
                    Path savedIcon = outputDir.resolve(hash.substring(0, PACK_HASH_LENGTH) + ".png");
                    byte[] icon = readCachedIcon(savedIcon);
                    if (icon == null) {
                        // Migrate the full-hash cache written by previous Kalo
                        // versions so existing Java-matched head portraits
                        // survive without a network fetch or any visual change.
                        icon = readCachedIcon(outputDir.resolve(hash + ".png"));
                    }
                    if (icon == null) {
                        Path cachedAtlas = geyserCache.resolve(hash + ".png");
                        BufferedImage source = null;
                        if (Files.isRegularFile(cachedAtlas) && Files.size(cachedAtlas) < MAX_DOWNLOAD_BYTES) {
                            source = ImageIO.read(cachedAtlas.toFile());
                        }
                        if (source == null && attemptedDownloads++ < MAX_NEW_FETCHES_PER_BUILD) {
                            source = downloadSkin(hash);
                        }
                        if (source == null) continue;
                        icon = renderPortrait(source);
                        if (icon == null) continue;
                    }
                    // The Bedrock Form checks for this same short filename.
                    // Keep old full-hash cache files untouched for rollback.
                    if (!Files.isRegularFile(savedIcon)
                            || !java.util.Arrays.equals(Files.readAllBytes(savedIcon), icon)) {
                        Files.write(savedIcon, icon);
                    }
                    pack.file(iconPath(hash) + ".png", Writable.bytes(icon));
                    count++;
                } catch (Exception invalid) {
                    logger.log(Level.FINE, "Could not render GUI head " + hash, invalid);
                }
            }
            logger.info("Kalo generated " + count + " Bedrock Form head icon(s) from Java DeluxeMenus");
            return count;
        } catch (IOException scanFailure) {
            logger.log(Level.WARNING, "Could not generate Bedrock Form head icons", scanFailure);
            return 0;
        }
    }

    private static byte[] readCachedIcon(Path file) throws IOException {
        if (!Files.isRegularFile(file) || Files.size(file) >= 128 * 1024) return null;
        byte[] bytes = Files.readAllBytes(file);
        BufferedImage image = ImageIO.read(new ByteArrayInputStream(bytes));
        return image != null && image.getWidth() == ICON_SIZE
                && image.getHeight() == ICON_SIZE ? bytes : null;
    }

    private static BufferedImage downloadSkin(String hash) throws IOException {
        URLConnection request = new URL("https://textures.minecraft.net/texture/" + hash).openConnection();
        request.setConnectTimeout(1200);
        request.setReadTimeout(1200);
        try (var stream = request.getInputStream()) {
            byte[] bytes = stream.readNBytes(MAX_DOWNLOAD_BYTES + 1);
            if (bytes.length > MAX_DOWNLOAD_BYTES) throw new IOException("Oversized Minecraft skin");
            return ImageIO.read(new ByteArrayInputStream(bytes));
        }
    }

    /** Reconstructs the same head face + transparent outer layer used by Java. */
    static byte[] renderPortrait(BufferedImage atlasOrSkin) throws IOException {
        int width = atlasOrSkin.getWidth(), height = atlasOrSkin.getHeight();
        int x, y, hatX, hatY;
        if (width == 48 && height == 16) {
            // Geyser's six-face 3D atlas, first row is base and second is hat.
            x = 8; y = 0; hatX = 8; hatY = 8;
        } else if (width >= 64 && height >= 32 && width <= 1024 && height <= 1024) {
            // Mojang skin (64x32 or 64x64, potentially high resolution).
            int scale = width / 64;
            if (scale < 1 || width % 64 != 0 || height < 32 * scale) return null;
            x = 8 * scale; y = 8 * scale; hatX = 40 * scale; hatY = 8 * scale;
            return renderLayers(atlasOrSkin, x, y, hatX, hatY, 8 * scale);
        } else return null;
        return renderLayers(atlasOrSkin, x, y, hatX, hatY, 8);
    }

    private static byte[] renderLayers(BufferedImage source, int x, int y, int hatX, int hatY,
                                       int side) throws IOException {
        BufferedImage face = new BufferedImage(side, side, BufferedImage.TYPE_INT_ARGB);
        Graphics2D painter = face.createGraphics();
        try {
            painter.drawImage(source, 0, 0, side, side, x, y, x + side, y + side, null);
            painter.drawImage(source, 0, 0, side, side, hatX, hatY,
                    hatX + side, hatY + side, null);
        } finally { painter.dispose(); }
        BufferedImage icon = new BufferedImage(ICON_SIZE, ICON_SIZE, BufferedImage.TYPE_INT_ARGB);
        Graphics2D scaling = icon.createGraphics();
        try {
            scaling.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                    RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
            scaling.drawImage(face, 0, 0, ICON_SIZE, ICON_SIZE, null);
        } finally { scaling.dispose(); }
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        if (!ImageIO.write(icon, "PNG", output)) throw new IOException("PNG ImageIO unavailable");
        return output.toByteArray();
    }
}
