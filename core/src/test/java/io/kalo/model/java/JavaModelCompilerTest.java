package io.kalo.model.java;

import io.kalo.model.ModelIR;
import io.kalo.model.blockbench.BlockbenchImporter;
import net.kyori.adventure.key.Key;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JavaModelCompilerTest {

    private static ModelIR fixture(String name) throws Exception {
        try (var stream = JavaModelCompilerTest.class.getResourceAsStream("/blockbench/" + name)) {
            assertNotNull(stream);
            return BlockbenchImporter.importJson(
                    new String(stream.readAllBytes(), StandardCharsets.UTF_8),
                    Key.key("testpack", "models/item/thing.bbmodel"), name);
        }
    }

    private static ModelIR model(String json) throws Exception {
        return BlockbenchImporter.importJson(json,
                Key.key("testpack", "models/item/thing.bbmodel"), "inline");
    }

    @Test
    void aSimpleCubeBecomesAJavaModelWithTheAuthoredUvs() throws Exception {
        ModelIR imported = fixture("simple_cube.bbmodel");
        JavaModelCompiler.Result result = JavaModelCompiler.compile(imported,
                Map.of("0", Key.key("testpack", "item/ruby")), "testpack", "testpack_thing");

        assertTrue(result.compiled(), result.errorSummary());
        assertEquals("testpack:item/ruby",
                result.model().getAsJsonObject("textures").get("t0").getAsString());
        assertEquals(1, result.model().getAsJsonArray("elements").size());
        var element = result.model().getAsJsonArray("elements").get(0).getAsJsonObject();
        assertEquals(4, element.getAsJsonArray("from").get(0).getAsInt());
        assertEquals(12, element.getAsJsonArray("to").get(1).getAsInt());
        var north = element.getAsJsonObject("faces").getAsJsonObject("north");
        assertEquals("#t0", north.get("texture").getAsString());
        assertEquals(4, north.getAsJsonArray("uv").get(1).getAsInt());
        assertFalse(result.model().has("texture_size"),
                "16x16 is Java's default and should stay implicit");
        assertEquals(golden("simple_cube.java-model.json"), result.model().toString(),
                "the Java model is a golden, byte-stable artifact");
    }

    private static String golden(String name) throws IOException {
        try (var stream = JavaModelCompilerTest.class.getResourceAsStream("/golden/" + name)) {
            assertNotNull(stream);
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8).trim();
        }
    }

    @Test
    void aPackRelativeTexturePathResolvesWithoutAnOverride() throws Exception {
        ModelIR imported = fixture("simple_cube.bbmodel");
        JavaModelCompiler.Result result = JavaModelCompiler.compile(imported, Map.of(),
                "testpack", "testpack_thing");

        assertTrue(result.compiled(), result.errorSummary());
        assertEquals("testpack:item/ruby", result.textures().getFirst().key().asString());
    }

    @Test
    void hierarchyIsFlattenedButHiddenCubesAndDisplayTransformsAreHandled() throws Exception {
        ModelIR imported = fixture("rigged_arm.bbmodel");
        JavaModelCompiler.Result result = JavaModelCompiler.compile(imported,
                Map.of("0", Key.key("testpack", "item/steel")), "testpack", "testpack_rig");

        assertTrue(result.compiled(), result.errorSummary());
        assertEquals(2, result.model().getAsJsonArray("elements").size(),
                "the hidden marker must not become a Java element");
        assertEquals(45, result.model().getAsJsonArray("elements").get(1)
                .getAsJsonObject().getAsJsonObject("rotation").get("angle").getAsDouble());
        assertTrue(result.model().has("display"));
        assertEquals(2, result.model().getAsJsonObject("display")
                .getAsJsonObject("gui").getAsJsonArray("translation").get(1).getAsInt());
        assertTrue(result.diagnostics().stream().anyMatch(d ->
                d.code().equals("blockbench.animation-runtime")));
        assertTrue(result.diagnostics().stream().anyMatch(d ->
                d.code().equals("java.cube-hidden")));
    }

    @Test
    void aRotatedBoneIsRefusedInsteadOfBakedIncorrectly() throws Exception {
        ModelIR imported = model("""
                {"meta":{"format_version":"5.0","model_format":"java_block"},
                 "groups":[{"uuid":"g","name":"arm","origin":[8,8,8],"rotation":[0,30,0]}],
                 "outliner":[{"uuid":"g","children":["e"]}],
                 "elements":[{"uuid":"e","from":[0,0,0],"to":[4,4,4],
                              "faces":{"north":{"uv":[0,0,4,4],"texture":0}}}],
                 "textures":[{"id":"0","name":"t","relative_path":"textures/item/t.png"}]}
                """);

        JavaModelCompiler.Result result = JavaModelCompiler.compile(imported, Map.of(),
                "testpack", "testpack_thing");
        assertFalse(result.compiled());
        assertNull(result.model());
        assertTrue(result.errorSummary().contains("no bone hierarchy"), result.errorSummary());
    }

    @Test
    void inflatedGeometryIsRefusedBecauseJavaHasNoInflateField() throws Exception {
        ModelIR imported = model("""
                {"meta":{"format_version":"4.10","model_format":"java_block"},
                 "elements":[{"uuid":"e","from":[0,0,0],"to":[4,4,4],"inflate":0.5,
                              "faces":{"north":{"uv":[0,0,4,4],"texture":0}}}],
                 "textures":[{"id":"0","name":"t","relative_path":"textures/item/t.png"}]}
                """);

        JavaModelCompiler.Result result = JavaModelCompiler.compile(imported, Map.of(),
                "testpack", "testpack_thing");
        assertFalse(result.compiled());
        assertTrue(result.errorSummary().contains("no inflation field"));
    }

    @Test
    void multiAxisElementRotationIsRefused() throws Exception {
        ModelIR imported = model("""
                {"meta":{"format_version":"4.10","model_format":"java_block"},
                 "elements":[{"uuid":"e","from":[0,0,0],"to":[4,4,4],"rotation":[10,20,0],
                              "faces":{"north":{"uv":[0,0,4,4],"texture":0}}}],
                 "textures":[{"id":"0","name":"t","relative_path":"textures/item/t.png"}]}
                """);

        JavaModelCompiler.Result result = JavaModelCompiler.compile(imported, Map.of(),
                "testpack", "testpack_thing");
        assertFalse(result.compiled());
        assertTrue(result.errorSummary().contains("single axis"));
    }

    @Test
    void aTextureThatCannotResolveFailsBeforeAnyModelIsEmitted() throws Exception {
        ModelIR imported = model("""
                {"meta":{"format_version":"4.10","model_format":"java_block"},
                 "elements":[{"uuid":"e","from":[0,0,0],"to":[4,4,4],
                              "faces":{"north":{"uv":[0,0,4,4],"texture":0}}}],
                 "textures":[{"id":"0","name":"t"}]}
                """);

        JavaModelCompiler.Result result = JavaModelCompiler.compile(imported, Map.of(),
                "testpack", "testpack_thing");
        assertFalse(result.compiled());
        assertTrue(result.errorSummary().contains("textures"));
    }
}
