package io.kalo.platform.bedrock;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The coordinate maths is the part that fails invisibly — a mirrored model still looks
 * like a model — so it is pinned here rather than left to a visual check.
 */
class BedrockGeometryTest {

    private static JsonObject model(String json) {
        return JsonParser.parseString(json).getAsJsonObject();
    }

    private static JsonObject firstCube(JsonObject geometry) {
        return geometry.getAsJsonArray("minecraft:geometry").get(0).getAsJsonObject()
                .getAsJsonArray("bones").get(0).getAsJsonObject()
                .getAsJsonArray("cubes").get(0).getAsJsonObject();
    }

    private static double[] doubles(JsonArray array) {
        double[] values = new double[array.size()];
        for (int i = 0; i < array.size(); i++) {
            values[i] = array.get(i).getAsDouble();
        }
        return values;
    }

    @Test
    void aFullBlockLandsAtTheBedrockOrigin() {
        JsonObject geometry = BedrockGeometry.convert("geometry.kalo.x", model("""
                {"elements":[{"from":[0,0,0],"to":[16,16,16],"faces":{}}]}
                """));

        assertNotNull(geometry);
        JsonObject cube = firstCube(geometry);

        // Bedrock centres X and Z on the block and measures Y from the floor.
        assertArrayEquals(new double[]{-8, 0, -8}, doubles(cube.getAsJsonArray("origin")));
        assertArrayEquals(new double[]{16, 16, 16}, doubles(cube.getAsJsonArray("size")));
    }

    @Test
    void xIsMirroredSoTheOriginComesFromTheFarCorner() {
        // The failure this guards: using from.x - 8 gives 2 here instead of -4, which is
        // only visibly wrong once the model is asymmetric.
        JsonObject geometry = BedrockGeometry.convert("geometry.kalo.x", model("""
                {"elements":[{"from":[10,0,3],"to":[12,4,5],"faces":{}}]}
                """));

        JsonObject cube = firstCube(geometry);
        assertArrayEquals(new double[]{-4, 0, -5}, doubles(cube.getAsJsonArray("origin")));
        assertArrayEquals(new double[]{2, 4, 2}, doubles(cube.getAsJsonArray("size")));
    }

    @Test
    void eastAndWestSwapWithTheMirroredAxis() {
        assertEquals("west", BedrockGeometry.mirrorFace("east"));
        assertEquals("east", BedrockGeometry.mirrorFace("west"));
        assertEquals("north", BedrockGeometry.mirrorFace("north"));
        assertEquals("up", BedrockGeometry.mirrorFace("up"));
    }

    @Test
    void uvCornersBecomeACornerPlusASize() {
        JsonObject geometry = BedrockGeometry.convert("geometry.kalo.x", model("""
                {"elements":[{"from":[0,0,0],"to":[16,16,16],
                  "faces":{"north":{"uv":[2,3,10,11],"texture":"#0"}}}]}
                """));

        JsonObject north = firstCube(geometry).getAsJsonObject("uv").getAsJsonObject("north");
        assertArrayEquals(new double[]{2, 3}, doubles(north.getAsJsonArray("uv")));
        assertArrayEquals(new double[]{8, 8}, doubles(north.getAsJsonArray("uv_size")));
    }

    @Test
    void flippedUvsKeepAPositiveSize() {
        // Java allows the corners in either order to flip a texture.
        JsonObject geometry = BedrockGeometry.convert("geometry.kalo.x", model("""
                {"elements":[{"from":[0,0,0],"to":[16,16,16],
                  "faces":{"up":{"uv":[10,11,2,3]}}}]}
                """));

        JsonObject up = firstCube(geometry).getAsJsonObject("uv").getAsJsonObject("up");
        assertArrayEquals(new double[]{2, 3}, doubles(up.getAsJsonArray("uv")));
        assertArrayEquals(new double[]{8, 8}, doubles(up.getAsJsonArray("uv_size")));
    }

    @Test
    void aFaceWithNoUvIsLeftOutRatherThanGuessed() {
        // Java infers these from the element's position; Bedrock has no equivalent, and a
        // wrong UV is harder to notice than a missing face.
        JsonObject geometry = BedrockGeometry.convert("geometry.kalo.x", model("""
                {"elements":[{"from":[0,0,0],"to":[16,16,16],"faces":{"north":{"texture":"#0"}}}]}
                """));

        assertFalse(firstCube(geometry).has("uv"));
    }

    @Test
    void elementRotationCarriesOverWithTheMirroredSign() {
        JsonObject geometry = BedrockGeometry.convert("geometry.kalo.x", model("""
                {"elements":[{"from":[0,0,0],"to":[16,16,16],
                  "rotation":{"origin":[8,8,8],"axis":"y","angle":22.5},"faces":{}}]}
                """));

        JsonObject cube = firstCube(geometry);
        assertArrayEquals(new double[]{0, 8, 0}, doubles(cube.getAsJsonArray("pivot")));
        assertArrayEquals(new double[]{0, -22.5, 0}, doubles(cube.getAsJsonArray("rotation")));
    }

    @Test
    void aParentOnlyModelHasNoShapeToConvert() {
        // Very common: a model that just points at block/cube_all and sets textures.
        assertNull(BedrockGeometry.convert("geometry.kalo.x", model("""
                {"parent":"minecraft:block/cube_all","textures":{"all":"testpack:block/x"}}
                """)));
    }

    @Test
    void textureSizeIsHonouredWhenBlockbenchWritesIt() {
        JsonObject geometry = BedrockGeometry.convert("geometry.kalo.x", model("""
                {"texture_size":[32,64],"elements":[{"from":[0,0,0],"to":[16,16,16],"faces":{}}]}
                """));

        JsonObject description = geometry.getAsJsonArray("minecraft:geometry").get(0)
                .getAsJsonObject().getAsJsonObject("description");
        assertEquals(32, description.get("texture_width").getAsInt());
        assertEquals(64, description.get("texture_height").getAsInt());
    }

    @Test
    void geometryIdentifiersAreNamespacedPerPack() {
        // Two packs may both define "chair"; the identifier has to keep them apart.
        assertEquals("geometry.kalo.testpack_chair", BedrockGeometry.identifierFor("testpack", "chair"));
    }

    @Test
    void wholeNumbersSerializeWithoutADecimalPoint() {
        // Bedrock's own geometry files look like this, and a diff against them should not
        // be all noise.
        JsonObject geometry = BedrockGeometry.convert("geometry.kalo.x", model("""
                {"elements":[{"from":[0,0,0],"to":[16,16,16],"faces":{}}]}
                """));

        assertTrue(geometry.toString().contains("[-8,0,-8]"), geometry.toString());
    }

    private static void assertArrayEquals(double[] expected, double[] actual) {
        org.junit.jupiter.api.Assertions.assertArrayEquals(expected, actual, 1e-9);
    }
}
