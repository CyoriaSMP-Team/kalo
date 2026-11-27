package io.kalo.platform.bedrock;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Turns a Java model's {@code display} block into the Bedrock animations that pose a held
 * item. Java poses an item with data on the model; Bedrock has no such field, so the pose
 * has to be an animation on the bone bound to the hand.
 *
 * <p>Only {@code firstperson_righthand} and {@code thirdperson_righthand} are read: the
 * attachable binds the main-hand slot. A context the model does not author falls back to
 * the pose the Bedrock Wiki ships for a bound item, so an item with no {@code display}
 * still sits in the hand rather than at the wrist.</p>
 *
 * <p><b>Unverified against a Bedrock client.</b> The sign flips and the choice to add Java's
 * translation to the Wiki baseline are the usual convention (Java is +X east, Bedrock mirrors
 * X), not something measured. Every constant lives here so a correction is one edit, and
 * {@code BedrockItemDisplayTest} pins them.</p>
 */
final class BedrockItemDisplay {

    static final String FIRST_PERSON = "firstperson_righthand";
    static final String THIRD_PERSON = "thirdperson_righthand";

    // Bedrock Wiki, "bound to a bone" pose (skeleton_head.animation.json).
    private static final double[] FIRST_ROTATION = {27, -39, -159};
    private static final double[] FIRST_POSITION = {0, 14.5, 2.4};
    private static final double[] THIRD_ROTATION = {-20, -32.5, 0};
    private static final double[] THIRD_POSITION = {0, 19, -4};
    private static final double THIRD_SCALE = 0.65;
    // Centre of the Wiki's reference model: a vanilla-sized head, 8 tall, from y=24.
    static final double[] REFERENCE_CENTER = {0, 28, 0};

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
     * @param center    the model's centre in Bedrock model space, which is also the bone's
     *                  pivot. The Wiki poses were tuned for a head centred at
     *                  {@link #REFERENCE_CENTER}; the difference is added to every position
     *                  so a model that sits lower (Kalo's start at y=0) is not drawn low.
     */
    static @NotNull JsonObject animations(@NotNull String shorthand, @NotNull JsonObject javaModel,
                                          @NotNull String boneName, double[] center) {
        JsonObject display = javaModel.has("display") && javaModel.get("display").isJsonObject()
                ? javaModel.getAsJsonObject("display") : new JsonObject();
        JsonObject animations = new JsonObject();
        animations.add(animationId(shorthand, true),
                pose(boneName, display.get(FIRST_PERSON), FIRST_ROTATION, FIRST_POSITION, 1.0, false, center));
        animations.add(animationId(shorthand, false),
                pose(boneName, display.get(THIRD_PERSON), THIRD_ROTATION, THIRD_POSITION, THIRD_SCALE, true, center));
        JsonObject root = new JsonObject();
        root.addProperty("format_version", "1.10.0");
        root.add("animations", animations);
        return root;
    }

    private static @NotNull JsonObject pose(@NotNull String bone, @Nullable JsonElement authored,
                                            double[] baseRotation, double[] basePosition,
                                            double baseScale, boolean thirdPerson,
                                            double[] center) {
        double[] rotation = baseRotation.clone();
        double[] position = basePosition.clone();
        double scale = baseScale;
        if (authored != null && authored.isJsonObject()) {
            JsonObject transform = authored.getAsJsonObject();
            double[] javaRotation = vector(transform.get("rotation"), 0);
            double[] javaTranslation = vector(transform.get("translation"), 0);
            double[] javaScale = vector(transform.get("scale"), 1);
            // Authored rotation replaces the baseline; mirroring X reverses Y and Z.
            rotation = new double[]{javaRotation[0], -javaRotation[1], -javaRotation[2]};
            // Java translation is clamped to ±80 in 1/16 block; Bedrock position is in the
            // same pixel units. Offset from the baseline so the item still reaches the hand.
            position = new double[]{
                    basePosition[0] - javaTranslation[0],
                    basePosition[1] + javaTranslation[1],
                    basePosition[2] - javaTranslation[2]};
            // Bedrock takes one uniform scale; average the three Java axes.
            scale = (javaScale[0] + javaScale[1] + javaScale[2]) / 3.0;
            if (thirdPerson && transform.get("scale") == null) {
                scale = baseScale;
            }
        }
        for (int axis = 0; axis < 3; axis++) {
            position[axis] += REFERENCE_CENTER[axis] - center[axis];
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
