package io.kalo.platform.bedrock;

import io.kalo.content.glyph.definition.GlyphDefinition;
import io.kalo.pack.ResourcePack;
import net.kyori.adventure.key.Key;
import io.kalo.pack.Writable;
import org.jetbrains.annotations.NotNull;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Converts Kalo's Java bitmap glyphs into Bedrock glyph pages.
 *
 * <p>Bedrock does not read Java font JSON. It looks for one 16×16 page per codepoint
 * high byte at {@code font/glyph_XX.png}, so a Java glyph is useful to a Bedrock player
 * only when that page exists. The cell size is the smallest power of two that fits the
 * largest glyph on the page, within Bedrock's readable range.</p>
 */
public final class BedrockGlyphPages {

    private static final int GRID = 16;
    private static final int MIN_CELL = 16;
    private static final int MAX_CELL = 128;
    private static final int MAX_SOURCE = 4096;

    private BedrockGlyphPages() {
    }

    /**
     * @return how many page images were written
     */
    public static int write(@NotNull ResourcePack bedrock, @NotNull ResourcePack javaSource,
                            @NotNull Iterable<GlyphDefinition> glyphs) throws IOException {
        return write(bedrock, javaSource, glyphs, Map.of());
    }

    /**
     * @param offsets HUD content key to horizontal pixel offset; ordinary glyphs are 0
     */
    public static int write(@NotNull ResourcePack bedrock, @NotNull ResourcePack javaSource,
                            @NotNull Iterable<GlyphDefinition> glyphs,
                            @NotNull Map<Key, Integer> offsets) throws IOException {
        Map<Integer, List<Glyph>> pages = new LinkedHashMap<>();
        for (GlyphDefinition glyph : glyphs) {
            int codepoint = glyph.character();
            if (codepoint < 0xE000 || codepoint > 0xF8FF) {
                // Java allows it with a warning; Bedrock has no page for ordinary text.
                continue;
            }
            String path = "assets/" + glyph.texture().namespace() + "/textures/"
                    + glyph.texture().value() + ".png";
            Writable source = javaSource.file(path);
            if (source == null) {
                continue;
            }
            byte[] bytes = source.toByteArray();
            if (bytes.length > 4 * 1024 * 1024) {
                continue;
            }
            BufferedImage image;
            try (ByteArrayInputStream input = new ByteArrayInputStream(bytes)) {
                image = ImageIO.read(input);
            }
            if (image == null || image.getWidth() < 1 || image.getHeight() < 1
                    || image.getWidth() > MAX_SOURCE || image.getHeight() > MAX_SOURCE) {
                continue;
            }
            pages.computeIfAbsent(codepoint >> 8, ignored -> new ArrayList<>())
                    .add(new Glyph(codepoint, image,
                            offsets.getOrDefault(glyph.key(), 0), glyph.height()));
        }

        int written = 0;
        for (Map.Entry<Integer, List<Glyph>> entry : pages.entrySet()) {
            BufferedImage page = page(entry.getValue());
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            if (!ImageIO.write(page, "png", output)) {
                continue;
            }
            bedrock.file(String.format(java.util.Locale.ROOT,
                    "font/glyph_%02X.png", entry.getKey()), Writable.bytes(output.toByteArray()));
            written++;
        }
        return written;
    }

    private static BufferedImage page(List<Glyph> glyphs) {
        int largest = 1;
        for (Glyph glyph : glyphs) {
            largest = Math.max(largest, Math.max(glyph.image().getWidth(),
                    glyph.image().getHeight()));
        }
        int cell = MIN_CELL;
        while (cell < largest && cell < MAX_CELL) {
            cell *= 2;
        }
        int size = cell * GRID;
        BufferedImage page = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        for (Glyph glyph : glyphs) {
            int index = glyph.codepoint() & 0xFF;
            int cellX = (index % GRID) * cell;
            int cellY = (index / GRID) * cell;
            BufferedImage image = glyph.image();
            double fit = Math.min((double) cell / image.getWidth(),
                    (double) cell / image.getHeight());
            int width = Math.max(1, (int) Math.round(image.getWidth() * fit));
            int height = Math.max(1, (int) Math.round(image.getHeight() * fit));
            int shift = glyph.fontHeight() <= 0 ? 0 : (int) Math.round(
                    glyph.offset() * height / (double) glyph.fontHeight());
            int x = cellX + shift;
            int y = cellY + (cell - height) / 2;
            for (int dy = 0; dy < height; dy++) {
                int targetY = y + dy;
                if (targetY < cellY || targetY >= cellY + cell) {
                    continue;
                }
                int sourceY = Math.min(image.getHeight() - 1,
                        dy * image.getHeight() / height);
                for (int dx = 0; dx < width; dx++) {
                    int targetX = x + dx;
                    if (targetX < cellX || targetX >= cellX + cell) {
                        continue;
                    }
                    int sourceX = Math.min(image.getWidth() - 1,
                            dx * image.getWidth() / width);
                    page.setRGB(targetX, targetY, image.getRGB(sourceX, sourceY));
                }
            }
        }
        return page;
    }

    private record Glyph(int codepoint, BufferedImage image, int offset, int fontHeight) {
    }
}
