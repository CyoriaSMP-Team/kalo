package io.kalo.platform.bedrock;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class BedrockItemDisplayTest {

    private static final double[] CENTER = BedrockItemDisplay.REFERENCE_CENTER;

    @Test
    void lowerModelIsRaisedToTheReferenceCentre() {
        // A 16-tall model starting at y=0 centres at 8, 20 below the Wiki's head.
        JsonObject out = BedrockItemDisplay.animations("ns_a", new JsonObject(), "kalo", new double[]{0, 8, 0});
        assertEquals(34.5, pose(out, "ns_a", true).getAsJsonArray("position").get(1).getAsDouble());
    }

    private static JsonObject pose(JsonObject out, String name, boolean first) {
        return out.getAsJsonObject("animations")
                .getAsJsonObject(BedrockItemDisplay.animationId(name, first))
                .getAsJsonObject("bones").getAsJsonObject("kalo");
    }

    @Test
    void modelWithoutDisplayUsesWikiBaseline() {
        JsonObject out = BedrockItemDisplay.animations("ns_a", new JsonObject(), "kalo", CENTER);
        JsonObject first = pose(out, "ns_a", true);
        assertEquals(14.5, first.getAsJsonArray("position").get(1).getAsDouble());
        assertFalse(first.has("scale"));
        assertEquals(0.65, pose(out, "ns_a", false).get("scale").getAsDouble());
    }

    @Test
    void authoredDisplayIsConverted() {
        JsonObject model = JsonParser.parseString("""
                {"display":{"firstperson_righthand":
                  {"rotation":[10,20,30],"translation":[1,2,3],"scale":[2,2,2]}}}
                """).getAsJsonObject();
        JsonObject first = pose(BedrockItemDisplay.animations("ns_a", model, "kalo", CENTER), "ns_a", true);
        assertEquals(-20, first.getAsJsonArray("rotation").get(1).getAsDouble());
        assertEquals(-30, first.getAsJsonArray("rotation").get(2).getAsDouble());
        assertEquals(-1, first.getAsJsonArray("position").get(0).getAsDouble());
        assertEquals(16.5, first.getAsJsonArray("position").get(1).getAsDouble());
        assertEquals(2.0, first.get("scale").getAsDouble());
    }
}
