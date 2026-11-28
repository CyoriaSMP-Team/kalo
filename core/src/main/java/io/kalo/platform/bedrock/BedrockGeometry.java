package io.kalo.platform.bedrock;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Locale;

/**
 * Turns a Java model's elements into a flat Bedrock geometry.
 *
 * <p>Both formats describe cuboids, but not in the same space:</p>
 *
 * <ul>
 *   <li>Java gives two opposite corners in {@code 0..16} model units; Bedrock wants an
 *       {@code origin} plus a {@code size}.</li>
 *   <li>Bedrock's model space is centred on the block in X and Z, and its X axis runs the
 *       opposite way from Java's. The origin is therefore
 *       {@code (8 - to.x, from.y, from.z - 8)}: taking X from the far corner is what folds
 *       the mirroring in. Y needs no shift — both formats measure up from the block's
 *       floor.</li>
 *   <li>Java UVs are two corners; Bedrock takes one corner plus a signed size.</li>
 * </ul>
 *
 * <p>Only what the formats share is converted: axis-aligned cuboids, an optional
 * single-axis element rotation, and per-face UVs. An element without usable corners is
 * skipped rather than guessed at, and {@link #convert} returns {@code null} if nothing
 * survives — absent geometry is easier to spot than geometry that is subtly wrong.</p>
 */
public final class BedrockGeometry {

    /** Sheet size Java assumes when a model does not declare {@code texture_size}. */
    private static final int DEFAULT_TEXTURE_SIZE = 16;

    private static final List<String> FACES = List.of("north", "south", "east", "west", "up", "down");

    private BedrockGeometry() {
    }

    /**
     * Builds a single-bone geometry from a parsed Java model.
     *
     * @param identifier geometry identifier written into the description
     * @param javaModel  the Java model JSON
     * @return the geometry document, or {@code null} if no element could be converted
     */
    public static @Nullable JsonObject convert(@NotNull String identifier, @NotNull JsonObject javaModel) {
        JsonArray elements = javaModel.getAsJsonArray("elements");
        if (elements == null || elements.isEmpty()) {
            // The model only points at a parent and textures; its shape lives in a file
            // this converter does not follow.
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
        // Bedrock's mirrored X means the origin sits at the far corner, not the near one.
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
     * Copies a single-axis element rotation.
     *
     * <p>Java rotates around a pivot on one axis; Bedrock stores a three-component
     * rotation around a pivot. The pivot moves with the mirrored, centred space like any
     * other point, and the angle signs follow the axes that were flipped: X and Y invert,
     * Z does not.</p>
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
            // Only the flipped axes get a sign change; Z is authored the same way.
            case "x" -> array(-angle, 0, 0);
            case "y" -> array(0, -angle, 0);
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
                // Four numbers or nothing: inventing the missing ones would produce a
                // face Java itself would have rejected.
                continue;
            }

            double x1 = uv.get(0).getAsDouble();
            double y1 = uv.get(1).getAsDouble();
            double x2 = uv.get(2).getAsDouble();
            double y2 = uv.get(3).getAsDouble();

            JsonObject bedrockFace = new JsonObject();
            if ("up".equals(face) || "down".equals(face)) {
                // Top and bottom faces run the other way in Bedrock. The resulting
                // negative uv_size is legal and preserves the image; abs() would visibly
                // unflip it.
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

            // Only the cube coordinates are mirrored. Face keys still mean the same world
            // directions; swapping east and west here would mirror the model twice.
            converted.add(face, bedrockFace);
        }

        return converted;
    }

    /** Identity mapping; package-visible so the coordinate tests can pin face naming. */
    static @NotNull String mirrorFace(@NotNull String javaFace) {
        return javaFace;
    }

    /** Java's derived UVs for a face that omits the optional {@code uv} field. */
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

    /** Turns a texture reference into a Bedrock material name: no {@code #}, no separators. */
    public static @NotNull String materialName(@NotNull String texture) {
        String value = texture.startsWith("#") ? texture.substring(1) : texture;
        return value.replace(':', '_').replace('/', '_');
    }

    /** Reads the {@code texture_size} Blockbench writes for non-16x16 sheets. */
    private static int[] textureSize(@NotNull JsonObject javaModel) {
        JsonElement size = javaModel.get("texture_size");
        if (size != null && size.isJsonArray() && size.getAsJsonArray().size() == 2) {
            JsonArray array = size.getAsJsonArray();
            return new int[]{array.get(0).getAsInt(), array.get(1).getAsInt()};
        }
        return new int[]{DEFAULT_TEXTURE_SIZE, DEFAULT_TEXTURE_SIZE};
    }

    /** The geometry identifier used for a piece of content. */
    public static @NotNull String identifierFor(@NotNull String namespace, @NotNull String name) {
        return "geometry.kalo." + namespace + "_" + name;
    }

    private static @NotNull JsonArray array(double... values) {
        JsonArray array = new JsonArray(values.length);
        for (double value : values) {
            // Integer-shaped numbers are written as integers, which is how a hand-authored
            // Bedrock geometry reads and keeps the JSON compact.
            if (value == Math.rint(value) && !Double.isInfinite(value)) {
                array.add((int) value);
            } else {
                array.add(value);
            }
        }
        return array;
    }
}
