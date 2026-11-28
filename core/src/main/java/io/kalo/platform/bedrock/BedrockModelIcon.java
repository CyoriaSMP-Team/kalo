package io.kalo.platform.bedrock;

import io.kalo.model.ModelIR;
import io.kalo.model.ModelIR.Bone;
import io.kalo.model.ModelIR.Cube;
import io.kalo.model.ModelIR.Face;
import io.kalo.model.ModelIR.Vec3;
import io.kalo.model.ModelTextureResolver;
import io.kalo.pack.ResourcePack;
import io.kalo.pack.Writable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.imageio.ImageIO;
import java.awt.Color;
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
 * Renders a small isometric inventory preview from the shared model IR.
 *
 * <p>A Blockbench item is authored as a 3D object, but Bedrock's inventory item is a flat
 * icon. Reusing the first texture makes every multi-cube item look like a paint swatch;
 * this renderer projects the authored cubes and draws their textured faces instead. It is
 * deliberately a preview renderer, not a client renderer: no lighting, culling guarantees
 * or morph targets.</p>
 */
final class BedrockModelIcon {

    private static final int SIZE = 64;
    private static final int PADDING = 3;
    private static final List<String> FACES =
            List.of("north", "south", "east", "west", "up", "down");

    private static final double YAW = Math.toRadians(-38);
    private static final double PITCH = Math.toRadians(24);

    private BedrockModelIcon() {
    }

    /**
     * @return a PNG, or {@code null} when the model has no textured visible faces or no
     *         texture image can be read
     */
    static byte @Nullable [] render(@NotNull ModelIR model,
                                    @NotNull List<ModelTextureResolver.ResolvedTexture> textures,
                                    @NotNull ResourcePack javaSource) throws IOException {
        Map<String, ModelTextureResolver.ResolvedTexture> byId = new LinkedHashMap<>();
        Map<String, BufferedImage> images = new LinkedHashMap<>();
        for (ModelTextureResolver.ResolvedTexture texture : textures) {
            byId.put(texture.textureId(), texture);
            BufferedImage image = image(texture, javaSource);
            if (image != null) {
                images.put(texture.textureId(), image);
            }
        }
        if (images.isEmpty() || textures.isEmpty()) {
            return null;
        }
        ModelTextureResolver.ResolvedTexture fallback = textures.getFirst();

        Map<String, Bone> bones = new LinkedHashMap<>();
        for (Bone bone : model.bones()) {
            bones.put(bone.id(), bone);
        }

        List<ProjectedFace> faces = new ArrayList<>();
        for (Bone bone : model.bones()) {
            if (!bone.visible() || !bone.export()) {
                continue;
            }
            for (Cube cube : bone.cubes()) {
                if (!cube.visible() || !cube.export()) {
                    continue;
                }
                for (String faceName : FACES) {
                    Face face = cube.faces().get(faceName);
                    if (face == null) {
                        continue;
                    }
                    ModelTextureResolver.ResolvedTexture resolved =
                            byId.get(face.textureId());
                    if (resolved == null) {
                        resolved = fallback;
                    }
                    BufferedImage image = resolved == null ? null
                            : images.get(resolved.textureId());
                    if (image == null) {
                        continue;
                    }
                    Vec3[] corners = cubeCorners(cube, faceName);
                    for (int i = 0; i < corners.length; i++) {
                        corners[i] = rotateBoneChain(corners[i], bone, bones);
                        corners[i] = rotateAround(corners[i], cube.pivot(),
                                cube.rotation());
                    }
                    ProjectedFace projected = project(corners, face, image, model);
                    if (projected != null) {
                        faces.add(projected);
                    }
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
            for (ScreenPoint point : face.points()) {
                minX = Math.min(minX, point.x());
                minY = Math.min(minY, point.y());
                maxX = Math.max(maxX, point.x());
                maxY = Math.max(maxY, point.y());
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
            // Painter's algorithm: farther faces first.
            faces.sort(Comparator.comparingDouble(ProjectedFace::depth));
            for (ProjectedFace face : faces) {
                Path2D path = new Path2D.Double();
                for (int i = 0; i < face.points().length; i++) {
                    ScreenPoint point = face.points()[i];
                    double x = point.x() * scale + offsetX;
                    double y = point.y() * scale + offsetY;
                    if (i == 0) {
                        path.moveTo(x, y);
                    } else {
                        path.lineTo(x, y);
                    }
                }
                path.closePath();
                drawTextured(graphics, path, face, scale, offsetX, offsetY);
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

    private static void drawTextured(Graphics2D graphics, Path2D path,
                                     ProjectedFace face, double scale,
                                     double offsetX, double offsetY) {
        ScreenPoint[] points = face.points();
        double sourceWidth = face.u2() - face.u1();
        double sourceHeight = face.v2() - face.v1();
        if (Math.abs(sourceWidth) < 1e-9 || Math.abs(sourceHeight) < 1e-9) {
            return;
        }

        // The first three projected corners correspond to the source rectangle's
        // bottom-left, bottom-right and top-left corners.
        ScreenPoint p0 = points[0];
        ScreenPoint p1 = points[1];
        ScreenPoint p2 = points[3];
        double x0 = p0.x() * scale + offsetX;
        double y0 = p0.y() * scale + offsetY;
        double x1 = p1.x() * scale + offsetX;
        double y1 = p1.y() * scale + offsetY;
        double x2 = p2.x() * scale + offsetX;
        double y2 = p2.y() * scale + offsetY;

        double a = (x1 - x0) / sourceWidth;
        double b = (x2 - x0) / sourceHeight;
        double c = x0 - a * face.u1() - b * face.v2();
        double d = (y1 - y0) / sourceWidth;
        double e = (y2 - y0) / sourceHeight;
        double f = y0 - d * face.u1() - e * face.v2();

        graphics.setClip(path);
        if (Double.isFinite(a) && Double.isFinite(b) && Double.isFinite(c)
                && Double.isFinite(d) && Double.isFinite(e) && Double.isFinite(f)) {
            graphics.drawImage(face.image(),
                    new AffineTransform(a, d, b, e, c, f), null);
        } else {
            graphics.setColor(average(face.image(), face.u1(), face.v1(),
                    face.u2(), face.v2()));
            graphics.fill(path);
        }
        graphics.setClip(null);
    }

    private static ProjectedFace project(Vec3[] corners, Face face,
                                         BufferedImage image, ModelIR model) {
        ScreenPoint[] points = new ScreenPoint[4];
        double depth = 0;
        for (int i = 0; i < 4; i++) {
            double x = corners[i].x() - 8;
            double y = corners[i].y() - 8;
            double z = corners[i].z() - 8;
            double rx = x * Math.cos(YAW) - z * Math.sin(YAW);
            double rz = x * Math.sin(YAW) + z * Math.cos(YAW);
            double ry = y * Math.cos(PITCH) - rz * Math.sin(PITCH);
            points[i] = new ScreenPoint(rx, -ry);
            depth += y * Math.sin(PITCH) + rz * Math.cos(PITCH);
        }

        double scaleU = image.getWidth() / (double) model.textureWidth();
        double scaleV = image.getHeight() / (double) model.textureHeight();
        double u1 = face.u1() * scaleU;
        double v1 = face.v1() * scaleV;
        double u2 = face.u2() * scaleU;
        double v2 = face.v2() * scaleV;
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

    private static Vec3[] cubeCorners(Cube cube, String face) {
        Vec3 from = cube.from();
        Vec3 to = cube.to();
        return switch (face) {
            case "north" -> new Vec3[]{
                    new Vec3(from.x(), from.y(), from.z()),
                    new Vec3(to.x(), from.y(), from.z()),
                    new Vec3(to.x(), to.y(), from.z()),
                    new Vec3(from.x(), to.y(), from.z())};
            case "south" -> new Vec3[]{
                    new Vec3(to.x(), from.y(), to.z()),
                    new Vec3(from.x(), from.y(), to.z()),
                    new Vec3(from.x(), to.y(), to.z()),
                    new Vec3(to.x(), to.y(), to.z())};
            case "east" -> new Vec3[]{
                    new Vec3(to.x(), from.y(), to.z()),
                    new Vec3(to.x(), from.y(), from.z()),
                    new Vec3(to.x(), to.y(), from.z()),
                    new Vec3(to.x(), to.y(), to.z())};
            case "west" -> new Vec3[]{
                    new Vec3(from.x(), from.y(), from.z()),
                    new Vec3(from.x(), from.y(), to.z()),
                    new Vec3(from.x(), to.y(), to.z()),
                    new Vec3(from.x(), to.y(), from.z())};
            case "up" -> new Vec3[]{
                    new Vec3(from.x(), to.y(), from.z()),
                    new Vec3(to.x(), to.y(), from.z()),
                    new Vec3(to.x(), to.y(), to.z()),
                    new Vec3(from.x(), to.y(), to.z())};
            case "down" -> new Vec3[]{
                    new Vec3(from.x(), from.y(), to.z()),
                    new Vec3(to.x(), from.y(), to.z()),
                    new Vec3(to.x(), from.y(), from.z()),
                    new Vec3(from.x(), from.y(), from.z())};
            default -> throw new IllegalArgumentException("unknown face " + face);
        };
    }

    private static Vec3 rotateBoneChain(Vec3 point, Bone bone, Map<String, Bone> bones) {
        Vec3 result = point;
        if (bone.parentId() != null) {
            Bone parent = bones.get(bone.parentId());
            if (parent != null) {
                result = rotateBoneChain(result, parent, bones);
            }
        }
        return rotateAround(result, bone.pivot(), bone.rotation());
    }

    private static Vec3 rotateAround(Vec3 point, Vec3 pivot, Vec3 degrees) {
        double x = point.x() - pivot.x();
        double y = point.y() - pivot.y();
        double z = point.z() - pivot.z();
        double rx = Math.toRadians(degrees.x());
        double ry = Math.toRadians(degrees.y());
        double rz = Math.toRadians(degrees.z());

        double y1 = y * Math.cos(rx) - z * Math.sin(rx);
        double z1 = y * Math.sin(rx) + z * Math.cos(rx);
        double x1 = x * Math.cos(ry) + z1 * Math.sin(ry);
        double z2 = -x * Math.sin(ry) + z1 * Math.cos(ry);
        double x2 = x1 * Math.cos(rz) - y1 * Math.sin(rz);
        double y2 = x1 * Math.sin(rz) + y1 * Math.cos(rz);
        return new Vec3(x2 + pivot.x(), y2 + pivot.y(), z2 + pivot.z());
    }

    private static @Nullable BufferedImage image(
            ModelTextureResolver.ResolvedTexture texture,
            ResourcePack javaSource) throws IOException {
        byte[] bytes = texture.embeddedPng();
        if (bytes == null) {
            Writable file = javaSource.file("assets/" + texture.key().namespace()
                    + "/textures/" + texture.key().value() + ".png");
            if (file == null) {
                return null;
            }
            bytes = file.toByteArray();
        }
        try (ByteArrayInputStream input = new ByteArrayInputStream(bytes)) {
            return ImageIO.read(input);
        }
    }

    private static Color average(BufferedImage image, double u1, double v1,
                                 double u2, double v2) {
        int x1 = clamp((int) Math.floor(u1), image.getWidth());
        int y1 = clamp((int) Math.floor(v1), image.getHeight());
        int x2 = clamp((int) Math.ceil(u2), image.getWidth());
        int y2 = clamp((int) Math.ceil(v2), image.getHeight());
        long red = 0;
        long green = 0;
        long blue = 0;
        long alpha = 0;
        long count = 0;
        for (int y = Math.min(y1, y2); y <= Math.max(y1, y2); y++) {
            for (int x = Math.min(x1, x2); x <= Math.max(x1, x2); x++) {
                int argb = image.getRGB(x, y);
                red += (argb >> 16) & 0xff;
                green += (argb >> 8) & 0xff;
                blue += argb & 0xff;
                alpha += (argb >>> 24) & 0xff;
                count++;
            }
        }
        if (count == 0) {
            return new Color(255, 0, 255, 255);
        }
        return new Color((int) (red / count), (int) (green / count),
                (int) (blue / count), (int) (alpha / count));
    }

    private static int clamp(int value, int limit) {
        return Math.max(0, Math.min(limit - 1, value));
    }

    private record ScreenPoint(double x, double y) {
    }

    private record ProjectedFace(ScreenPoint[] points, double depth,
                                 BufferedImage image, double u1, double v1,
                                 double u2, double v2) {
    }
}
