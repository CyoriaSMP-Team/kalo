package io.kalo.platform.java;

import com.google.gson.JsonObject;
import io.kalo.content.vfx.definition.VfxDefinition;
import io.kalo.pack.Json;
import io.kalo.pack.ResourcePack;
import io.kalo.pack.Writable;
import org.jetbrains.annotations.NotNull;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

/**
 * Paints the item-display sprites for a VFX and writes their models.
 *
 * <p>A sprite is a generated 32×32 PNG with the layer's colour and a soft glow, streak or
 * spark falloff. Its item model uses the vanilla {@code item/generated} parent, and the
 * runtime points {@code minecraft:item_model} at the same {@code namespace:vfx/...} key.
 * Camera layers get their own {@code .../camera_<n>} key so they never collide with the
 * world-space motes.</p>
 */
public final class VfxSpritePainter {

    private static final int SIZE = 32;

    private VfxSpritePainter() {
    }

    public static int compile(@NotNull ResourcePack pack,
                              @NotNull Iterable<VfxDefinition> definitions) {
        int count = 0;
        for (VfxDefinition definition : definitions) {
            for (int layer = 0; layer < definition.layerCount(); layer++) {
                pack.file(texturePath(definition, layer),
                        Writable.bytes(png(definition, layer)));
                pack.file(modelPath(definition, layer),
                        Json.writable(model(definition, layer)));
                count++;
            }
            for (int camera = 0; camera < definition.cameraLayerCount(); camera++) {
                int encoded = VfxDefinition.cameraLayerId(camera);
                pack.file(texturePath(definition, encoded),
                        Writable.bytes(png(definition, encoded)));
                pack.file(modelPath(definition, encoded),
                        Json.writable(model(definition, encoded)));
                count++;
            }
        }
        return count;
    }

    /** Exposed for tests and diagnostics; a negative index selects a camera layer. */
    public static @NotNull String texturePath(@NotNull VfxDefinition definition,
                                              int layerIndex) {
        return "assets/" + definition.key().namespace() + "/textures/"
                + definition.spriteTextureFor(layerIndex).value() + ".png";
    }

    /** Exposed for tests and diagnostics; a negative index selects a camera layer. */
    public static @NotNull String modelPath(@NotNull VfxDefinition definition,
                                            int layerIndex) {
        return "assets/" + definition.key().namespace() + "/models/"
                + definition.spriteModelFor(layerIndex).value() + ".json";
    }

    static byte @NotNull [] png(@NotNull VfxDefinition definition, int layerIndex) {
        VfxRecipeLayer layer = VfxRecipeLayer.of(definition, layerIndex);
        BufferedImage image = new BufferedImage(SIZE, SIZE, BufferedImage.TYPE_INT_ARGB);
        int rgb = layer.color() & 0xFFFFFF;
        for (int y = 0; y < SIZE; y++) {
            for (int x = 0; x < SIZE; x++) {
                double nx = (x + 0.5) / SIZE * 2 - 1;
                double ny = (y + 0.5) / SIZE * 2 - 1;
                double strength = switch (layer.kind()) {
                    case "streak" -> 1 - Math.min(1,
                            Math.sqrt(nx * nx * 4 + ny * ny * 0.35));
                    case "spark" -> 1 - Math.min(1, Math.abs(nx) * 1.8 + Math.abs(ny) * 1.8);
                    default -> 1 - Math.min(1, Math.sqrt(nx * nx + ny * ny));
                };
                int alpha = (int) Math.round(255 * Math.pow(
                        Math.max(0, Math.min(1, strength * 1.08)), 1.7));
                image.setRGB(x, y, (alpha << 24) | rgb);
            }
        }
        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            ImageIO.write(image, "png", output);
            return output.toByteArray();
        } catch (IOException impossible) {
            throw new IllegalStateException("Could not encode a VFX sprite", impossible);
        }
    }

    private static @NotNull JsonObject model(@NotNull VfxDefinition definition,
                                             int layerIndex) {
        JsonObject textures = new JsonObject();
        textures.addProperty("layer0", definition.spriteTextureFor(layerIndex).asString());
        JsonObject root = new JsonObject();
        root.addProperty("parent", "minecraft:item/generated");
        root.add("textures", textures);
        return root;
    }

    /** Small view that keeps the painter independent of the recipe's public shape. */
    private record VfxRecipeLayer(@NotNull String kind, int color) {
        static VfxRecipeLayer of(@NotNull VfxDefinition definition, int layerIndex) {
            if (layerIndex >= 0) {
                var layer = definition.recipe().layers().get(layerIndex);
                return new VfxRecipeLayer(layer.kind(), layer.color());
            }
            var camera = definition.recipe().cameras().get(-layerIndex - 1).layer();
            return new VfxRecipeLayer(camera.kind(), camera.color());
        }
    }
}
