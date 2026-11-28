package io.kalo.model.bedrock;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.kalo.model.ModelIR.DisplayTransform;
import io.kalo.model.ModelIR.Vec3;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Builds the two attachable hold animations every bound 3D item needs.
 *
 * <p>A geometry with {@code binding: q.item_slot_to_bone_name(...)} sits at the player's
 * hand bone. The vanilla {@code animation.steve_head.hold_*} animations are authored for
 * the player skeleton, so they do not position a bound custom model; a model with no
 * custom hold animation appears at the hand's raw scale and orientation. This class emits
 * a small loop animation per view, using authored Blockbench display transforms when they
 * exist and a conservative half-scale fallback otherwise.</p>
 */
public final class BedrockHoldAnimations {

    /** Half the authored model size, matching Java's held-item transform. */
    private static final double BASE_ITEM_SCALE = 0.5;

    private BedrockHoldAnimations() {
    }

    /**
     * @param rootBone the bone carrying the item-slot binding
     * @param first    first-person display transform, or null for the fallback
     * @param third    third-person display transform, or null for the fallback
     * @param assetStem stable name used in the animation ids
     */
    public static @NotNull Result build(@NotNull String rootBone,
                                        @Nullable DisplayTransform first,
                                        @Nullable DisplayTransform third,
                                        @NotNull String assetStem) {
        return build(rootBone, first, third, assetStem, null, null);
    }

    /**
     * @param scaleBase held-item scale replacing {@link #BASE_ITEM_SCALE}, or null
     * @param rotation  explicit Bedrock rotation (X, Y, Z) in degrees, or null
     */
    public static @NotNull Result build(@NotNull String rootBone,
                                        @Nullable DisplayTransform first,
                                        @Nullable DisplayTransform third,
                                        @NotNull String assetStem,
                                        @Nullable Double scaleBase,
                                        @Nullable java.util.List<Double> rotation) {
        DisplayTransform firstTransform = first != null ? first : fallback();
        DisplayTransform thirdTransform = third != null ? third : fallback();
        String firstId = "animation.kalo." + sanitize(assetStem) + ".hold_first_person";
        String thirdId = "animation.kalo." + sanitize(assetStem) + ".hold_third_person";

        JsonObject animations = new JsonObject();
        animations.add(firstId, animation(rootBone, firstTransform, scaleBase, rotation));
        animations.add(thirdId, animation(rootBone, thirdTransform, scaleBase, rotation));

        JsonObject root = new JsonObject();
        root.addProperty("format_version", "1.8.0");
        root.add("animations", animations);

        Map<String, String> ids = new LinkedHashMap<>();
        ids.put("hold_first_person", firstId);
        ids.put("hold_third_person", thirdId);
        return new Result(root, ids);
    }

    /** Parses one Java/Blockbench display slot from a Java model JSON. */
    public static @NotNull DisplayTransform parse(@NotNull JsonObject model,
                                                   @NotNull String slot) {
        JsonObject display = model.has("display") && model.get("display").isJsonObject()
                ? model.getAsJsonObject("display") : null;
        if (display == null || !display.has(slot)
                || !display.get(slot).isJsonObject()) {
            return fallback();
        }
        JsonObject transform = display.getAsJsonObject(slot);
        return new DisplayTransform(
                vec(transform, "rotation", Vec3.ZERO),
                vec(transform, "translation", Vec3.ZERO),
                vec(transform, "scale", Vec3.ONE));
    }

    private static JsonObject animation(String root, DisplayTransform transform,
                                       @Nullable Double scaleBase,
                                       @Nullable java.util.List<Double> rotation) {
        JsonObject bone = new JsonObject();
        if (rotation != null) {
            // An explicit bedrock.rotation is already Bedrock's own space; do not mirror.
            bone.add("rotation", array(rotation.get(0), rotation.get(1), rotation.get(2)));
        } else if (!transform.rotation().equals(Vec3.ZERO)) {
            // Java/Blockbench mirror X, so Bedrock negates X and Y rotations.
            bone.add("rotation", array(-transform.rotation().x(),
                    -transform.rotation().y(), transform.rotation().z()));
        }
        if (!transform.translation().equals(Vec3.ZERO)) {
            // Display translations are in model units; X mirrors with the model.
            bone.add("position", array(-transform.translation().x(),
                    transform.translation().y(), transform.translation().z()));
        }
        // Java's display scale is relative to the renderer's half-size item transform.
        // A Bedrock attachable has no such base, so the same model authored at 16 units
        // arrives a full block wide in the hand. Apply the base here, once.
        double base = scaleBase != null ? scaleBase : BASE_ITEM_SCALE;
        bone.add("scale", array(transform.scale().x() * base,
                transform.scale().y() * base,
                transform.scale().z() * base));
        JsonObject bones = new JsonObject();
        bones.add(root, bone);

        JsonObject animation = new JsonObject();
        animation.addProperty("loop", true);
        animation.addProperty("animation_length", 0.05);
        animation.add("bones", bones);
        return animation;
    }

    private static DisplayTransform fallback() {
        return new DisplayTransform(Vec3.ZERO, Vec3.ZERO, Vec3.ONE);
    }

    private static Vec3 vec(JsonObject parent, String field, Vec3 fallback) {
        if (!parent.has(field) || !parent.get(field).isJsonArray()) {
            return fallback;
        }
        JsonArray array = parent.getAsJsonArray(field);
        if (array.size() != 3) {
            return fallback;
        }
        double x = array.get(0).getAsDouble();
        double y = array.get(1).getAsDouble();
        double z = array.get(2).getAsDouble();
        if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)) {
            return fallback;
        }
        return new Vec3(x, y, z);
    }

    private static JsonArray array(double... values) {
        JsonArray array = new JsonArray();
        for (double value : values) {
            if (value == Math.rint(value)
                    && value >= Integer.MIN_VALUE && value <= Integer.MAX_VALUE) {
                array.add((int) value);
            } else {
                array.add(value);
            }
        }
        return array;
    }

    private static String sanitize(@Nullable String value) {
        String cleaned = value == null ? "" : value.toLowerCase(java.util.Locale.ROOT)
                .replaceAll("[^a-z0-9_]+", "_")
                .replaceAll("_{2,}", "_")
                .replaceAll("^_+|_+$", "");
        return cleaned.isBlank() ? "item" : cleaned;
    }

    public record Result(@NotNull JsonObject json, @NotNull Map<String, String> ids) {
        public Result {
            ids = java.util.Collections.unmodifiableMap(new LinkedHashMap<>(ids));
        }
    }
}
