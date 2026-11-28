package io.kalo.model.animation;

import io.kalo.model.ModelIR;
import io.kalo.model.ModelIR.Animation;
import io.kalo.model.ModelIR.Animator;
import io.kalo.model.ModelIR.Keyframe;
import io.kalo.model.ModelIR.Vec3;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Samples Blockbench animation channels at runtime.
 *
 * <p>The IR is the source of truth for both platforms: Bedrock gets JSON, Java samples the
 * same keyframes here. Times are seconds, as authored in Blockbench. Linear, step,
 * cubic Bezier and Catmull-Rom segments are sampled. Catmull-Rom uses the immediate
 * neighbouring keys, matching the generated Bedrock {@code lerp_mode}.</p>
 *
 * <p>Only {@code loop} animations are auto-played, matching the Bedrock client entity's
 * {@code scripts.animate} list. Multiple loops on the same bone resolve last-wins, which
 * keeps the Java pose deterministic without pretending to implement Bedrock's additive
 * blending.</p>
 */
public final class ModelAnimations {

    private ModelAnimations() {
    }

    /** Every animation the model declares with loop mode {@code loop}. */
    public static @NotNull List<Clip> loops(@NotNull ModelIR model) {
        List<Clip> clips = new ArrayList<>();
        for (Clip clip : all(model)) {
            if (clip.isLoop()) {
                clips.add(clip);
            }
        }
        return List.copyOf(clips);
    }

    /** Every authored animation, loop or one-shot, for interaction playback. */
    public static @NotNull List<Clip> all(@NotNull ModelIR model) {
        List<Clip> clips = new ArrayList<>();
        for (Animation animation : model.animations()) {
            Clip clip = compile(animation);
            if (clip != null) {
                clips.add(clip);
            }
        }
        return List.copyOf(clips);
    }

    /**
     * Finds a clip by authored or sanitized name, case-insensitively. Place YAML names
     * are human names while Bedrock ids are sanitized, so both spellings resolve.
     */
    public static @Nullable Clip find(@NotNull List<Clip> clips, @Nullable String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        String wanted = normalize(name);
        for (Clip clip : clips) {
            if (normalize(clip.name()).equals(wanted)) {
                return clip;
            }
        }
        return null;
    }

    /** The same rule the Bedrock compiler uses for animation ids. */
    public static @NotNull String normalize(@NotNull String value) {
        String cleaned = value.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9_]+", "_")
                .replaceAll("_{2,}", "_")
                .replaceAll("^_+|_+$", "");
        return cleaned.isBlank() ? "animation" : cleaned;
    }

    /** @return null when the animation has no length or no bone channels to sample */
    public static @Nullable Clip compile(@NotNull Animation animation) {
        if (!Double.isFinite(animation.length()) || animation.length() <= 0) {
            return null;
        }
        Map<String, BoneTrack> tracks = new LinkedHashMap<>();
        for (Animator animator : animation.animators()) {
            if (!"bone".equalsIgnoreCase(animator.type())) {
                continue;
            }
            BoneTrack track = BoneTrack.of(animator);
            if (track != null) {
                tracks.put(track.boneId(), track);
            }
        }
        if (tracks.isEmpty()) {
            return null;
        }
        return new Clip(animation.name(), animation.loop(), animation.length(),
                List.copyOf(tracks.values()));
    }

    public record Clip(
            @NotNull String name,
            @NotNull String loop,
            double length,
            @NotNull List<BoneTrack> tracks
    ) {
        public Clip {
            tracks = List.copyOf(tracks);
        }

        public boolean isLoop() {
            return "loop".equalsIgnoreCase(loop);
        }

        /** Samples at an elapsed wall-clock time, wrapping or holding per loop mode. */
        public @NotNull Pose sample(double elapsedSeconds) {
            double time = switch (loop.toLowerCase(java.util.Locale.ROOT)) {
                case "loop" -> length <= 0 ? 0 : Math.max(0, elapsedSeconds) % length;
                default -> Math.max(0, Math.min(elapsedSeconds, length));
            };
            return sampleAt(time);
        }

        /** Samples and holds the final frame, regardless of the authored loop mode. */
        public @NotNull Pose sampleOnce(double elapsedSeconds) {
            return sampleAt(Math.max(0, Math.min(elapsedSeconds, length)));
        }

        public boolean finished(double elapsedSeconds) {
            return elapsedSeconds >= length;
        }

        private @NotNull Pose sampleAt(double time) {
            Map<String, BonePose> bones = new LinkedHashMap<>();
            for (BoneTrack track : tracks) {
                bones.put(track.boneId(), track.sample(time));
            }
            return new Pose(bones);
        }
    }

    /** One bone's channels. Missing channels stay neutral. */
    public record BoneTrack(
            @NotNull String boneId,
            @NotNull String boneName,
            @Nullable Channel rotation,
            @Nullable Channel position,
            @Nullable Channel scale
    ) {
        static @Nullable BoneTrack of(@NotNull Animator animator) {
            Channel rotation = null;
            Channel position = null;
            Channel scale = null;
            for (String channel : List.of("rotation", "position", "scale")) {
                Channel sampled = Channel.of(animator.keyframes(), channel);
                if (sampled == null) {
                    continue;
                }
                switch (channel) {
                    case "rotation" -> rotation = sampled;
                    case "position" -> position = sampled;
                    default -> scale = sampled;
                }
            }
            if (rotation == null && position == null && scale == null) {
                return null;
            }
            return new BoneTrack(animator.nodeId(), animator.name(), rotation, position, scale);
        }

        public @NotNull BonePose sample(double time) {
            return new BonePose(
                    rotation == null ? Vec3.ZERO : rotation.at(time),
                    position == null ? Vec3.ZERO : position.at(time),
                    scale == null ? Vec3.ONE : scale.at(time));
        }
    }

    /** Sorted keyframes for one channel, using the first authored data point. */
    public record Channel(@NotNull List<Key> keys) {
        public Channel {
            keys = List.copyOf(keys);
        }

        static @Nullable Channel of(@NotNull List<Keyframe> keyframes, @NotNull String channel) {
            List<Key> keys = new ArrayList<>();
            for (Keyframe keyframe : keyframes) {
                if (!channel.equalsIgnoreCase(keyframe.channel())
                        || keyframe.dataPoints().isEmpty()) {
                    continue;
                }
                keys.add(new Key(keyframe.time(), keyframe.dataPoints().getFirst(),
                        keyframe.interpolation(), keyframe.bezier()));
            }
            keys.sort(java.util.Comparator.comparingDouble(Key::time));
            return keys.isEmpty() ? null : new Channel(keys);
        }

        public @NotNull Vec3 at(double time) {
            if (keys.isEmpty()) {
                return Vec3.ZERO;
            }
            Key first = keys.getFirst();
            if (time <= first.time()) {
                return first.value();
            }
            Key last = keys.getLast();
            if (time >= last.time()) {
                return last.value();
            }
            for (int index = 1; index < keys.size(); index++) {
                Key right = keys.get(index);
                if (time > right.time()) {
                    continue;
                }
                Key left = keys.get(index - 1);
                if (time >= right.time()) {
                    return right.value();
                }
                double span = right.time() - left.time();
                if (span <= 0) {
                    return right.value();
                }
                if ("step".equalsIgnoreCase(left.interpolation())) {
                    return left.value();
                }
                if (BezierInterpolation.usesBezier(left.interpolation(),
                        right.interpolation())) {
                    return BezierInterpolation.sample(
                            left.time(), left.value(), left.bezier(),
                            right.time(), right.value(), right.bezier(), time);
                }
                double factor = (time - left.time()) / span;
                if (CatmullRomInterpolation.usesCatmullRom(left.interpolation())) {
                    Key before = index >= 2 ? keys.get(index - 2) : left;
                    Key after = index + 1 < keys.size() ? keys.get(index + 1) : right;
                    return CatmullRomInterpolation.sample(
                            before.value(), left.value(), right.value(), after.value(),
                            factor);
                }
                return new Vec3(
                        mix(left.value().x(), right.value().x(), factor),
                        mix(left.value().y(), right.value().y(), factor),
                        mix(left.value().z(), right.value().z(), factor));
            }
            return last.value();
        }

        private static double mix(double left, double right, double factor) {
            return left + (right - left) * factor;
        }
    }

    public record Key(double time, @NotNull Vec3 value,
                      @NotNull String interpolation,
                      @NotNull ModelIR.BezierHandles bezier) {
        public Key {
            interpolation = interpolation == null ? "linear" : interpolation;
            bezier = bezier == null ? ModelIR.BezierHandles.DEFAULT : bezier;
        }

        public Key(double time, @NotNull Vec3 value) {
            this(time, value, "linear", ModelIR.BezierHandles.DEFAULT);
        }
    }

    public record BonePose(@NotNull Vec3 rotation, @NotNull Vec3 position,
                           @NotNull Vec3 scale) {
        public static final BonePose NEUTRAL = new BonePose(Vec3.ZERO, Vec3.ZERO, Vec3.ONE);
    }

    /** Sampled bones for one moment; absent bones are in their rest pose. */
    public record Pose(@NotNull Map<String, BonePose> bones) {
        public Pose {
            bones = Map.copyOf(bones);
        }

        public @NotNull BonePose bone(@NotNull String boneId) {
            return bones.getOrDefault(boneId, BonePose.NEUTRAL);
        }

        /** Later clips win for the same bone, matching the deterministic last-wins rule. */
        public @NotNull Pose merge(@NotNull Pose later) {
            Map<String, BonePose> merged = new LinkedHashMap<>(bones);
            merged.putAll(later.bones);
            return new Pose(merged);
        }

        /**
         * Linear blend toward another pose. Rotation is interpolated per Euler component
         * rather than as a quaternion; controller blend weights are usually small
         * corrections, and this keeps the Java sampler dependency-free.
         */
        public @NotNull Pose blend(@NotNull Pose other, double weight) {
            double factor = Math.max(0, Math.min(1, weight));
            java.util.Set<String> keys = new java.util.LinkedHashSet<>(bones.keySet());
            keys.addAll(other.bones.keySet());
            Map<String, BonePose> blended = new LinkedHashMap<>();
            for (String key : keys) {
                BonePose left = bones.getOrDefault(key, BonePose.NEUTRAL);
                BonePose right = other.bones.getOrDefault(key, BonePose.NEUTRAL);
                blended.put(key, new BonePose(
                        lerp(left.rotation(), right.rotation(), factor),
                        lerp(left.position(), right.position(), factor),
                        lerp(left.scale(), right.scale(), factor)));
            }
            return new Pose(blended);
        }

        private static Vec3 lerp(Vec3 left, Vec3 right, double factor) {
            return new Vec3(
                    left.x() + (right.x() - left.x()) * factor,
                    left.y() + (right.y() - left.y()) * factor,
                    left.z() + (right.z() - left.z()) * factor);
        }
    }
}
