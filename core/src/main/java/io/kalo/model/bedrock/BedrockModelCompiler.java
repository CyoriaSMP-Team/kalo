package io.kalo.model.bedrock;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.kalo.model.ModelIR;
import io.kalo.model.ModelIR.Animation;
import io.kalo.model.ModelIR.Animator;
import io.kalo.model.ModelIR.Bone;
import io.kalo.model.ModelIR.Cube;
import io.kalo.model.ModelIR.Diagnostic;
import io.kalo.model.ModelIR.DisplayTransform;
import io.kalo.model.ModelIR.Face;
import io.kalo.model.ModelIR.Keyframe;
import io.kalo.model.ModelIR.Severity;
import io.kalo.model.ModelIR.Vec3;
import io.kalo.model.ModelTextureResolver;
import io.kalo.model.animation.BezierInterpolation;
import net.kyori.adventure.key.Key;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/**
 * Compiles the model IR to Bedrock geometry, animations and animation controllers.
 *
 * <p>The IR keeps Blockbench's outliner, and the compiler emits it as real Bedrock bones
 * with parents, pivots and rotations instead of flattening the model into one bone. A cube
 * stays on the bone that authored it, so a shoulder rotation still carries the arm.</p>
 *
 * <p>{@link #compile} takes Java block/item space: X mirrored, X and Z shifted by half a
 * block, and X/Y rotations negated. {@link #compileEntity} takes centred Bedrock entity
 * space and copies it through unchanged. In both cases the output is plain Bedrock
 * animation JSON — which runtime plays it (placed model, block, attachable) is decided
 * outside this class.</p>
 */
public final class BedrockModelCompiler {

    private static final List<String> FACE_ORDER =
            List.of("north", "south", "east", "west", "up", "down");
    /** Samples inserted per Bezier segment; Bedrock keyframes only interpolate linearly. */
    private static final int BEZIER_BAKE_STEPS = 8;

    private BedrockModelCompiler() {
    }

    /**
     * Compiles a model authored in Java block/item space.
     *
     * @param model      source model
     * @param identifier Bedrock geometry identifier
     * @param overrides  author texture overrides, keyed by texture id, name or path
     * @param namespace  namespace for textures that come from the model itself
     * @param assetStem  stable stem for generated asset names
     */
    public static @NotNull Result compile(@NotNull ModelIR model,
                                          @NotNull String identifier,
                                          @NotNull Map<String, Key> overrides,
                                          @NotNull String namespace,
                                          @NotNull String assetStem) {
        return compile(model, identifier, overrides, namespace, assetStem, false);
    }

    /**
     * Compiles a model authored in Bedrock entity space — feet on y=0, X and Z already
     * centred — as mobs and worn armor are. No mirroring or centring is applied, no hold
     * animations are generated, and keyframe values keep the signs they were authored
     * with.
     */
    public static @NotNull Result compileEntity(@NotNull ModelIR model,
                                                @NotNull String identifier,
                                                @NotNull Map<String, Key> overrides,
                                                @NotNull String namespace,
                                                @NotNull String assetStem) {
        return compile(model, identifier, overrides, namespace, assetStem, true);
    }

    private static @NotNull Result compile(@NotNull ModelIR model,
                                           @NotNull String identifier,
                                           @NotNull Map<String, Key> overrides,
                                           @NotNull String namespace,
                                           @NotNull String assetStem,
                                           boolean entitySpace) {
        List<Diagnostic> diagnostics = new ArrayList<>(model.diagnostics());
        ModelTextureResolver.Result textures = ModelTextureResolver.resolve(model, overrides,
                namespace, assetStem);
        diagnostics.addAll(textures.diagnostics());
        if (!textures.complete()) {
            return failed(diagnostics, "bedrock.textures",
                    "Bedrock geometry cannot be compiled because one or more textures did"
                            + " not resolve");
        }
        if (textures.textures().isEmpty()) {
            return failed(diagnostics, "bedrock.no-textures",
                    "Bedrock geometry has no textures to bind");
        }

        Map<String, String> boneNames = new LinkedHashMap<>();
        for (Bone bone : model.bones()) {
            String name = uniqueBoneName(bone, boneNames);
            boneNames.put(bone.id(), name);
        }

        JsonArray bones = new JsonArray();
        int cubes = 0;
        for (Bone bone : model.bones()) {
            JsonObject boneJson = new JsonObject();
            boneJson.addProperty("name", boneNames.get(bone.id()));
            String parent = bone.parentId() == null ? null : boneNames.get(bone.parentId());
            if (parent != null) {
                boneJson.addProperty("parent", parent);
            } else if (bone.parentId() != null) {
                diagnostics.add(new Diagnostic(Severity.WARNING, "bedrock.missing-parent",
                        "Bone '" + bone.name() + "' names missing parent '"
                                + bone.parentId() + "'; it was emitted as a root"));
            }
            boneJson.add("pivot", bedrockVec(bone.pivot(), entitySpace));
            if (!bone.rotation().equals(Vec3.ZERO)) {
                boneJson.add("rotation", bedrockRotation(bone.rotation(), entitySpace));
            }
            if (!bone.visible() || !bone.export()) {
                diagnostics.add(new Diagnostic(Severity.INFO, "bedrock.bone-hidden",
                        "Bone '" + bone.name() + "' is hidden/not exported; Bedrock has no"
                                + " bone visibility flag, so the bone is kept for its child"
                                + " transforms and its cubes are omitted"));
            }

            JsonArray boneCubes = new JsonArray();
            if (bone.visible() && bone.export()) {
                for (Cube cube : bone.cubes()) {
                    if (!cube.visible() || !cube.export()) {
                        diagnostics.add(new Diagnostic(Severity.INFO, "bedrock.cube-hidden",
                                "Cube '" + cube.name() + "' is hidden/not exported and was"
                                        + " omitted from Bedrock geometry"));
                        continue;
                    }
                    boneCubes.add(cube(cube, textures, entitySpace));
                    cubes++;
                }
            }
            if (!boneCubes.isEmpty()) {
                boneJson.add("cubes", boneCubes);
            }
            bones.add(boneJson);
        }

        if (cubes == 0) {
            return failed(diagnostics, "bedrock.no-elements",
                    "Bedrock geometry has no visible, exportable cubes");
        }
        AnimationBuild animationBuild = buildAnimations(model, boneNames, assetStem,
                diagnostics, entitySpace);
        ControllerBuild controllerBuild = buildControllers(model, assetStem,
                animationBuild.names, diagnostics);
        long roots = model.bones().stream().filter(bone -> bone.parentId() == null).count();
        if (roots > 1) {
            diagnostics.add(new Diagnostic(Severity.WARNING, "bedrock.multiple-roots",
                    "The outliner has " + roots + " root bones; a held-item attachable binds"
                            + " only its first root to the item slot"));
        }
        if (!model.display().isEmpty()) {
            diagnostics.add(new Diagnostic(Severity.INFO, "bedrock.display-transforms",
                    "Blockbench display transforms are Java item-model data; Bedrock"
                            + " attachables use their own hold animations"));
        }

        JsonObject description = new JsonObject();
        description.addProperty("identifier", identifier);
        description.addProperty("texture_width", model.textureWidth());
        description.addProperty("texture_height", model.textureHeight());
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

        return new Result(root, textures.textures().values().stream().toList(),
                textures.materials(), animationBuild.json(), animationBuild.ids(),
                animationBuild.loopIds(), controllerBuild.json(),
                controllerBuild.ids(), List.copyOf(diagnostics));
    }

    /**
     * Converts the IR's keyframes into Bedrock animation JSON. Which animations get wired
     * up is the attachable's decision; this method only records what it could not express.
     */
    private static @NotNull AnimationBuild buildAnimations(
            @NotNull ModelIR model,
            @NotNull Map<String, String> boneNames,
            @NotNull String assetStem,
            @NotNull List<Diagnostic> diagnostics,
            boolean entitySpace) {
        JsonObject animations = new JsonObject();
        Map<String, String> ids = new LinkedHashMap<>();
        Map<String, String> names = new LinkedHashMap<>();
        List<String> loopIds = new ArrayList<>();
        java.util.Set<String> usedShortNames = new java.util.LinkedHashSet<>(
                List.of("hold_first_person", "hold_third_person"));

        for (Animation animation : model.animations()) {
            JsonObject bones = new JsonObject();
            for (Animator animator : animation.animators()) {
                if (!"bone".equals(animator.type())) {
                    diagnostics.add(new Diagnostic(Severity.UNSUPPORTED,
                            "bedrock.animation-animator",
                            "Animation '" + animation.name() + "' targets '"
                                    + animator.name() + "' as type '" + animator.type()
                                    + "'; only bone animators can be wired to attachables"));
                    continue;
                }
                String bone = boneNames.get(animator.nodeId());
                if (bone == null) {
                    for (Bone candidate : model.bones()) {
                        if (candidate.name().equals(animator.name())) {
                            bone = boneNames.get(candidate.id());
                            break;
                        }
                    }
                }
                if (bone == null) {
                    diagnostics.add(new Diagnostic(Severity.UNSUPPORTED,
                            "bedrock.animation-target",
                            "Animation '" + animation.name() + "' targets '"
                                    + animator.name() + "', which does not resolve to an"
                                    + " exported Bedrock bone"));
                    continue;
                }

                JsonObject channels = new JsonObject();
                for (String channel : List.of("rotation", "position", "scale")) {
                    JsonObject keyframes = buildChannel(animator, channel, animation.name(),
                            diagnostics, entitySpace);
                    if (keyframes != null && !keyframes.isEmpty()) {
                        channels.add(channel, keyframes);
                    }
                }
                if (!channels.isEmpty()) {
                    bones.add(bone, channels);
                }
            }
            if (bones.isEmpty()) {
                diagnostics.add(new Diagnostic(Severity.WARNING,
                        "bedrock.animation-empty",
                        "Animation '" + animation.name() + "' has no representable bone"
                                + " channels and was not emitted"));
                continue;
            }

            String shortName = unique(usedShortNames, sanitize(animation.name()));
            usedShortNames.add(shortName);
            names.put(sanitize(animation.name()), shortName);
            String id = "animation.kalo." + sanitize(assetStem) + "." + shortName;
            ids.put(shortName, id);
            JsonObject out = new JsonObject();
            String loop = animation.loop();
            if ("loop".equals(loop)) {
                out.addProperty("loop", true);
                loopIds.add(shortName);
            } else if ("hold".equals(loop)) {
                // Bedrock's name for "play once, then stay on the final frame".
                out.addProperty("loop", "hold_on_last_frame");
            }
            double maxTime = animation.length();
            for (Animator animator : animation.animators()) {
                for (Keyframe keyframe : animator.keyframes()) {
                    maxTime = Math.max(maxTime, keyframe.time());
                }
            }
            out.addProperty("animation_length", clean(Math.max(0.05, maxTime)));
            out.add("bones", bones);
            animations.add(id, out);
        }

        if (!entitySpace) {
            addHoldAnimations(model, boneNames, assetStem, animations, ids, diagnostics);
        }

        if (animations.isEmpty()) {
            return new AnimationBuild(null, Map.of(), List.of(), Map.of());
        }
        JsonObject root = new JsonObject();
        root.addProperty("format_version", "1.8.0");
        root.add("animations", animations);
        return new AnimationBuild(root, ids, loopIds, names);
    }

    /**
     * Generates the hold clips an attachable needs. Display transforms authored in
     * Blockbench take precedence over the built-in fallback.
     */
    private static void addHoldAnimations(
            @NotNull ModelIR model,
            @NotNull Map<String, String> boneNames,
            @NotNull String assetStem,
            @NotNull JsonObject animations,
            @NotNull Map<String, String> ids,
            @NotNull List<Diagnostic> diagnostics) {
        String root = null;
        for (Bone bone : model.bones()) {
            if (bone.parentId() == null) {
                root = boneNames.get(bone.id());
                if (root != null) {
                    break;
                }
            }
        }
        if (root == null && !model.bones().isEmpty()) {
            root = boneNames.get(model.bones().getFirst().id());
        }
        if (root == null) {
            return;
        }

        DisplayTransform first = model.display().get("firstperson_righthand");
        if (first == null) {
            first = model.display().get("firstperson_lefthand");
        }
        DisplayTransform third = model.display().get("thirdperson_righthand");
        if (third == null) {
            third = model.display().get("thirdperson_lefthand");
        }
        BedrockHoldAnimations.Result hold = BedrockHoldAnimations.build(root, first, third,
                assetStem);
        hold.json().getAsJsonObject("animations").entrySet()
                .forEach(entry -> animations.add(entry.getKey(), entry.getValue()));
        ids.putAll(hold.ids());

        long roots = model.bones().stream().filter(bone -> bone.parentId() == null).count();
        if (roots > 1) {
            diagnostics.add(new Diagnostic(Severity.WARNING, "bedrock.hold-multiple-roots",
                    "The model has " + roots + " root bones; hold transforms are applied to"
                            + " the first root only, matching the attachable binding"));
        }
    }

    /**
     * Builds one animation channel for an animator, ordered by time. Returns {@code null}
     * when the animator has no usable keys on that channel.
     */
    private static @Nullable JsonObject buildChannel(
            @NotNull Animator animator,
            @NotNull String channel,
            @NotNull String animationName,
            @NotNull List<Diagnostic> diagnostics,
            boolean entitySpace) {
        TreeMap<Double, Keyframe> ordered = new TreeMap<>();
        for (Keyframe keyframe : animator.keyframes()) {
            if (!channel.equals(keyframe.channel())) {
                continue;
            }
            if (keyframe.dataPoints().isEmpty()) {
                continue;
            }
            if (keyframe.dataPoints().size() > 1) {
                diagnostics.add(new Diagnostic(Severity.UNSUPPORTED,
                        "bedrock.animation-discontinuous",
                        "Animation '" + animationName + "' channel " + channel
                                + " has a discontinuous keyframe; only its first value"
                                + " was emitted"));
            }
            ordered.put(keyframe.time(), keyframe);
        }
        if (ordered.isEmpty()) {
            return null;
        }

        JsonObject output = new JsonObject();
        Keyframe previous = null;
        JsonArray previousValue = null;
        int bakedBezierKeys = 0;
        for (Map.Entry<Double, Keyframe> entry : ordered.entrySet()) {
            Keyframe keyframe = entry.getValue();
            if (previous != null
                    && !"step".equalsIgnoreCase(previous.interpolation())
                    && BezierInterpolation.usesBezier(previous.interpolation(),
                    keyframe.interpolation())) {
                bakedBezierKeys += bakeBezier(output, previous, keyframe, channel, entitySpace);
            }
            JsonArray value = animationValue(channel, keyframe, entitySpace);
            JsonElement node;
            String interpolation = keyframe.interpolation()
                    .toLowerCase(Locale.ROOT);
            if (previous != null && "step".equals(previous.interpolation()
                    .toLowerCase(Locale.ROOT)) && previousValue != null) {
                JsonObject step = new JsonObject();
                step.add("pre", previousValue.deepCopy());
                step.add("post", value);
                node = step;
            } else if ("catmullrom".equals(interpolation)
                    || "smooth".equals(interpolation)) {
                JsonObject smooth = new JsonObject();
                smooth.add("post", value);
                smooth.addProperty("lerp_mode", "catmullrom");
                node = smooth;
            } else {
                // Bezier segments were sampled above; the points added here are linear.
                node = value;
            }
            output.add(timeKey(entry.getKey()), node);
            previous = keyframe;
            previousValue = value;
        }
        if (bakedBezierKeys > 0) {
            diagnostics.add(new Diagnostic(Severity.INFO,
                    "bedrock.animation-bezier-baked",
                    "Animation '" + animationName + "' channel " + channel
                            + " baked its Bezier segments into " + bakedBezierKeys
                            + " intermediate linear keyframes"));
        }
        return output;
    }

    /** Samples the span between two Bezier keys into intermediate linear keys. */
    private static int bakeBezier(@NotNull JsonObject output,
                                  @NotNull Keyframe left,
                                  @NotNull Keyframe right,
                                  @NotNull String channel,
                                  boolean entitySpace) {
        double gap = right.time() - left.time();
        if (gap <= 0) {
            return 0;
        }
        int keys = 0;
        for (int step = 1; step < BEZIER_BAKE_STEPS; step++) {
            double time = left.time() + gap * step / BEZIER_BAKE_STEPS;
            Vec3 value = BezierInterpolation.sample(left, right, time);
            output.add(timeKey(time), animationValue(channel, value, entitySpace));
            keys++;
        }
        return keys;
    }

    private static JsonArray animationValue(String channel, Keyframe keyframe,
                                            boolean entitySpace) {
        return animationValue(channel, keyframe.dataPoints().getFirst(), entitySpace);
    }

    private static JsonArray animationValue(String channel, Vec3 value,
                                            boolean entitySpace) {
        if (entitySpace) {
            // Entity-space input needs no mirroring or sign change.
            return array(value.x(), value.y(), value.z());
        }
        return switch (channel) {
            // Mirrored space: X and Y angles invert, and so does X translation.
            case "rotation" -> array(-value.x(), -value.y(), value.z());
            case "position" -> array(-value.x(), value.y(), value.z());
            default -> array(value.x(), value.y(), value.z());
        };
    }

    /** Bedrock time keys are strings, so whole seconds are given an explicit decimal. */
    private static @NotNull String timeKey(double time) {
        BigDecimal decimal = BigDecimal.valueOf(Math.max(0, time))
                .stripTrailingZeros();
        String text = decimal.toPlainString();
        return text.contains(".") ? text : text + ".0";
    }

    /** Fits an arbitrary name into Bedrock's lower-case identifier alphabet. */
    private static @NotNull String sanitize(@Nullable String value) {
        String cleaned = value == null ? "" : value.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9_]+", "_")
                .replaceAll("_{2,}", "_")
                .replaceAll("^_+|_+$", "");
        return cleaned.isBlank() ? "animation" : cleaned;
    }

    /** Appends {@code _2}, {@code _3}, … until the name is unused. */
    private static @NotNull String unique(@NotNull java.util.Set<String> taken,
                                          @NotNull String base) {
        String candidate = base;
        int suffix = 2;
        while (taken.contains(candidate)) {
            candidate = base + "_" + suffix++;
        }
        return candidate;
    }

    private record AnimationBuild(@Nullable JsonObject json,
                                  @NotNull Map<String, String> ids,
                                  @NotNull List<String> loopIds,
                                  @NotNull Map<String, String> names) {
        private AnimationBuild {
            ids = java.util.Collections.unmodifiableMap(
                    new LinkedHashMap<>(ids));
            loopIds = List.copyOf(loopIds);
            names = java.util.Collections.unmodifiableMap(
                    new LinkedHashMap<>(names));
        }
    }

    private record ControllerBuild(@Nullable JsonObject json,
                                   @NotNull List<String> ids) {
        private ControllerBuild {
            ids = List.copyOf(ids);
        }
    }

    /**
     * Emits one controller definition per IR controller, giving generated names the
     * {@code controller.animation.<stem>.} prefix.
     */
    private static @NotNull ControllerBuild buildControllers(
            @NotNull ModelIR model,
            @NotNull String assetStem,
            @NotNull Map<String, String> animationNames,
            @NotNull List<Diagnostic> diagnostics) {
        if (model.controllers().isEmpty()) {
            return new ControllerBuild(null, List.of());
        }
        JsonObject table = new JsonObject();
        List<String> ids = new ArrayList<>();
        for (ModelIR.Controller controller : model.controllers()) {
            String name = controller.name().startsWith("controller.animation.")
                    ? controller.name()
                    : "controller.animation." + sanitize(assetStem) + "."
                    + sanitize(controller.name());
            JsonObject entry = new JsonObject();
            entry.addProperty("initial_state", controller.initialState());
            JsonObject states = new JsonObject();
            for (Map.Entry<String, ModelIR.State> stateEntry
                    : controller.states().entrySet()) {
                ModelIR.State state = stateEntry.getValue();
                JsonObject stateJson = new JsonObject();
                JsonArray animations = new JsonArray();
                for (ModelIR.AnimationRef reference : state.animations()) {
                    String shortName = animationNames.get(sanitize(reference.animation()));
                    if (shortName == null) {
                        diagnostics.add(new Diagnostic(Severity.WARNING,
                                "bedrock.controller-animation",
                                "Controller '" + name + "' plays missing animation '"
                                        + reference.animation() + "'"));
                        shortName = sanitize(reference.animation());
                    }
                    if (reference.blendExpression() == null) {
                        animations.add(shortName);
                    } else {
                        JsonObject blend = new JsonObject();
                        blend.addProperty(shortName, reference.blendExpression());
                        animations.add(blend);
                    }
                }
                stateJson.add("animations", animations);
                if (!state.variables().isEmpty()) {
                    JsonObject variables = new JsonObject();
                    for (Map.Entry<String, ModelIR.Variable> variable
                            : state.variables().entrySet()) {
                        JsonObject value = new JsonObject();
                        value.addProperty("input", variable.getValue().input());
                        if (!variable.getValue().remapCurve().isEmpty()) {
                            JsonObject curve = new JsonObject();
                            variable.getValue().remapCurve().forEach((point, mapped) ->
                                    curve.addProperty(String.valueOf(point), mapped));
                            value.add("remap_curve", curve);
                        }
                        variables.add(variable.getKey(), value);
                    }
                    stateJson.add("variables", variables);
                }
                if (!state.transitions().isEmpty()) {
                    JsonArray transitions = new JsonArray();
                    for (ModelIR.Transition transition : state.transitions()) {
                        JsonObject object = new JsonObject();
                        object.addProperty(transition.target(), transition.condition());
                        transitions.add(object);
                    }
                    stateJson.add("transitions", transitions);
                }
                if (state.blendTransition() != null) {
                    stateJson.addProperty("blend_transition", state.blendTransition());
                }
                states.add(stateEntry.getKey(), stateJson);
            }
            entry.add("states", states);
            table.add(name, entry);
            ids.add(name);
        }
        JsonObject root = new JsonObject();
        root.addProperty("format_version", "1.10.0");
        root.add("animation_controllers", table);
        return new ControllerBuild(root, ids);
    }

    private static JsonObject cube(Cube cube, ModelTextureResolver.Result textures,
                                   boolean entitySpace) {
        JsonObject json = new JsonObject();
        Vec3 from = cube.from();
        Vec3 to = cube.to();

        if (entitySpace) {
            json.add("origin", array(from.x(), from.y(), from.z()));
        } else {
            // Mirrored, centred space: the origin comes from the far X corner, and X/Z
            // are pulled back by half a block. BedrockGeometry applies the same rule.
            json.add("origin", array(8 - to.x(), from.y(), from.z() - 8));
        }
        json.add("size", array(to.x() - from.x(), to.y() - from.y(),
                to.z() - from.z()));
        if (cube.inflate() != 0.0) {
            json.addProperty("inflate", clean(cube.inflate()));
        }
        if (!cube.rotation().equals(Vec3.ZERO)) {
            json.add("pivot", entitySpace
                    ? array(cube.pivot().x(), cube.pivot().y(), cube.pivot().z())
                    : array(8 - cube.pivot().x(), cube.pivot().y(),
                    cube.pivot().z() - 8));
            json.add("rotation", bedrockRotation(cube.rotation(), entitySpace));
        }

        JsonObject faces = new JsonObject();
        ModelTextureResolver.ResolvedTexture fallback = textures.first();
        for (String faceName : FACE_ORDER) {
            Face face = cube.faces().get(faceName);
            if (face == null) {
                continue;
            }
            ModelTextureResolver.ResolvedTexture resolved = textures.texture(face.textureId());
            if (resolved == null) {
                resolved = fallback;
            }
            if (resolved == null) {
                continue;
            }
            JsonObject faceJson = new JsonObject();
            if ("up".equals(faceName) || "down".equals(faceName)) {
                // Horizontal faces are wound the other way in Bedrock. The negative size
                // is expected; normalising it would flip the texture.
                faceJson.add("uv", array(face.u2(), face.v2()));
                faceJson.add("uv_size", array(face.u1() - face.u2(), face.v1() - face.v2()));
            } else {
                faceJson.add("uv", array(face.u1(), face.v1()));
                faceJson.add("uv_size", array(face.u2() - face.u1(), face.v2() - face.v1()));
            }
            if (face.rotation() != 0) {
                faceJson.addProperty("uv_rotation", face.rotation());
            }
            faceJson.addProperty("material_instance", resolved.material());
            faces.add(faceName, faceJson);
        }
        json.add("uv", faces);
        return json;
    }

    /**
     * Produces a Bedrock-safe bone name and keeps it distinct from names already used by
     * this model.
     */
    private static String uniqueBoneName(Bone bone, Map<String, String> taken) {
        String base = bone.name() == null ? "bone" : bone.name()
                .toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_.-]", "_");
        if (base.isBlank()) {
            base = "bone";
        }
        if (Character.isDigit(base.charAt(0))) {
            base = "bone_" + base;
        }
        String candidate = base;
        int suffix = 2;
        while (taken.containsValue(candidate)) {
            candidate = base + "_" + suffix++;
        }
        return candidate;
    }

    /** Converts a rotation into Bedrock axes, leaving entity-space values alone. */
    private static JsonArray bedrockRotation(Vec3 rotation, boolean entitySpace) {
        if (entitySpace) {
            return array(rotation.x(), rotation.y(), rotation.z());
        }
        // X and Y flip with the mirrored space; Z does not.
        return array(-rotation.x(), -rotation.y(), rotation.z());
    }

    /** Converts a point into Bedrock's centred space, unless it is already there. */
    private static JsonArray bedrockVec(Vec3 value, boolean entitySpace) {
        if (entitySpace) {
            return array(value.x(), value.y(), value.z());
        }
        return array(8 - value.x(), value.y(), value.z() - 8);
    }

    private static JsonArray array(double... values) {
        JsonArray array = new JsonArray();
        for (double value : values) {
            // Whole values serialize as 8, not 8.0, matching hand-written geometry files.
            if (value == Math.rint(value)
                    && value >= Integer.MIN_VALUE && value <= Integer.MAX_VALUE) {
                array.add((int) value);
            } else {
                array.add(value);
            }
        }
        return array;
    }

    /** Narrows whole numbers to {@code int} for the same reason as {@link #array}. */
    private static Number clean(double value) {
        return value == Math.rint(value)
                && value >= Integer.MIN_VALUE && value <= Integer.MAX_VALUE
                ? (Number) (int) value : (Number) value;
    }

    /** Records a fatal diagnostic and returns an empty result. */
    private static Result failed(List<Diagnostic> diagnostics, String code, String message) {
        diagnostics.add(new Diagnostic(Severity.ERROR, code, message));
        return new Result(null, List.of(), Map.of(), null, Map.of(), List.of(),
                null, List.of(), List.copyOf(diagnostics));
    }

    /**
     * Compiler output: the geometry document, the textures it refers to, the animation and
     * controller documents, and everything needed to reference them by name.
     */
    public record Result(
            @Nullable JsonObject geometry,
            @NotNull List<ModelTextureResolver.ResolvedTexture> textures,
            @NotNull Map<Key, String> materials,
            @Nullable JsonObject animations,
            @NotNull Map<String, String> animationIds,
            @NotNull List<String> loopAnimations,
            @Nullable JsonObject controllers,
            @NotNull List<String> controllerIds,
            @NotNull List<Diagnostic> diagnostics
    ) {
        public Result {
            textures = List.copyOf(textures);
            materials = java.util.Collections.unmodifiableMap(
                    new LinkedHashMap<>(materials));
            animationIds = java.util.Collections.unmodifiableMap(
                    new LinkedHashMap<>(animationIds));
            loopAnimations = List.copyOf(loopAnimations);
            controllerIds = List.copyOf(controllerIds);
            diagnostics = List.copyOf(diagnostics);
        }

        /** True when a geometry was produced; false means only diagnostics are useful. */
        public boolean compiled() {
            return geometry != null;
        }

        /** All ERROR messages joined with {@code "; "}, or a generic fallback. */
        public @NotNull String errorSummary() {
            return diagnostics.stream()
                    .filter(diagnostic -> diagnostic.severity() == Severity.ERROR)
                    .map(Diagnostic::message)
                    .reduce((left, right) -> left + "; " + right)
                    .orElse("unsupported model");
        }
    }
}
