package io.kalo.model.blockbench;

import io.kalo.model.ModelIR;
import io.kalo.model.ModelImportException;
import net.kyori.adventure.key.Key;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BlockbenchImporterTest {

    private static final Key ID = Key.key("testpack", "models/item/thing.bbmodel");

    private static String fixture(String name) throws IOException {
        try (var stream = BlockbenchImporterTest.class.getResourceAsStream("/blockbench/" + name)) {
            assertNotNull(stream, "missing fixture " + name);
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static ModelIR importFixture(String name) throws Exception {
        return BlockbenchImporter.importJson(fixture(name), ID, name);
    }

    @Test
    void aCubeKeepsItsTextureUvsAndBounds() throws Exception {
        ModelIR model = importFixture("simple_cube.bbmodel");

        assertEquals("simple_cube", model.name());
        assertEquals(16, model.textureWidth());
        assertEquals(16, model.textureHeight());
        assertEquals(1, model.textures().size());
        assertEquals("0", model.textures().getFirst().id());
        assertEquals("ruby", model.textures().getFirst().name());
        assertEquals("textures/item/ruby.png", model.textures().getFirst().relativePath());

        assertEquals(1, model.bones().size());
        ModelIR.Bone root = model.bones().getFirst();
        assertEquals("root", root.name());
        assertEquals(1, root.cubes().size());
        ModelIR.Cube cube = root.cubes().getFirst();
        assertEquals(new ModelIR.Vec3(4, 0, 4), cube.from());
        assertEquals(new ModelIR.Vec3(12, 12, 12), cube.to());
        assertEquals(6, cube.faces().size());
        assertEquals(4, cube.faces().get("north").u1());
        assertEquals(12, cube.faces().get("north").u2());
        assertEquals("0", cube.faces().get("north").textureId());
        assertTrue(root.visible());
        assertTrue(root.export());
    }

    @Test
    void theOutlinerHierarchyIsPreservedRatherThanFlattened() throws Exception {
        ModelIR model = importFixture("rigged_arm.bbmodel");

        assertEquals(2, model.bones().size());
        ModelIR.Bone root = model.bones().getFirst();
        ModelIR.Bone arm = model.bones().get(1);
        assertEquals("root", root.name());
        assertEquals("arm", arm.name());
        assertEquals(root.id(), arm.parentId());
        assertTrue(root.children().contains(arm.id()));
        assertEquals(new ModelIR.Vec3(8, 10, 8), arm.pivot());

        // Two visible cubes live on the arm; the hidden marker stays attached to root.
        assertEquals(2, arm.cubes().size());
        assertEquals(1, root.cubes().size());
        assertEquals("hidden_marker", root.cubes().getFirst().name());
        assertFalse(root.cubes().getFirst().visible());
    }

    @Test
    void rotationsAndPerFaceUvsSurviveImport() throws Exception {
        ModelIR model = importFixture("rigged_arm.bbmodel");
        ModelIR.Cube rotated = model.bones().get(1).cubes().get(1);

        assertEquals(new ModelIR.Vec3(0, 45, 0), rotated.rotation());
        assertEquals(new ModelIR.Vec3(8, 10, 8), rotated.pivot());
        assertEquals(0, rotated.faces().get("north").u1());
        assertEquals(6, rotated.faces().get("north").v2());
    }

    @Test
    void animationKeyframesAndDisplayTransformsAreRetained() throws Exception {
        ModelIR model = importFixture("rigged_arm.bbmodel");

        assertEquals(2, model.animations().size());
        ModelIR.Animation animation = model.animations().getFirst();
        assertEquals("swing", animation.name());
        assertEquals("loop", animation.loop());
        assertEquals(1.0, animation.length(), 1e-9);
        assertEquals(1, animation.animators().size());
        assertEquals(3, animation.animators().getFirst().keyframes().size());
        assertEquals(-60, animation.animators().getFirst().keyframes().get(1)
                .dataPoints().getFirst().z(), 1e-9);

        ModelIR.Animation sway = model.animations().get(1);
        assertEquals("sway", sway.name());
        assertEquals(4, sway.animators().getFirst().keyframes().size());
        assertEquals("catmullrom",
                sway.animators().getFirst().keyframes().getFirst().interpolation());

        assertEquals(0.5, model.display().get("gui").scale().x(), 1e-9);

        assertTrue(model.diagnostics().stream().anyMatch(diagnostic ->
                diagnostic.code().equals("blockbench.animation-runtime")
                        && diagnostic.severity() == ModelIR.Severity.INFO));
    }

    @Test
    void bezierKeyframeHandlesAreRetained() throws Exception {
        com.google.gson.JsonObject root = com.google.gson.JsonParser
                .parseString(fixture("rigged_arm.bbmodel")).getAsJsonObject();
        com.google.gson.JsonObject keyframe = root.getAsJsonArray("animations").get(0)
                .getAsJsonObject().getAsJsonObject("animators")
                .entrySet().iterator().next().getValue().getAsJsonObject()
                .getAsJsonArray("keyframes").get(0).getAsJsonObject();
        keyframe.addProperty("interpolation", "bezier");
        com.google.gson.JsonArray rightTime = new com.google.gson.JsonArray();
        rightTime.add(0.4);
        rightTime.add(0.4);
        rightTime.add(0.4);
        keyframe.add("bezier_right_time", rightTime);
        com.google.gson.JsonArray rightValue = new com.google.gson.JsonArray();
        rightValue.add(0.0);
        rightValue.add(0.0);
        rightValue.add(0.0);
        keyframe.add("bezier_right_value", rightValue);

        ModelIR model = BlockbenchImporter.importJson(root.toString(), ID, "bezier.bbmodel");
        ModelIR.Keyframe parsed = model.animations().getFirst().animators().getFirst()
                .keyframes().getFirst();

        assertEquals("bezier", parsed.interpolation());
        assertEquals(0.4, parsed.bezier().rightTime().x(), 1e-9);
        assertEquals(0.4, parsed.bezier().rightTime().y(), 1e-9);
        assertEquals(0.4, parsed.bezier().rightTime().z(), 1e-9);
        assertEquals(0.0, parsed.bezier().rightValue().x(), 1e-9);
        assertEquals(-0.1, parsed.bezier().leftTime().x(), 1e-9,
                "Blockbench's default incoming handle is retained");

        io.kalo.model.animation.ModelAnimations.Clip clip =
                io.kalo.model.animation.ModelAnimations.compile(
                        model.animations().getFirst());
        assertNotNull(clip);
        assertTrue(clip.sample(0.25).bone("arm").rotation().z() > -30.0,
                "the imported handles must drive the runtime sampler");
    }

    @Test
    void animationControllersAreImportedWithStatesAndTransitions() throws Exception {
        byte[] bytes;
        try (var stream = BlockbenchImporterTest.class.getResourceAsStream(
                "/blockbench/rigged_arm_controller.bbmodel")) {
            assertNotNull(stream);
            bytes = stream.readAllBytes();
        }

        var model = BlockbenchImporter.importBytes(bytes,
                Key.key("testpack", "controlled"), "controller fixture");

        assertEquals(1, model.controllers().size());
        var controller = model.controllers().getFirst();
        assertEquals("controller.animation.test", controller.name());
        assertEquals("default", controller.initialState());
        assertEquals(2, controller.states().size());
        var defaultState = controller.states().get("default");
        assertEquals("swing", defaultState.animations().getFirst().animation());
        assertEquals("open", defaultState.transitions().getFirst().target());
        assertEquals("query.is_moving",
                defaultState.transitions().getFirst().condition());
        assertEquals(0.2, controller.states().get("open").blendTransition(), 1e-9);
        assertEquals("variable.speed_curve",
                controller.states().get("open").animations().getFirst().blendExpression());
        assertEquals("query.modified_move_speed",
                controller.states().get("open").variables().get("speed_curve").input());
        assertEquals(0.7, controller.states().get("open").variables().get("speed_curve")
                .remapCurve().get(1.0), 1e-9);
    }

    @Test
    void malformedJsonIsRejected() {
        assertThrows(ModelImportException.class, () ->
                BlockbenchImporter.importJson("{not json", ID, "broken"));
    }

    @Test
    void duplicateElementIdsAreRejected() {
        String json = """
                {"meta":{"format_version":"4.10","model_format":"java_block"},
                 "elements":[
                   {"uuid":"same","from":[0,0,0],"to":[1,1,1],"faces":{}},
                   {"uuid":"same","from":[0,0,0],"to":[1,1,1],"faces":{}}]}
                """;
        assertThrows(ModelImportException.class, () ->
                BlockbenchImporter.importJson(json, ID, "duplicate"));
    }

    @Test
    void invalidDimensionsAreRejected() {
        String json = """
                {"meta":{"format_version":"4.10","model_format":"java_block"},
                 "elements":[{"uuid":"a","from":[4,4,4],"to":[1,1,1],"faces":{}}]}
                """;
        assertThrows(ModelImportException.class, () ->
                BlockbenchImporter.importJson(json, ID, "inverted"));
    }

    @Test
    void unsafeTexturePathsAreRejected() {
        String json = """
                {"meta":{"format_version":"4.10","model_format":"java_block"},
                 "elements":[],
                 "textures":[{"id":"0","name":"evil","relative_path":"../secret.png"}]}
                """;
        assertThrows(ModelImportException.class, () ->
                BlockbenchImporter.importJson(json, ID, "traversal"));
    }

    @Test
    void boxUvModelsAreRejectedInsteadOfProducingWrongUvs() {
        String json = """
                {"meta":{"format_version":"4.10","model_format":"java_block","box_uv":true},
                 "elements":[]}
                """;
        assertThrows(ModelImportException.class, () ->
                BlockbenchImporter.importJson(json, ID, "box-uv"));
    }

    @Test
    void aFileOutsideThePackRootIsRejected(@TempDir Path temp) throws IOException {
        Path pack = Files.createDirectory(temp.resolve("pack"));
        Path outside = Files.writeString(temp.resolve("outside.bbmodel"), "{}");
        assertThrows(ModelImportException.class, () ->
                BlockbenchImporter.importFile(pack, outside, ID));
    }

    @Test
    void oversizedInputIsRejectedBeforeParsing() {
        byte[] bytes = new byte[BlockbenchImporter.MAX_FILE_BYTES + 1];
        assertThrows(ModelImportException.class, () ->
                BlockbenchImporter.importBytes(bytes, ID, "huge"));
    }

    @Test
    void embeddedPngTexturesAreDecodedAndMeasuredWithoutReadingHostPaths() throws Exception {
        String base64 = "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJ"
                + "AAAADUlEQVR42mP8z8BQDwAFgwJ/lK3V9wAAAABJRU5ErkJggg==";
        String json = """
                {"meta":{"format_version":"4.10","model_format":"java_block"},
                 "resolution":{"width":16,"height":16},
                 "elements":[],
                 "textures":[{"id":"0","name":"embedded","internal":true,
                              "source":"data:image/png;base64,%s"}]}
                """.formatted(base64);
        ModelIR model = BlockbenchImporter.importJson(json, ID, "embedded");
        ModelIR.Texture texture = model.textures().getFirst();
        assertNotNull(texture.embeddedPng());
        assertEquals(1, texture.width());
        assertEquals(1, texture.height());
        assertEquals(16, texture.uvWidth());
    }

    @Test
    void faceTextureReferencesCanUseTheTextureUuid() {
        String json = """
                {"meta":{"format_version":"5.0","model_format":"java_block"},
                 "elements":[{"uuid":"e","from":[0,0,0],"to":[4,4,4],
                              "faces":{"north":{"uv":[0,0,4,4],"texture":"tex-uuid"}}}],
                 "textures":[{"uuid":"tex-uuid","id":"0","name":"ruby",
                              "relative_path":"textures/item/ruby.png"}]}
                """;
        ModelIR model = org.junit.jupiter.api.Assertions.assertDoesNotThrow(() ->
                BlockbenchImporter.importJson(json, ID, "uuid-face"));
        assertEquals("0", model.bones().getFirst().cubes().getFirst()
                .faces().get("north").textureId());
        assertEquals("tex-uuid", model.textures().getFirst().uuid());
    }

    @Test
    void aGroupCycleIsRejected() {
        String json = """
                {"meta":{"format_version":"5.0","model_format":"java_block"},
                 "groups":[{"uuid":"a","name":"a","origin":[0,0,0]}],
                 "outliner":[{"uuid":"a","children":["a"]}],
                 "elements":[]}
                """;
        assertThrows(ModelImportException.class, () ->
                BlockbenchImporter.importJson(json, ID, "cycle"));
    }
}
