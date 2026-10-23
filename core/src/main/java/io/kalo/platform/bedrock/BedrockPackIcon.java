package io.kalo.platform.bedrock;

import io.kalo.pack.Writable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** Bedrock's pack_icon.png must be a square PNG, not Java's pack.png metadata. */
public final class BedrockPackIcon {
    private static final int SIDE = 256;
    private static final long MAX_IMAGE_BYTES = 4L * 1024 * 1024;

    private BedrockPackIcon() {}

    /** Prefer the server branding configured in Kalo, then Java pack.png, then Kalo's fallback. */
    public static @NotNull Writable create(@NotNull Path kaloFolder,
                                            @Nullable String configuredLogo,
                                            @Nullable Writable javaPackIcon) throws IOException {
        BufferedImage image = null;
        if (configuredLogo != null && !configuredLogo.isBlank()) {
            Path root = kaloFolder.toAbsolutePath().normalize();
            Path logo = root.resolve(configuredLogo).normalize();
            if (logo.startsWith(root) && Files.isRegularFile(logo) && Files.size(logo) <= MAX_IMAGE_BYTES) {
                image = ImageIO.read(logo.toFile());
            }
        }
        if (image == null && javaPackIcon != null) {
            byte[] bytes = javaPackIcon.toByteArray();
            if (bytes.length <= MAX_IMAGE_BYTES) image = ImageIO.read(new ByteArrayInputStream(bytes));
        }
        BufferedImage icon = new BufferedImage(SIDE, SIDE, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = icon.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setColor(new Color(17, 22, 30));
            g.fillRect(0, 0, SIDE, SIDE);
            if (image != null && image.getWidth() > 0 && image.getHeight() > 0) {
                double scale = Math.min((double) SIDE / image.getWidth(), (double) SIDE / image.getHeight());
                int w = Math.max(1, (int) Math.round(image.getWidth() * scale));
                int h = Math.max(1, (int) Math.round(image.getHeight() * scale));
                g.drawImage(image, (SIDE - w) / 2, (SIDE - h) / 2, w, h, null);
            } else {
                g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 150));
                g.setColor(new Color(65, 208, 197));
                java.awt.FontMetrics font = g.getFontMetrics();
                String k = "K";
                g.drawString(k, (SIDE - font.stringWidth(k)) / 2, 181);
            }
        } finally {
            g.dispose();
        }
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        if (!ImageIO.write(icon, "png", output)) throw new IOException("PNG writer unavailable");
        return Writable.bytes(output.toByteArray());
    }
}
