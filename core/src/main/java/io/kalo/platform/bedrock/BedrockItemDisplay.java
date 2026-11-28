package io.kalo.platform.bedrock;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.kalo.content.item.definition.BedrockOptions;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Turns a Java model's {@code display} block into the Bedrock animations that pose a held
 * item. Java poses an item with data on the model; Bedrock has no such field, so the pose
 * has to be an animation on the bone bound to the hand.
 *
 * <p>The pose is derived, not copied from any sample:</p>
 * <ul>
 *   <li>The bone's pivot is the model's centre, so rotation and scale act about the centre
 *       exactly as Java's do.</li>
 *   <li>The position puts that centre on the hand anchor: {@code HAND_ANCHOR - centre}.
 *       Bedrock Wiki documents that a bound bone is drawn 24 units low, so the anchor is
 *       that offset undone, {@code y = 24}.</li>
 *   <li>Java's {@code translation} is added in Bedrock's mirrored X/Z, its {@code rotation}
 *       as {@code [rx, -ry, -rz]}, its {@code scale} averaged to one number. A context the
 *       model does not author is the neutral pose: no rotation, scale 1.</li>
 *   <li>{@code bedrock.hold} in the item's YAML is added on top, per view.</li>
 * </ul>
 *
 * <p><b>Not measured on a Bedrock client.</b> The 24-unit lift is from documentation, the sign
 * flips are the usual X-mirror convention, and the two views share them. Where a model sits
 * wrong, use {@code bedrock.hold}; every constant is here so a measured correction is one
 * edit, and {@code BedrockItemDisplayTest} pins them.</p>
 */
final class BedrockItemDisplay {

    static final String FIRST_PERSON = "firstperson_righthand";
    static final String THIRD_PERSON = "thirdperson_righthand";

    /** Where the model centre should sit in the bound bone's space: the 24-unit offset undone. */
    static final double[] HAND_ANCHOR = {0, 24, 0};

    private BedrockItemDisplay() {
    }

    static @NotNull String animationId(@NotNull String shorthand, boolean firstPerson) {
        return "animation." + shorthand + (firstPerson ? ".hold_first_person" : ".hold_third_person");
    }

    static @NotNull String animationPath(@NotNull String shorthand) {
        return "animations/" + shorthand + ".animation.json";
    }

    /**
     * @param javaModel the resolved model; its {@code display} may be absent
     * @param boneName  the bone carrying the hand binding, which is what gets animated
     * @param center    the model's centre in Bedrock model space, which is also the bone's pivot
     * @param override  per-item correction from YAML, or {@code null}
     */
    static @NotNull JsonObject animations(@NotNull String shorthand, @NotNull JsonObject javaModel,
                                          @NotNull String boneName, double[] center,
                                          @Nullable BedrockOptions.HoldPose override) {
        JsonObject display = javaModel.has("display") && javaModel.get("display").isJsonObject()
                ? javaModel.getAsJsonObject("display") : new JsonObject();
        JsonObject animations = new JsonObject();
        animations.add(animationId(shorthand, true),
                pose(boneName, display.get(FIRST_PERSON), center, override == null ? null : override.firstPerson()));
        animations.add(animationId(shorthand, false),
                pose(boneName, display.get(THIRD_PERSON), center, override == null ? null : override.thirdPerson()));
        JsonObject root = new JsonObject();
        root.addProperty("format_version", "1.10.0");
        root.add("animations", animations);
        return root;
    }

    private static @NotNull JsonObject pose(@NotNull String bone, @Nullable JsonElement authored,
                                            double[] center, BedrockOptions.@Nullable View override) {
        double[] rotation = {0, 0, 0};
        double[] translation = {0, 0, 0};
        double scale = 1.0;
        if (authored != null && authored.isJsonObject()) {
            JsonObject transform = authored.getAsJsonObject();
            double[] javaRotation = vector(transform.get("rotation"), 0);
            double[] javaTranslation = vector(transform.get("translation"), 0);
            double[] javaScale = vector(transform.get("scale"), 1);
            // Mirroring X reverses rotation about Y and Z.
            rotation = new double[]{javaRotation[0], -javaRotation[1], -javaRotation[2]};
            // Java translation is in 1/16 block, the same unit as Bedrock bone position.
            translation = new double[]{-javaTranslation[0], javaTranslation[1], -javaTranslation[2]};
            // Bedrock takes one uniform scale; average the three Java axes.
            scale = (javaScale[0] + javaScale[1] + javaScale[2]) / 3.0;
        }
        double[] position = new double[3];
        for (int axis = 0; axis < 3; axis++) {
            position[axis] = HAND_ANCHOR[axis] - center[axis] + translation[axis];
        }
        if (override != null) {
            add(position, override.position());
            add(rotation, override.rotation());
            if (override.scale() != null) scale *= override.scale();
        }
        JsonObject boneAnimation = new JsonObject();
        boneAnimation.add("rotation", array(rotation));
        boneAnimation.add("position", array(position));
        if (scale != 1.0) {
            boneAnimation.addProperty("scale", scale);
        }
        JsonObject bones = new JsonObject();
        bones.add(bone, boneAnimation);
        JsonObject animation = new JsonObject();
        animation.addProperty("loop", true);
        animation.add("bones", bones);
        return animation;
    }

    /** Centre of the cubes' bounding box, ignoring per-cube rotation. */
    static double[] centerOf(@NotNull JsonArray cubes) {
        double[] min = {Double.MAX_VALUE, Double.MAX_VALUE, Double.MAX_VALUE};
        double[] max = {-Double.MAX_VALUE, -Double.MAX_VALUE, -Double.MAX_VALUE};
        for (JsonElement element : cubes) {
            JsonObject cube = element.getAsJsonObject();
            JsonArray origin = cube.getAsJsonArray("origin");
            JsonArray size = cube.getAsJsonArray("size");
            for (int axis = 0; axis < 3; axis++) {
                double low = origin.get(axis).getAsDouble();
                min[axis] = Math.min(min[axis], low);
                max[axis] = Math.max(max[axis], low + size.get(axis).getAsDouble());
            }
        }
        return new double[]{(min[0] + max[0]) / 2, (min[1] + max[1]) / 2, (min[2] + max[2]) / 2};
    }

    private static void add(double[] target, @Nullable List<Double> delta) {
        if (delta == null) return;
        for (int i = 0; i < 3 && i < delta.size(); i++) target[i] += delta.get(i);
    }

    private static double[] vector(@Nullable JsonElement element, double fallback) {
        double[] out = {fallback, fallback, fallback};
        if (element == null || !element.isJsonArray()) return out;
        JsonArray array = element.getAsJsonArray();
        for (int i = 0; i < 3 && i < array.size(); i++) {
            if (array.get(i).isJsonPrimitive() && array.get(i).getAsJsonPrimitive().isNumber()) {
                out[i] = array.get(i).getAsDouble();
            }
        }
        return out;
    }

    private static JsonArray array(double[] values) {
        JsonArray array = new JsonArray();
        for (double value : values) array.add(value);
        return array;
    }
}
