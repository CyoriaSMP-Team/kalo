package io.kalo.pack;

import org.jetbrains.annotations.NotNull;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;

/**
 * One shared 1x1 fully transparent PNG.
 *
 * <p>Used where a platform has no way to omit a vanilla layer: a 3D worn model needs the
 * flat equipment texture to draw nothing, and an animated Bedrock block needs its static
 * surface to stop drawing under the client entity.</p>
 */
public final class TransparentPng {

    private static final byte[] PNG = create();

    private TransparentPng() {
    }

    public static byte @NotNull [] bytes() {
        return PNG.clone();
    }

    private static byte[] create() {
        BufferedImage image = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
        image.setRGB(0, 0, 0);
        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            ImageIO.write(image, "png", output);
            return output.toByteArray();
        } catch (IOException impossible) {
            throw new UncheckedIOException("JVM cannot write a PNG", impossible);
        }
    }
}
