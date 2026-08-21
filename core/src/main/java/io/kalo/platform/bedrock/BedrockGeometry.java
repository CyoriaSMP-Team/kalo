package io.kalo.platform.bedrock;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Locale;

/**
 * Converts a Java block/item model into a Bedrock geometry.
 *
 * <p>The two formats describe the same shape in different coordinate systems, and the
 * differences are easy to get subtly wrong:</p>
 *
 * <ul>
 *   <li>Java positions a cuboid by two corners in {@code 0..16}; Bedrock positions it by
 *       an {@code origin} plus a {@code size}.</li>
 *   <li><b>Bedrock's X axis is mirrored.</b> A cuboid's origin is therefore
 *       {@code 8 - to.x}, not {@code from.x - 8}.
 *       Getting this backwards produces a model that looks right until it is asymmetric.</li>
 *   <li>Java's Y is measured from the block floor and Bedrock's origin Y matches it, but
 *       X and Z are centred on the block, so both shift by 8.</li>
 *   <li>Java UVs are two corners {@code [x1,y1,x2,y2]}; Bedrock takes a corner plus a size.</li>
 * </ul>
 *
 * <p>Handles axis-aligned cuboids with optional single-axis element rotation, which is
 * what Blockbench produces for the overwhelming majority of custom content. Anything else
 * is reported rather than approximated — a silently wrong model is worse than a missing
 * one, because nobody goes looking for the cause.</p>
 */
public final class BedrockGeometry {

    /** Java models are authored against a 16x16 texture unless they say otherwise. */
    private static final int DEFAULT_TEXTURE_SIZE = 16;

    private static final List<String> FACES = List.of("north", "south", "east", "west", "up", "down");

    private BedrockGeometry() {
    }

    /**
     * @param identifier Bedrock geometry identifier, e.g. {@code geometry.kalo.ruby_chair}
     * @param javaModel  the parsed Java model
     * @return the geometry, or {@code null} if the model has no convertible elements
     */
    public static @Nullable JsonObject convert(@NotNull String identifier, @NotNull JsonObject javaModel) {
        JsonArray elements = javaModel.getAsJsonArray("elements");
        if (elements == null || elements.isEmpty()) {
            // A model that only sets a parent and textures has no shape of its own to
            // convert; it inherits one, which the resource pack cannot resolve here.
            return null;
        }

        int[] textureSize = textureSize(javaModel);

        JsonArray cubes = new JsonArray();
        for (JsonElement element : elements) {
            if (!element.isJsonObject()) {
                continue;
            }
            JsonObject cube = convertElement(element.getAsJsonObject(), textureSize);
            if (cube != null) {
                cubes.add(cube);
            }
        }

        if (cubes.isEmpty()) {
            return null;
        }

        JsonObject bone = new JsonObject();
        bone.addProperty("name", "kalo");
        bone.add("pivot", array(0, 0, 0));
        bone.add("cubes", cubes);

        JsonArray bones = new JsonArray();
        bones.add(bone);

        JsonObject description = new JsonObject();
        description.addProperty("identifier", identifier);
        description.addProperty("texture_width", textureSize[0]);
        description.addProperty("texture_height", textureSize[1]);
        description.addProperty("visible_bounds_width", 2);
        description.addProperty("visible_bounds_height", 2);
        description.add("visible_bounds_offset", array(0, 0.5, 0));

        JsonObject geometry = new JsonObject();
        geometry.add("description", description);
        geometry.add("bones", bones);

        JsonArray geometries = new JsonArray();
        geometries.add(geometry);

        JsonObject root = new JsonObject();
        root.addProperty("format_version", "1.12.0");
        root.add("minecraft:geometry", geometries);
        return root;
    }

    private static @Nullable JsonObject convertElement(@NotNull JsonObject element, int[] textureSize) {
        JsonArray from = element.getAsJsonArray("from");
        JsonArray to = element.getAsJsonArray("to");
        if (from == null || to == null || from.size() != 3 || to.size() != 3) {
            return null;
        }

        double fromX = from.get(0).getAsDouble();
        double fromY = from.get(1).getAsDouble();
        double fromZ = from.get(2).getAsDouble();
        double toX = to.get(0).getAsDouble();
        double toY = to.get(1).getAsDouble();
        double toZ = to.get(2).getAsDouble();

        JsonObject cube = new JsonObject();
        // X is mirrored between the two engines, so the origin comes from `to`, not `from`.
        cube.add("origin", array(8 - toX, fromY, fromZ - 8));
        cube.add("size", array(toX - fromX, toY - fromY, toZ - fromZ));

        JsonObject rotation = element.getAsJsonObject("rotation");
        if (rotation != null) {
            applyRotation(cube, rotation);
        }

        JsonObject faces = element.getAsJsonObject("faces");
        if (faces != null) {
            JsonObject uv = convertFaces(faces, fromX, fromY, fromZ, toX, toY, toZ);
            if (uv.size() > 0) {
                cube.add("uv", uv);
            }
        }

        return cube;
    }

    /**
     * Java rotates an element around a pivot on one axis; Bedrock takes the same idea but
     * expects the angle on all three axes. Mirroring the model's X coordinates reverses
     * an X-axis rotation; Y and Z keep their authored sign. This matches the converter
     * Geyser itself ships in Rainbow.
     */
    private static void applyRotation(@NotNull JsonObject cube, @NotNull JsonObject rotation) {
        JsonArray origin = rotation.getAsJsonArray("origin");
        JsonElement angleElement = rotation.get("angle");
        JsonElement axisElement = rotation.get("axis");
        if (origin == null || origin.size() != 3 || angleElement == null || axisElement == null) {
            return;
        }

        double angle = angleElement.getAsDouble();
        String axis = axisElement.getAsString().toLowerCase(Locale.ROOT);

        cube.add("pivot", array(
                8 - origin.get(0).getAsDouble(),
                origin.get(1).getAsDouble(),
                origin.get(2).getAsDouble() - 8));

        cube.add("rotation", switch (axis) {
            // Mirroring the model coordinates reverses rotation around X only.
            case "x" -> array(-angle, 0, 0);
            case "y" -> array(0, angle, 0);
            case "z" -> array(0, 0, angle);
            default -> array(0, 0, 0);
        });
    }

    private static @NotNull JsonObject convertFaces(@NotNull JsonObject faces,
                                                     double fromX, double fromY, double fromZ,
                                                     double toX, double toY, double toZ) {
        JsonObject converted = new JsonObject();

        for (String face : FACES) {
            JsonObject javaFace = faces.getAsJsonObject(face);
            if (javaFace == null) {
                continue;
            }
            JsonArray uv = javaFace.getAsJsonArray("uv");
            if (uv == null) {
                uv = defaultUv(face, fromX, fromY, fromZ, toX, toY, toZ);
            } else if (uv.size() != 4) {
                // Malformed rather than omitted. Java would reject this model too, so do
                // not invent coordinates for it.
                continue;
            }

            double x1 = uv.get(0).getAsDouble();
            double y1 = uv.get(1).getAsDouble();
            double x2 = uv.get(2).getAsDouble();
            double y2 = uv.get(3).getAsDouble();

            JsonObject bedrockFace = new JsonObject();
            if ("up".equals(face) || "down".equals(face)) {
                // Java and Bedrock orient horizontal faces oppositely. Negative sizes
                // are intentional and supported by Bedrock; taking abs() silently
                // unflips asymmetric textures.
                bedrockFace.add("uv", array(x2, y2));
                bedrockFace.add("uv_size", array(x1 - x2, y1 - y2));
            } else {
                bedrockFace.add("uv", array(x1, y1));
                bedrockFace.add("uv_size", array(x2 - x1, y2 - y1));
            }

            JsonElement uvRotation = javaFace.get("rotation");
            if (uvRotation != null) {
                bedrockFace.addProperty("uv_rotation", uvRotation.getAsInt());
            }

            JsonElement texture = javaFace.get("texture");
            if (texture != null && texture.isJsonPrimitive()) {
                bedrockFace.addProperty("material_instance", materialName(texture.getAsString()));
            }

            // The cube coordinates themselves are mirrored. Face names stay attached to
            // their world directions; swapping east/west here mirrors the model twice.
            converted.add(face, bedrockFace);
        }

        return converted;
    }

    /** Kept package-visible for the coordinate-regression tests. */
    static @NotNull String mirrorFace(@NotNull String javaFace) {
        return javaFace;
    }

    /** Java's exact inferred face UVs when a model omits the optional {@code uv} field. */
    private static @NotNull JsonArray defaultUv(@NotNull String face,
                                                 double fromX, double fromY, double fromZ,
                                                 double toX, double toY, double toZ) {
        return switch (face) {
            case "down" -> array(fromX, 16 - toZ, toX, 16 - fromZ);
            case "up" -> array(fromX, fromZ, toX, toZ);
            case "north" -> array(16 - toX, 16 - toY, 16 - fromX, 16 - fromY);
            case "south" -> array(fromX, 16 - toY, toX, 16 - fromY);
            case "west" -> array(fromZ, 16 - toY, toZ, 16 - fromY);
            case "east" -> array(16 - toZ, 16 - toY, 16 - fromZ, 16 - fromY);
            default -> throw new IllegalArgumentException("unknown cube face '" + face + "'");
        };
    }

    /** Bedrock material-instance names cannot use Java's leading reference marker. */
    public static @NotNull String materialName(@NotNull String texture) {
        String value = texture.startsWith("#") ? texture.substring(1) : texture;
        return value.replace(':', '_').replace('/', '_');
    }

    /** Blockbench writes {@code texture_size: [w, h]} when the sheet is not 16x16. */
    private static int[] textureSize(@NotNull JsonObject javaModel) {
        JsonElement size = javaModel.get("texture_size");
        if (size != null && size.isJsonArray() && size.getAsJsonArray().size() == 2) {
            JsonArray array = size.getAsJsonArray();
            return new int[]{array.get(0).getAsInt(), array.get(1).getAsInt()};
        }
        return new int[]{DEFAULT_TEXTURE_SIZE, DEFAULT_TEXTURE_SIZE};
    }

    /** The Bedrock geometry identifier for a piece of content. */
    public static @NotNull String identifierFor(@NotNull String namespace, @NotNull String name) {
        return "geometry.kalo." + namespace + "_" + name;
    }

    private static @NotNull JsonArray array(double... values) {
        JsonArray array = new JsonArray(values.length);
        for (double value : values) {
            // Whole numbers serialize as 8 rather than 8.0, which is what Bedrock's own
            // geometry files look like.
            if (value == Math.rint(value) && !Double.isInfinite(value)) {
                array.add((int) value);
            } else {
                array.add(value);
            }
        }
        return array;
    }
}
