package io.kalo.model.animation;

import io.kalo.model.ModelIR;
import io.kalo.model.ModelIR.Animation;
import io.kalo.model.ModelIR.Animator;
import io.kalo.model.ModelIR.Bone;
import io.kalo.model.ModelIR.Keyframe;
import io.kalo.model.ModelIR.Vec3;
import net.kyori.adventure.key.Key;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ModelAnimationsTest {

    private static Keyframe rotation(double time, double x, double y, double z) {
        return new Keyframe(time, "rotation", "linear",
                List.of(new Vec3(x, y, z)));
    }

    private static Animation swing(String loop, double length) {
        return new Animation("uuid", "swing", loop, length, List.of(
                new Animator("arm", "arm", "bone", List.of(
                        rotation(0, 0, 0, 0),
                        rotation(0.5, 0, 0, -60),
                        rotation(1.0, 0, 0, 0)))));
    }

    private static ModelIR model(Animation... animations) {
        return new ModelIR(Key.key("testpack", "animated"), "animated", "5.0",
                "bedrock", 16, 16, List.of(),
                List.of(new Bone("arm", "arm", null, new Vec3(8, 8, 8), Vec3.ZERO,
                        true, true, List.of(), List.of())),
                List.of(animations), List.of(), Map.of(), List.of());
    }

    @Test
    void aLoopAnimationInterpolatesAndWraps() {
        ModelAnimations.Clip clip = ModelAnimations.compile(swing("loop", 1.0));
        assertNotNull(clip);
        assertEquals(-30.0, clip.sample(0.25).bone("arm").rotation().z(), 1e-9);
        assertEquals(-60.0, clip.sample(0.5).bone("arm").rotation().z(), 1e-9);
        // 1.25s wraps to 0.25s, not the clamped final frame.
        assertEquals(-30.0, clip.sample(1.25).bone("arm").rotation().z(), 1e-9);
    }

    @Test
    void holdAndOnceClampAtTheLastKeyframe() {
        ModelAnimations.Clip hold = ModelAnimations.compile(swing("hold", 1.0));
        ModelAnimations.Clip once = ModelAnimations.compile(swing("once", 1.0));
        assertNotNull(hold);
        assertNotNull(once);
        assertEquals(0.0, hold.sample(5.0).bone("arm").rotation().z(), 1e-9);
        assertEquals(0.0, once.sample(5.0).bone("arm").rotation().z(), 1e-9);
    }

    @Test
    void allAnimationsIncludeOneShotsAndCanBeFoundByHumanName() {
        Animation once = swing("once", 1.0);
        once = new Animation(once.id(), "Wave Hello", once.loop(), once.length(),
                once.animators());
        ModelIR model = model(swing("loop", 1.0), once);

        var all = ModelAnimations.all(model);
        assertEquals(2, all.size());
        assertEquals(1, ModelAnimations.loops(model).size());
        var found = ModelAnimations.find(all, "wave hello");
        assertNotNull(found);
        assertEquals("Wave Hello", found.name());
        assertEquals("wave_hello", ModelAnimations.normalize("Wave Hello"));
        assertNull(ModelAnimations.find(all, "missing"));
    }

    @Test
    void aOneShotSamplesToItsFinalFrameAndFinishes() {
        ModelAnimations.Clip clip = ModelAnimations.compile(swing("once", 1.0));
        assertNotNull(clip);
        assertEquals(-60.0, clip.sampleOnce(0.5).bone("arm").rotation().z(), 1e-9);
        assertEquals(0.0, clip.sampleOnce(2.0).bone("arm").rotation().z(), 1e-9);
        assertFalse(clip.finished(0.99));
        assertTrue(clip.finished(1.0));
    }

    @Test
    void aBezierSegmentEasesInsteadOfInterpolatingLinearly() throws java.io.IOException {
        ModelIR.BezierHandles leftHandles = new ModelIR.BezierHandles(
                new Vec3(-0.1, -0.1, -0.1), Vec3.ZERO,
                new Vec3(0.5, 0.5, 0.5), Vec3.ZERO);
        ModelIR.BezierHandles rightHandles = new ModelIR.BezierHandles(
                new Vec3(-0.5, -0.5, -0.5), Vec3.ZERO,
                new Vec3(0.1, 0.1, 0.1), Vec3.ZERO);
        Animation animation = new Animation("uuid", "ease", "once", 1.0, List.of(
                new Animator("arm", "arm", "bone", List.of(
                        new Keyframe(0, "rotation", "bezier", List.of(Vec3.ZERO),
                                leftHandles),
                        new Keyframe(1, "rotation", "bezier",
                                List.of(new Vec3(0, 0, 90)), rightHandles)))));
        ModelAnimations.Clip clip = ModelAnimations.compile(animation);
        assertNotNull(clip);

        double quarter = clip.sample(0.25).bone("arm").rotation().z();
        double middle = clip.sample(0.5).bone("arm").rotation().z();
        double threeQuarters = clip.sample(0.75).bone("arm").rotation().z();

        assertTrue(quarter < 22.5, "Bezier must ease in, got " + quarter);
        assertEquals(45.0, middle, 1e-6);
        assertTrue(threeQuarters > 67.5, "Bezier must ease out, got " + threeQuarters);

        StringBuilder samples = new StringBuilder("time,z\n");
        for (int step = 0; step <= 8; step++) {
            double time = step / 8.0;
            samples.append(time).append(',')
                    .append(clip.sample(time).bone("arm").rotation().z())
                    .append('\n');
        }
        Path artifacts = Path.of("build", "reports", "bezier");
        Files.createDirectories(artifacts);
        Files.writeString(artifacts.resolve("java-bezier-samples.csv"),
                samples.toString(), StandardCharsets.UTF_8);
    }

    @Test
    void aCatmullRomSegmentUsesItsNeighbouringKeys() throws java.io.IOException {
        Animation animation = new Animation("uuid", "catmull", "once", 1.5, List.of(
                new Animator("arm", "arm", "bone", List.of(
                        new Keyframe(0, "rotation", "catmullrom",
                                List.of(Vec3.ZERO)),
                        new Keyframe(0.5, "rotation", "catmullrom",
                                List.of(Vec3.ZERO)),
                        new Keyframe(1.0, "rotation", "catmullrom",
                                List.of(new Vec3(0, 0, 90))),
                        new Keyframe(1.5, "rotation", "catmullrom",
                                List.of(Vec3.ZERO))))));
        ModelAnimations.Clip clip = ModelAnimations.compile(animation);
        assertNotNull(clip);

        assertEquals(20.390625, clip.sample(0.625).bone("arm").rotation().z(), 1e-9);
        assertEquals(50.625, clip.sample(0.75).bone("arm").rotation().z(), 1e-9);
        assertEquals(90.0, clip.sample(1.0).bone("arm").rotation().z(), 1e-9);

        StringBuilder samples = new StringBuilder("time,z\n");
        for (int step = 0; step <= 6; step++) {
            double time = step * 0.25;
            samples.append(time).append(',')
                    .append(clip.sample(time).bone("arm").rotation().z())
                    .append('\n');
        }
        Path artifacts = Path.of("build", "reports", "catmull-rom");
        Files.createDirectories(artifacts);
        Files.writeString(artifacts.resolve("java-catmullrom-samples.csv"),
                samples.toString(), StandardCharsets.UTF_8);
    }

    @Test
    void catmullRomIsDrivenByTheLeftKeyLikeBedrockLerpMode() {
        Animation animation = new Animation("uuid", "catmull", "once", 1.5, List.of(
                new Animator("arm", "arm", "bone", List.of(
                        new Keyframe(0, "rotation", "linear", List.of(Vec3.ZERO)),
                        new Keyframe(0.5, "rotation", "catmullrom",
                                List.of(Vec3.ZERO)),
                        new Keyframe(1.0, "rotation", "linear",
                                List.of(new Vec3(0, 0, 90))),
                        new Keyframe(1.5, "rotation", "linear", List.of(Vec3.ZERO))))));
        ModelAnimations.Clip clip = ModelAnimations.compile(animation);
        assertNotNull(clip);

        assertEquals(0.0, clip.sample(0.25).bone("arm").rotation().z(), 1e-9,
                "the incoming segment has a linear left key");
        assertEquals(50.625, clip.sample(0.75).bone("arm").rotation().z(), 1e-9,
                "the outgoing segment carries Bedrock's lerp_mode on its left key");
    }

    @Test
    void aStepKeyframeStillHoldsUntilTheNextKey() {
        Animation animation = new Animation("uuid", "step", "once", 1.0, List.of(
                new Animator("arm", "arm", "bone", List.of(
                        new Keyframe(0, "rotation", "step", List.of(Vec3.ZERO)),
                        new Keyframe(1, "rotation", "linear",
                                List.of(new Vec3(0, 0, 90)))))));
        ModelAnimations.Clip clip = ModelAnimations.compile(animation);
        assertNotNull(clip);

        assertEquals(0.0, clip.sample(0.9).bone("arm").rotation().z(), 1e-9);
        assertEquals(90.0, clip.sample(1.0).bone("arm").rotation().z(), 1e-9);
    }

    @Test
    void aStepKeyframeSwitchesExactlyAtItsTime() {
        Animation animation = new Animation("uuid", "step", "once", 1.0, List.of(
                new Animator("arm", "arm", "bone", List.of(
                        new Keyframe(0, "rotation", "step", List.of(Vec3.ZERO)),
                        new Keyframe(0.5, "rotation", "step",
                                List.of(new Vec3(0, 0, 45))),
                        new Keyframe(1, "rotation", "linear",
                                List.of(new Vec3(0, 0, 90)))))));
        ModelAnimations.Clip clip = ModelAnimations.compile(animation);
        assertNotNull(clip);

        assertEquals(0.0, clip.sample(0.49).bone("arm").rotation().z(), 1e-9);
        assertEquals(45.0, clip.sample(0.5).bone("arm").rotation().z(), 1e-9);
        assertEquals(45.0, clip.sample(0.9).bone("arm").rotation().z(), 1e-9);
    }

    @Test
    void onlyLoopAnimationsAreAutoPlayed() {
        assertEquals(1, ModelAnimations.loops(model(swing("loop", 1.0))).size());
        assertTrue(ModelAnimations.loops(model(swing("once", 1.0))).isEmpty());
        assertTrue(ModelAnimations.loops(model(swing("hold", 1.0))).isEmpty());
    }

    @Test
    void anAnimationWithoutLengthOrBoneChannelsCompilesToNothing() {
        assertNull(ModelAnimations.compile(swing("loop", 0)));
        Animation noTracks = new Animation("uuid", "empty", "loop", 1.0, List.of(
                new Animator("arm", "arm", "bone", List.of())));
        assertNull(ModelAnimations.compile(noTracks));
    }

    @Test
    void positionAndScaleChannelsUseTheirOwnDefaults() {
        Animation animation = new Animation("uuid", "bob", "loop", 2.0, List.of(
                new Animator("arm", "arm", "bone", List.of(
                        new Keyframe(0, "position", "linear",
                                List.of(new Vec3(0, 0, 0))),
                        new Keyframe(2, "position", "linear",
                                List.of(new Vec3(4, 8, 0))),
                        new Keyframe(0, "scale", "linear",
                                List.of(new Vec3(1, 1, 1))),
                        new Keyframe(2, "scale", "linear",
                                List.of(new Vec3(2, 2, 2)))))));
        ModelAnimations.Clip clip = ModelAnimations.compile(animation);
        assertNotNull(clip);

        ModelAnimations.BonePose middle = clip.sample(1.0).bone("arm");
        assertEquals(2.0, middle.position().x(), 1e-9);
        assertEquals(4.0, middle.position().y(), 1e-9);
        assertEquals(1.5, middle.scale().x(), 1e-9);
        assertEquals(Vec3.ZERO, middle.rotation());
    }

    @Test
    void aBoneWithNoTracksStaysNeutral() {
        ModelAnimations.Clip clip = ModelAnimations.compile(swing("loop", 1.0));
        assertNotNull(clip);
        ModelAnimations.BonePose unknown = clip.sample(0.5).bone("missing");
        assertEquals(Vec3.ZERO, unknown.rotation());
        assertEquals(Vec3.ZERO, unknown.position());
        assertEquals(Vec3.ONE, unknown.scale());
    }

    @Test
    void mergingClipsIsLastWinsForTheSameBone() {
        ModelAnimations.Clip first = ModelAnimations.compile(swing("loop", 1.0));
        ModelAnimations.Clip second = ModelAnimations.compile(new Animation(
                "second", "second", "loop", 1.0, List.of(
                new Animator("arm", "arm", "bone", List.of(
                        rotation(0, 10, 0, 0))))));
        assertNotNull(first);
        assertNotNull(second);
        ModelAnimations.Pose merged = first.sample(0.5).merge(second.sample(0.5));
        assertEquals(10.0, merged.bone("arm").rotation().x(), 1e-9);
        assertFalse(merged.bones().isEmpty());
    }
}
