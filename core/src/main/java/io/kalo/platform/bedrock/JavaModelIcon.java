package io.kalo.platform.bedrock;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.kalo.content.item.definition.ModelDefinition;
import io.kalo.pack.ResourcePack;
import io.kalo.pack.Writable;
import net.kyori.adventure.key.Key;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Renders an inventory icon for a Java JSON item model.
 *
 * <p>A 3D Java item is authored as cubes with per-face UVs, but Bedrock's inventory item is
 * a flat icon. Copying the model's texture — the old behaviour — puts the whole texture
 * sheet in the hotbar, which is why a krathong looked like a circuit board. This projects
 * the authored cubes with the same isometric camera the Blockbench renderer uses and draws
 * each face through its UV window.</p>
 *
 * <p>Same limits as {@link BedrockModelIcon}: a preview, not a client renderer. No
 * lighting, no back-face culling, no morph targets; faces are painted far to near.</p>
 */
final class JavaModelIcon {

    private static final int SIZE = 64;
    private static final int PADDING = 3;
    private static final String[] FACES =
            {"north", "south", "east", "west", "up", "down"};

    private static final double YAW = Math.toRadians(-38);
    private static final double PITCH = Math.toRadians(24);

    private JavaModelIcon() {
    }

    /**
     * @return a PNG, or {@code null} when the model has no drawable textured faces
     */
    static byte @Nullable [] render(@NotNull JsonObject model,
                                    @NotNull ModelDefinition.Custom custom,
                                    @NotNull ResourcePack javaSource) throws IOException {
        JsonArray elements = model.getAsJsonArray("elements");
        if (elements == null || elements.isEmpty()) {
            return null;
        }
        Map<String, String> aliases = aliases(model);
        Map<Key, BufferedImage> images = new LinkedHashMap<>();
        List<ProjectedFace> faces = new ArrayList<>();

        for (JsonElement element : elements) {
            if (!element.isJsonObject()) {
                continue;
            }
            JsonObject entry = element.getAsJsonObject();
            double[] from = vector(entry.getAsJsonArray("from"));
            double[] to = vector(entry.getAsJsonArray("to"));
            if (from == null || to == null) {
                continue;
            }
            JsonObject faceMap = entry.getAsJsonObject("faces");
            if (faceMap == null) {
                continue;
            }
            double[] origin = new double[]{8, 8, 8};
            String axis = "y";
            double angle = 0;
            JsonObject rotation = entry.getAsJsonObject("rotation");
            if (rotation != null) {
                double[] parsed = vector(rotation.getAsJsonArray("origin"));
                if (parsed != null) {
                    origin = parsed;
                }
                if (rotation.has("axis")) {
                    axis = rotation.get("axis").getAsString();
                }
                angle = rotation.has("angle") ? rotation.get("angle").getAsDouble() : 0;
            }

            for (String faceName : FACES) {
                JsonObject face = faceMap.getAsJsonObject(faceName);
                if (face == null) {
                    continue;
                }
                Key texture = resolve(face.get("texture"), aliases, custom);
                if (texture == null) {
                    continue;
                }
                BufferedImage image = images.get(texture);
                if (image == null) {
                    image = load(texture, javaSource);
                    if (image == null) {
                        continue;
                    }
                    images.put(texture, image);
                }
                double[] uv = uv(face);
                if (uv == null) {
                    continue;
                }
                double[][] corners = corners(from, to, faceName);
                for (double[] corner : corners) {
                    rotate(corner, origin, axis, angle);
                }
                ProjectedFace projected = project(corners, image, uv);
                if (projected != null) {
                    faces.add(projected);
                }
            }
        }
        if (faces.isEmpty()) {
            return null;
        }

        double minX = Double.POSITIVE_INFINITY;
        double minY = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY;
        double maxY = Double.NEGATIVE_INFINITY;
        for (ProjectedFace face : faces) {
            for (double[] point : face.points()) {
                minX = Math.min(minX, point[0]);
                minY = Math.min(minY, point[1]);
                maxX = Math.max(maxX, point[0]);
                maxY = Math.max(maxY, point[1]);
            }
        }
        double span = Math.max(maxX - minX, maxY - minY);
        if (span <= 1e-9) {
            return null;
        }
        double scale = (SIZE - PADDING * 2.0) / span;
        double offsetX = (SIZE - (maxX - minX) * scale) / 2.0 - minX * scale;
        double offsetY = (SIZE - (maxY - minY) * scale) / 2.0 - minY * scale;

        BufferedImage canvas = new BufferedImage(SIZE, SIZE,
                BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = canvas.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                    RenderingHints.VALUE_ANTIALIAS_ON);
            faces.sort(Comparator.comparingDouble(ProjectedFace::depth));
            for (ProjectedFace face : faces) {
                Path2D path = new Path2D.Double();
                for (int index = 0; index < face.points().length; index++) {
                    double[] point = face.points()[index];
                    double x = point[0] * scale + offsetX;
                    double y = point[1] * scale + offsetY;
                    if (index == 0) {
                        path.moveTo(x, y);
                    } else {
                        path.lineTo(x, y);
                    }
                }
                path.closePath();
                draw(graphics, path, face, scale, offsetX, offsetY);
            }
        } finally {
            graphics.dispose();
        }

        ByteArrayOutputStream output = new ByteArrayOutputStream();
        if (!ImageIO.write(canvas, "png", output)) {
            return null;
        }
        return output.toByteArray();
    }

    // ---------------------------------------------------------------- projection

    private static @Nullable ProjectedFace project(double[][] corners,
                                                   @NotNull BufferedImage image,
                                                   double[] uv) {
        double[][] points = new double[4][];
        double depth = 0;
        for (int index = 0; index < 4; index++) {
            double x = corners[index][0] - 8;
            double y = corners[index][1] - 8;
            double z = corners[index][2] - 8;
            double rx = x * Math.cos(YAW) - z * Math.sin(YAW);
            double rz = x * Math.sin(YAW) + z * Math.cos(YAW);
            double ry = y * Math.cos(PITCH) - rz * Math.sin(PITCH);
            points[index] = new double[]{rx, -ry};
            depth += y * Math.sin(PITCH) + rz * Math.cos(PITCH);
        }

        // Java item UVs live in the 0..16 grid whatever the PNG resolution is.
        double scaleU = image.getWidth() / 16.0;
        double scaleV = image.getHeight() / 16.0;
        double u1 = uv[0] * scaleU;
        double v1 = uv[1] * scaleV;
        double u2 = uv[2] * scaleU;
        double v2 = uv[3] * scaleV;
        if (u1 > u2) {
            double swap = u1;
            u1 = u2;
            u2 = swap;
        }
        if (v1 > v2) {
            double swap = v1;
            v1 = v2;
            v2 = swap;
        }
        return new ProjectedFace(points, depth / 4.0, image, u1, v1, u2, v2);
    }

    private static void draw(Graphics2D graphics, Path2D path, ProjectedFace face,
                             double scale, double offsetX, double offsetY) {
        double[][] points = face.points();
        double sourceWidth = face.u2() - face.u1();
        double sourceHeight = face.v2() - face.v1();
        if (Math.abs(sourceWidth) < 1e-9 || Math.abs(sourceHeight) < 1e-9) {
            return;
        }

        // Corners 0, 1 and 3 are the source rectangle's bottom-left, bottom-right and
        // top-left. The affine transform therefore needs three of them, not all four.
        double x0 = points[0][0] * scale + offsetX;
        double y0 = points[0][1] * scale + offsetY;
        double x1 = points[1][0] * scale + offsetX;
        double y1 = points[1][1] * scale + offsetY;
        double x2 = points[3][0] * scale + offsetX;
        double y2 = points[3][1] * scale + offsetY;

        double a = (x1 - x0) / sourceWidth;
        // v2 is the bottom edge in texture space, so the vertical basis points from
        // v2 towards v1: dividing by (v1 - v2) keeps the image the right way up.
        double b = (x2 - x0) / (face.v1() - face.v2());
        double c = x0 - a * face.u1() - b * face.v2();
        double d = (y1 - y0) / sourceWidth;
        double e = (y2 - y0) / (face.v1() - face.v2());
        double f = y0 - d * face.u1() - e * face.v2();

        graphics.setClip(path);
        if (Double.isFinite(a) && Double.isFinite(b) && Double.isFinite(c)
                && Double.isFinite(d) && Double.isFinite(e) && Double.isFinite(f)) {
            graphics.drawImage(face.image(), new AffineTransform(a, d, b, e, c, f), null);
        }
        graphics.setClip(null);
    }

    // ---------------------------------------------------------------- model parsing

    private static Map<String, String> aliases(JsonObject model) {
        Map<String, String> aliases = new LinkedHashMap<>();
        JsonObject textures = model.getAsJsonObject("textures");
        if (textures == null) {
            return aliases;
        }
        for (Map.Entry<String, JsonElement> entry : textures.entrySet()) {
            if (entry.getValue().isJsonPrimitive()) {
                aliases.put(entry.getKey(), entry.getValue().getAsString());
            }
        }
        return aliases;
    }

    private static @Nullable Key resolve(@Nullable JsonElement element,
                                         @NotNull Map<String, String> aliases,
                                         @NotNull ModelDefinition.Custom custom) {
        if (element == null || !element.isJsonPrimitive()) {
            return null;
        }
        String value = element.getAsString();
        for (int depth = 0; depth < 4 && value.startsWith("#"); depth++) {
            value = aliases.get(value.substring(1));
            if (value == null) {
                return null;
            }
        }
        if (value.startsWith("#")) {
            return null;
        }
        try {
            return value.contains(":")
                    ? Key.key(value)
                    : Key.key(custom.model().namespace(), value);
        } catch (RuntimeException malformed) {
            return null;
        }
    }

    private static @Nullable BufferedImage load(Key texture, ResourcePack javaSource)
            throws IOException {
        Writable file = javaSource.file("assets/" + texture.namespace()
                + "/textures/" + texture.value() + ".png");
        if (file == null) {
            return null;
        }
        try (ByteArrayInputStream input = new ByteArrayInputStream(file.toByteArray())) {
            return ImageIO.read(input);
        }
    }

    private static @Nullable double[] vector(@Nullable JsonArray array) {
        if (array == null || array.size() < 3) {
            return null;
        }
        return new double[]{array.get(0).getAsDouble(), array.get(1).getAsDouble(),
                array.get(2).getAsDouble()};
    }

    private static @Nullable double[] uv(@NotNull JsonObject face) {
        JsonArray array = face.getAsJsonArray("uv");
        if (array == null || array.size() < 4) {
            return null;
        }
        return new double[]{array.get(0).getAsDouble(), array.get(1).getAsDouble(),
                array.get(2).getAsDouble(), array.get(3).getAsDouble()};
    }

    private static double[][] corners(double[] from, double[] to, String face) {
        return switch (face) {
            case "north" -> new double[][]{
                    {from[0], from[1], from[2]}, {to[0], from[1], from[2]},
                    {to[0], to[1], from[2]}, {from[0], to[1], from[2]}};
            case "south" -> new double[][]{
                    {to[0], from[1], to[2]}, {from[0], from[1], to[2]},
                    {from[0], to[1], to[2]}, {to[0], to[1], to[2]}};
            case "east" -> new double[][]{
                    {to[0], from[1], to[2]}, {to[0], from[1], from[2]},
                    {to[0], to[1], from[2]}, {to[0], to[1], to[2]}};
            case "west" -> new double[][]{
                    {from[0], from[1], from[2]}, {from[0], from[1], to[2]},
                    {from[0], to[1], to[2]}, {from[0], to[1], from[2]}};
            case "up" -> new double[][]{
                    {from[0], to[1], from[2]}, {to[0], to[1], from[2]},
                    {to[0], to[1], to[2]}, {from[0], to[1], to[2]}};
            case "down" -> new double[][]{
                    {from[0], from[1], to[2]}, {to[0], from[1], to[2]},
                    {to[0], from[1], from[2]}, {from[0], from[1], from[2]}};
            default -> throw new IllegalArgumentException("unknown face " + face);
        };
    }

    private static void rotate(double[] point, double[] origin, String axis, double degrees) {
        if (degrees == 0) {
            return;
        }
        double x = point[0] - origin[0];
        double y = point[1] - origin[1];
        double z = point[2] - origin[2];
        double radians = Math.toRadians(degrees);
        double cos = Math.cos(radians);
        double sin = Math.sin(radians);
        double rx = x;
        double ry = y;
        double rz = z;
        switch (axis) {
            case "x" -> {
                ry = y * cos - z * sin;
                rz = y * sin + z * cos;
            }
            case "z" -> {
                rx = x * cos - y * sin;
                ry = x * sin + y * cos;
            }
            default -> {
                rx = x * cos + z * sin;
                rz = -x * sin + z * cos;
            }
        }
        point[0] = rx + origin[0];
        point[1] = ry + origin[1];
        point[2] = rz + origin[2];
    }

    private record ProjectedFace(double[][] points, double depth,
                                 BufferedImage image, double u1, double v1,
                                 double u2, double v2) {
    }
}
