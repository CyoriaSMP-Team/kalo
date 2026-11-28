package io.kalo.model;

import net.kyori.adventure.key.Key;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Platform-neutral geometry, authored in Blockbench and imported from a {@code .bbmodel}.
 *
 * <p>This is deliberately not a Java model and not a Bedrock geometry. It keeps the
 * information both formats can represent independently: cube bounds, per-face UVs,
 * pivots, rotations, textures, the outliner hierarchy, display transforms and animation
 * keyframes. Each compiler decides what its output format can carry, and reports the rest
 * instead of silently dropping it.</p>
 *
 * <p>All coordinates are Blockbench/Minecraft authoring space: 16 units per block, Y up,
 * X east and Z south. Both compilers translate from this one coordinate system.</p>
 */
public record ModelIR(
        @NotNull Key id,
        @NotNull String name,
        @NotNull String formatVersion,
        @NotNull String modelFormat,
        int textureWidth,
        int textureHeight,
        @NotNull List<Texture> textures,
        @NotNull List<Bone> bones,
        @NotNull List<Animation> animations,
        @NotNull List<Controller> controllers,
        @NotNull Map<String, DisplayTransform> display,
        @NotNull List<Diagnostic> diagnostics
) {

    public ModelIR {
        textures = List.copyOf(textures);
        bones = List.copyOf(bones);
        animations = List.copyOf(animations);
        controllers = List.copyOf(controllers);
        display = Collections.unmodifiableMap(new LinkedHashMap<>(display));
        diagnostics = List.copyOf(diagnostics);
        if (textureWidth < 1 || textureWidth > 4096
                || textureHeight < 1 || textureHeight > 4096) {
            throw new IllegalArgumentException("model texture size must be within 1..4096");
        }
    }

    /** A texture referenced by a Blockbench model. */
    public record Texture(
            @NotNull String id,
            @Nullable String uuid,
            @NotNull String name,
            @Nullable String relativePath,
            int width,
            int height,
            int uvWidth,
            int uvHeight,
            boolean particle,
            @NotNull String renderMode,
            byte @Nullable [] embeddedPng
    ) {
        public Texture {
            embeddedPng = embeddedPng == null ? null : embeddedPng.clone();
        }

        @Override
        public byte @Nullable [] embeddedPng() {
            return embeddedPng == null ? null : embeddedPng.clone();
        }
    }

    /**
     * One outliner node. Cubes are attached here rather than to a flat element list, so a
     * child bone keeps its pivot and parent relationship when compiled to Bedrock.
     */
    public record Bone(
            @NotNull String id,
            @NotNull String name,
            @Nullable String parentId,
            @NotNull Vec3 pivot,
            @NotNull Vec3 rotation,
            boolean visible,
            boolean export,
            @NotNull List<Cube> cubes,
            @NotNull List<String> children
    ) {
        public Bone {
            cubes = List.copyOf(cubes);
            children = List.copyOf(children);
        }
    }

    /** A cube element. {@code from}/{@code to} are the authored corners in 0..16 units. */
    public record Cube(
            @NotNull String id,
            @NotNull String name,
            @NotNull Vec3 from,
            @NotNull Vec3 to,
            @NotNull Vec3 pivot,
            @NotNull Vec3 rotation,
            double inflate,
            boolean visible,
            boolean export,
            @NotNull Map<String, Face> faces
    ) {
        public Cube {
            faces = Map.copyOf(faces);
        }
    }

    /** Per-face UV corners plus the texture id the face resolves through. */
    public record Face(double u1, double v1, double u2, double v2, int rotation,
                       @Nullable String textureId) {
    }

    public record Animation(
            @NotNull String id,
            @NotNull String name,
            @NotNull String loop,
            double length,
            @NotNull List<Animator> animators
    ) {
        public Animation {
            animators = List.copyOf(animators);
        }
    }

    /**
     * A Bedrock animation controller: one active state at a time, transitions evaluated
     * in order, and the active state's animations selected for playback.
     */
    public record Controller(
            @NotNull String name,
            @NotNull String initialState,
            @NotNull Map<String, State> states
    ) {
        public Controller {
            states = java.util.Collections.unmodifiableMap(
                    new java.util.LinkedHashMap<>(states));
        }
    }

    public record State(
            @NotNull List<AnimationRef> animations,
            @NotNull List<Transition> transitions,
            @Nullable Double blendTransition,
            @NotNull Map<String, Variable> variables
    ) {
        public State {
            animations = List.copyOf(animations);
            transitions = List.copyOf(transitions);
            variables = java.util.Collections.unmodifiableMap(
                    new java.util.LinkedHashMap<>(variables));
        }

        public State(List<AnimationRef> animations, List<Transition> transitions,
                     @Nullable Double blendTransition) {
            this(animations, transitions, blendTransition, Map.of());
        }
    }

    /** A per-state Molang variable with an optional linearly remapped curve. */
    public record Variable(@NotNull String input,
                           @NotNull Map<Double, Double> remapCurve) {
        public Variable {
            remapCurve = java.util.Collections.unmodifiableMap(
                    new java.util.TreeMap<>(remapCurve));
        }
    }

    public record AnimationRef(@NotNull String animation, @Nullable String blendExpression) {
    }

    public record Transition(@NotNull String target, @NotNull String condition) {
    }

    public record Animator(
            @NotNull String nodeId,
            @NotNull String name,
            @NotNull String type,
            @NotNull List<Keyframe> keyframes
    ) {
        public Animator {
            keyframes = List.copyOf(keyframes);
        }
    }

    /**
     * Per-axis Blockbench Bezier handles. Times are offsets from the keyframe time;
     * values are offsets from the keyframe value. Blockbench's defaults are -0.1/+0.1
     * seconds and zero value offset.
     */
    public record BezierHandles(
            @NotNull Vec3 leftTime,
            @NotNull Vec3 leftValue,
            @NotNull Vec3 rightTime,
            @NotNull Vec3 rightValue
    ) {
        public static final BezierHandles DEFAULT = new BezierHandles(
                new Vec3(-0.1, -0.1, -0.1), Vec3.ZERO,
                new Vec3(0.1, 0.1, 0.1), Vec3.ZERO);

        public BezierHandles {
            leftTime = java.util.Objects.requireNonNull(leftTime, "leftTime");
            leftValue = java.util.Objects.requireNonNull(leftValue, "leftValue");
            rightTime = java.util.Objects.requireNonNull(rightTime, "rightTime");
            rightValue = java.util.Objects.requireNonNull(rightValue, "rightValue");
        }
    }

    public record Keyframe(
            double time,
            @NotNull String channel,
            @NotNull String interpolation,
            @NotNull List<Vec3> dataPoints,
            @NotNull BezierHandles bezier
    ) {
        public Keyframe {
            dataPoints = List.copyOf(dataPoints);
            bezier = bezier == null ? BezierHandles.DEFAULT : bezier;
        }

        public Keyframe(double time, @NotNull String channel,
                        @NotNull String interpolation,
                        @NotNull List<Vec3> dataPoints) {
            this(time, channel, interpolation, dataPoints, BezierHandles.DEFAULT);
        }
    }

    public record DisplayTransform(@NotNull Vec3 rotation, @NotNull Vec3 translation,
                                   @NotNull Vec3 scale) {
    }

    public record Vec3(double x, double y, double z) {
        public static final Vec3 ZERO = new Vec3(0, 0, 0);
        public static final Vec3 ONE = new Vec3(1, 1, 1);
    }

    public enum Severity {
        INFO,
        WARNING,
        UNSUPPORTED,
        ERROR
    }

    /**
     * A construct that was found and deliberately not carried into one or both formats.
     * Unsupported data is never dropped silently.
     */
    public record Diagnostic(@NotNull Severity severity, @NotNull String code,
                             @NotNull String message) {
    }

    /** Convenience for callers that want to keep a predictable diagnostic map. */
    public @NotNull Map<String, Diagnostic> diagnosticsByCode() {
        Map<String, Diagnostic> indexed = new LinkedHashMap<>();
        diagnostics.forEach(diagnostic -> indexed.putIfAbsent(diagnostic.code(), diagnostic));
        return indexed;
    }
}
