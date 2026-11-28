package io.kalo.model.bedrock;

import com.google.gson.JsonObject;
import io.kalo.model.ModelIR;
import io.kalo.model.blockbench.BlockbenchImporter;
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
import static org.junit.jupiter.api.Assertions.assertTrue;

class BedrockModelCompilerTest {

    private static ModelIR fixture(String name) throws Exception {
        try (var stream = BedrockModelCompilerTest.class.getResourceAsStream(
                "/blockbench/" + name)) {
            assertNotNull(stream);
            return BlockbenchImporter.importJson(
                    new String(stream.readAllBytes(), StandardCharsets.UTF_8),
                    Key.key("testpack", "models/item/thing.bbmodel"), name);
        }
    }

    private static JsonObject geometry(BedrockModelCompiler.Result result) {
        return result.geometry().getAsJsonArray("minecraft:geometry").get(0).getAsJsonObject();
    }

    @Test
    void aSimpleCubeIsMirroredAndCentredLikeBedrocksCoordinateSystem() throws Exception {
        ModelIR imported = fixture("simple_cube.bbmodel");
        BedrockModelCompiler.Result result = BedrockModelCompiler.compile(imported,
                "geometry.kalo.testpack_thing", Map.of("0", Key.key("testpack", "item/ruby")),
                "testpack", "testpack_thing");

        assertTrue(result.compiled(), result.errorSummary());
        JsonObject cube = geometry(result).getAsJsonArray("bones").get(0).getAsJsonObject()
                .getAsJsonArray("cubes").get(0).getAsJsonObject();
        // from [4,0,4], to [12,12,12] -> origin [8-12,0,4-8], size [8,8,8]
        assertEquals(-4, cube.getAsJsonArray("origin").get(0).getAsInt());
        assertEquals(0, cube.getAsJsonArray("origin").get(1).getAsInt());
        assertEquals(-4, cube.getAsJsonArray("origin").get(2).getAsInt());
        assertEquals(8, cube.getAsJsonArray("size").get(0).getAsInt());
    }

    @Test
    void entitySpaceKeepsAuthoredCoordinatesAndSkipsHoldPoses() throws Exception {
        ModelIR imported = fixture("rigged_arm.bbmodel");
        BedrockModelCompiler.Result result = BedrockModelCompiler.compileEntity(imported,
                "geometry.kalo.testpack_golem_mob",
                Map.of("0", Key.key("testpack", "item/steel")),
                "testpack", "testpack_golem_mob");

        assertTrue(result.compiled(), result.errorSummary());
        var bones = geometry(result).getAsJsonArray("bones");
        JsonObject arm = bones.get(1).getAsJsonObject();
        // Pivot [8,10,8] stays where it was authored in entity space.
        assertEquals(8, arm.getAsJsonArray("pivot").get(0).getAsInt());
        assertEquals(10, arm.getAsJsonArray("pivot").get(1).getAsInt());
        assertEquals(8, arm.getAsJsonArray("pivot").get(2).getAsInt());
        JsonObject cube = arm.getAsJsonArray("cubes").get(0).getAsJsonObject();
        // from [4,4,4], to [8,12,8] -> origin [4,4,4], no mirror or centre.
        assertEquals(4, cube.getAsJsonArray("origin").get(0).getAsInt());
        assertEquals(4, cube.getAsJsonArray("origin").get(1).getAsInt());
        assertEquals(4, cube.getAsJsonArray("origin").get(2).getAsInt());
        assertEquals(4, cube.getAsJsonArray("size").get(0).getAsInt());

        assertTrue(result.animationIds().containsKey("swing"));
        assertFalse(result.animationIds().containsKey("hold_first_person"),
                "mob/armor entities must not inherit held-item hold poses");
        JsonObject animation = result.animations().getAsJsonObject("animations")
                .getAsJsonObject("animation.kalo.testpack_golem_mob.swing");
        assertEquals(-60, animation.getAsJsonObject("bones").getAsJsonObject("arm")
                .getAsJsonObject("rotation").getAsJsonArray("0.5").get(2).getAsInt());
    }

    @Test
    void theOutlinerBecomesMultipleParentedBedrockBones() throws Exception {
        ModelIR imported = fixture("rigged_arm.bbmodel");
        BedrockModelCompiler.Result result = BedrockModelCompiler.compile(imported,
                "geometry.kalo.testpack_rig", Map.of("0", Key.key("testpack", "item/steel")),
                "testpack", "testpack_rig");

        assertTrue(result.compiled(), result.errorSummary());
        var bones = geometry(result).getAsJsonArray("bones");
        assertEquals(2, bones.size());
        JsonObject root = bones.get(0).getAsJsonObject();
        JsonObject arm = bones.get(1).getAsJsonObject();
        assertEquals("root", root.get("name").getAsString());
        assertEquals("arm", arm.get("name").getAsString());
        assertEquals("root", arm.get("parent").getAsString());
        // Pivot [8,10,8] is mirrored and centred: [0,10,0].
        assertEquals(0, arm.getAsJsonArray("pivot").get(0).getAsInt());
        assertEquals(10, arm.getAsJsonArray("pivot").get(1).getAsInt());
        assertEquals(2, arm.getAsJsonArray("cubes").size());
        assertFalse(root.has("cubes"), "the hidden marker must not be emitted");
    }

    @Test
    void elementRotationUvRotationAndMaterialNamesSurvive() throws Exception {
        ModelIR imported = fixture("rigged_arm.bbmodel");
        BedrockModelCompiler.Result result = BedrockModelCompiler.compile(imported,
                "geometry.kalo.testpack_rig", Map.of("0", Key.key("testpack", "item/steel")),
                "testpack", "testpack_rig");

        JsonObject rotated = geometry(result).getAsJsonArray("bones").get(1)
                .getAsJsonObject().getAsJsonArray("cubes").get(1).getAsJsonObject();
        assertEquals(0, rotated.getAsJsonArray("rotation").get(0).getAsInt());
        assertEquals(-45, rotated.getAsJsonArray("rotation").get(1).getAsInt());
        assertEquals(0, rotated.getAsJsonArray("rotation").get(2).getAsInt());

        JsonObject north = rotated.getAsJsonObject("uv").getAsJsonObject("north");
        assertEquals(0, north.getAsJsonArray("uv").get(0).getAsInt());
        assertEquals(0, north.getAsJsonArray("uv").get(1).getAsInt());
        assertEquals(4, north.getAsJsonArray("uv_size").get(0).getAsInt());
        assertEquals(6, north.getAsJsonArray("uv_size").get(1).getAsInt());
        assertEquals("t0", north.get("material_instance").getAsString());
    }

    @Test
    void animationsBecomeBedrockAnimationJsonAndLoopWiring() throws Exception {
        ModelIR imported = fixture("rigged_arm.bbmodel");
        BedrockModelCompiler.Result result = BedrockModelCompiler.compile(imported,
                "geometry.kalo.testpack_rig", Map.of("0", Key.key("testpack", "item/steel")),
                "testpack", "testpack_rig");

        assertNotNull(result.animations(), "the authored animation should be emitted");
        JsonObject swing = result.animations().getAsJsonObject("animations")
                .getAsJsonObject("animation.kalo.testpack_rig.swing");
        assertTrue(swing.get("loop").getAsBoolean());
        assertEquals(1.0, swing.get("animation_length").getAsDouble(), 1e-9);
        JsonObject rotation = swing.getAsJsonObject("bones")
                .getAsJsonObject("arm").getAsJsonObject("rotation");
        // At 0.5s the Blockbench keyframe is z=-60; Bedrock keeps Z but flips X/Y.
        assertEquals(-60, rotation.getAsJsonArray("0.5").get(2).getAsInt());
        assertEquals(List.of("swing"), result.loopAnimations());
        assertEquals("animation.kalo.testpack_rig.swing",
                result.animationIds().get("swing"));
    }

    @Test
    void aBezierSegmentIsBakedIntoLinearBedrockKeyframes() throws Exception {
        ModelIR base = fixture("rigged_arm.bbmodel");
        ModelIR.BezierHandles leftHandles = new ModelIR.BezierHandles(
                new ModelIR.Vec3(-0.1, -0.1, -0.1), ModelIR.Vec3.ZERO,
                new ModelIR.Vec3(0.5, 0.5, 0.5), ModelIR.Vec3.ZERO);
        ModelIR.BezierHandles rightHandles = new ModelIR.BezierHandles(
                new ModelIR.Vec3(-0.5, -0.5, -0.5), ModelIR.Vec3.ZERO,
                new ModelIR.Vec3(0.1, 0.1, 0.1), ModelIR.Vec3.ZERO);
        ModelIR.Animation ease = new ModelIR.Animation("ease-uuid", "Ease", "once", 1.0,
                List.of(new ModelIR.Animator("bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb",
                        "arm", "bone", List.of(
                        new ModelIR.Keyframe(0.0, "rotation", "bezier",
                                List.of(ModelIR.Vec3.ZERO), leftHandles),
                        new ModelIR.Keyframe(1.0, "rotation", "bezier",
                                List.of(new ModelIR.Vec3(0, 0, 90)), rightHandles)))));
        ModelIR model = new ModelIR(base.id(), base.name(), base.formatVersion(),
                base.modelFormat(), base.textureWidth(), base.textureHeight(),
                base.textures(), base.bones(), List.of(ease), List.of(),
                base.display(), base.diagnostics());

        BedrockModelCompiler.Result result = BedrockModelCompiler.compileEntity(model,
                "geometry.kalo.testpack_golem_mob",
                Map.of("0", Key.key("testpack", "item/steel")),
                "testpack", "testpack_golem_mob");

        assertTrue(result.compiled(), result.errorSummary());
        JsonObject rotation = result.animations().getAsJsonObject("animations")
                .getAsJsonObject("animation.kalo.testpack_golem_mob.ease")
                .getAsJsonObject("bones").getAsJsonObject("arm")
                .getAsJsonObject("rotation");
        assertTrue(rotation.size() >= 8,
                "Bezier must be baked into intermediate keyframes: " + rotation);
        assertEquals(0, rotation.getAsJsonArray("0.0").get(2).getAsInt());
        assertEquals(90, rotation.getAsJsonArray("1.0").get(2).getAsInt());
        double middle = rotation.getAsJsonArray("0.5").get(2).getAsDouble();
        assertTrue(middle > 40 && middle < 50, "midpoint was " + middle);
        assertTrue(result.diagnostics().stream()
                        .anyMatch(diagnostic -> diagnostic.code()
                                .equals("bedrock.animation-bezier-baked")),
                "the baked curve should be reported");
        assertFalse(result.diagnostics().stream()
                        .anyMatch(diagnostic -> diagnostic.code()
                                .equals("bedrock.animation-bezier")),
                "Bezier is no longer emitted as an unsupported linear fallback");

        Path artifacts = Path.of("build", "reports", "bezier");
        Files.createDirectories(artifacts);
        Files.writeString(artifacts.resolve("bedrock-animation.json"),
                result.animations().toString(), StandardCharsets.UTF_8);
    }

    @Test
    void controllerExpressionsWithArraysRandomAndQueriesArePreserved() throws Exception {
        ModelIR base = fixture("rigged_arm.bbmodel");
        String condition = "query.count([1, 2, 3]) == 3"
                + " && query.distance_from_camera > 10"
                + " && (variable.missing ?? query.is_moving)"
                + " && math.random(0.0, 1.0) < 1.0";
        String probe = "variable.x = [1, 2, 3];"
                + " loop(2, { variable.x[0] = variable.x[0] + 1; });"
                + " return variable.x[0];";
        ModelIR.Controller controller = new ModelIR.Controller(
                "controller.animation.test", "idle", Map.of(
                "idle", new ModelIR.State(
                        List.of(new ModelIR.AnimationRef("swing",
                                "[0.2, 0.8][query.is_moving]")),
                        List.of(new ModelIR.Transition("walk", condition)),
                        null,
                        Map.of("speed", new ModelIR.Variable(
                                        "query.ground_speed", Map.of()),
                                "probe", new ModelIR.Variable(probe, Map.of()))),
                "walk", new ModelIR.State(
                        List.of(new ModelIR.AnimationRef("swing", null)),
                        List.of(), null)));
        ModelIR model = new ModelIR(base.id(), base.name(), base.formatVersion(),
                base.modelFormat(), base.textureWidth(), base.textureHeight(),
                base.textures(), base.bones(), base.animations(), List.of(controller),
                base.display(), base.diagnostics());

        BedrockModelCompiler.Result result = BedrockModelCompiler.compileEntity(model,
                "geometry.kalo.testpack_golem_mob",
                Map.of("0", Key.key("testpack", "item/steel")),
                "testpack", "testpack_golem_mob");

        assertTrue(result.compiled(), result.errorSummary());
        JsonObject states = result.controllers()
                .getAsJsonObject("animation_controllers")
                .getAsJsonObject("controller.animation.test")
                .getAsJsonObject("states");
        JsonObject idle = states.getAsJsonObject("idle");
        assertEquals("[0.2, 0.8][query.is_moving]",
                idle.getAsJsonArray("animations").get(0).getAsJsonObject()
                        .get("swing").getAsString());
        assertEquals(condition,
                idle.getAsJsonArray("transitions").get(0).getAsJsonObject()
                        .get("walk").getAsString());
        assertEquals("query.ground_speed",
                idle.getAsJsonObject("variables").getAsJsonObject("speed")
                        .get("input").getAsString());
        assertEquals(probe,
                idle.getAsJsonObject("variables").getAsJsonObject("probe")
                        .get("input").getAsString());

        Path artifacts = Path.of("build", "reports", "molang");
        Files.createDirectories(artifacts);
        Files.writeString(artifacts.resolve("bedrock-controller.json"),
                result.controllers().toString(), StandardCharsets.UTF_8);
    }

    @Test
    void outputIsDeterministic() throws Exception {
        ModelIR imported = fixture("rigged_arm.bbmodel");
        BedrockModelCompiler.Result first = BedrockModelCompiler.compile(imported,
                "geometry.kalo.testpack_rig", Map.of("0", Key.key("testpack", "item/steel")),
                "testpack", "testpack_rig");
        BedrockModelCompiler.Result second = BedrockModelCompiler.compile(imported,
                "geometry.kalo.testpack_rig", Map.of("0", Key.key("testpack", "item/steel")),
                "testpack", "testpack_rig");
        assertEquals(first.geometry().toString(), second.geometry().toString());
    }

    @Test
    void aOneShotAnimationIsEmittedButNotAutoStarted() throws Exception {
        ModelIR base = fixture("rigged_arm.bbmodel");
        ModelIR.Animation once = new ModelIR.Animation("once-uuid", "Open", "once", 1.0,
                List.of(new ModelIR.Animator("bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb",
                        "arm", "bone", List.of(
                        new ModelIR.Keyframe(0.0, "rotation", "linear",
                                List.of(ModelIR.Vec3.ZERO)),
                        new ModelIR.Keyframe(1.0, "rotation", "linear",
                                List.of(new ModelIR.Vec3(0, 0, 45)))))));
        ModelIR model = new ModelIR(base.id(), base.name(), base.formatVersion(),
                base.modelFormat(), base.textureWidth(), base.textureHeight(),
                base.textures(), base.bones(), List.of(once), List.of(),
                base.display(), base.diagnostics());

        BedrockModelCompiler.Result result = BedrockModelCompiler.compileEntity(model,
                "geometry.kalo.testpack_golem_mob",
                Map.of("0", Key.key("testpack", "item/steel")),
                "testpack", "testpack_golem_mob");

        assertTrue(result.compiled(), result.errorSummary());
        assertTrue(result.animationIds().containsKey("open"));
        assertFalse(result.loopAnimations().contains("open"),
                "a once animation must not be added to the auto-start list");
        assertTrue(result.animations().getAsJsonObject("animations")
                .has("animation.kalo.testpack_golem_mob.open"));
    }

    @Test
    void missingTexturesFailBeforeGeometryIsReturned() throws Exception {
        ModelIR imported = BlockbenchImporter.importJson("""
                {"meta":{"format_version":"4.10","model_format":"java_block"},
                 "elements":[{"uuid":"e","from":[0,0,0],"to":[4,4,4],
                              "faces":{"north":{"uv":[0,0,4,4],"texture":0}}}],
                 "textures":[{"id":"0","name":"t"}]}
                """, Key.key("testpack", "models/item/thing.bbmodel"), "inline");

        BedrockModelCompiler.Result result = BedrockModelCompiler.compile(imported,
                "geometry.kalo.testpack_thing", Map.of(), "testpack", "testpack_thing");
        assertFalse(result.compiled());
        assertTrue(result.errorSummary().contains("textures"));
    }
}
