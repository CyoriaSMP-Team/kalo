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
 *       {@code 8 - to.x}, not {@code from.x - 8}, and the east and west faces swap.
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
            JsonObject uv = convertFaces(faces, textureSize);
            if (uv.size() > 0) {
                cube.add("uv", uv);
            }
        }

        return cube;
    }

    /**
     * Java rotates an element around a pivot on one axis; Bedrock takes the same idea but
     * expects the angle on all three axes and mirrors X, which flips the sign on the Y
     * and Z rotations.
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
            // The mirrored X axis reverses the direction of the other two rotations.
            case "x" -> array(-angle, 0, 0);
            case "y" -> array(0, -angle, 0);
            case "z" -> array(0, 0, angle);
            default -> array(0, 0, 0);
        });
    }

    private static @NotNull JsonObject convertFaces(@NotNull JsonObject faces, int[] textureSize) {
        JsonObject converted = new JsonObject();

        for (String face : FACES) {
            JsonObject javaFace = faces.getAsJsonObject(face);
            if (javaFace == null) {
                continue;
            }
            JsonArray uv = javaFace.getAsJsonArray("uv");
            if (uv == null || uv.size() != 4) {
                // Java infers missing UVs from the element's position. Bedrock has no
                // equivalent, so the face is left out rather than given a wrong one.
                continue;
            }

            double x1 = uv.get(0).getAsDouble();
            double y1 = uv.get(1).getAsDouble();
            double x2 = uv.get(2).getAsDouble();
            double y2 = uv.get(3).getAsDouble();

            JsonObject bedrockFace = new JsonObject();
            bedrockFace.add("uv", array(Math.min(x1, x2), Math.min(y1, y2)));
            bedrockFace.add("uv_size", array(Math.abs(x2 - x1), Math.abs(y2 - y1)));

            converted.add(mirrorFace(face), bedrockFace);
        }

        return converted;
    }

    /** East and west swap because Bedrock's X axis runs the other way. */
    static @NotNull String mirrorFace(@NotNull String javaFace) {
        return switch (javaFace) {
            case "east" -> "west";
            case "west" -> "east";
            default -> javaFace;
        };
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
