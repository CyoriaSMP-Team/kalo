package io.kalo.platform.bedrock;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.kalo.content.item.definition.BedrockOptions;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class BedrockItemDisplayTest {

    private static final double[] CENTER = {0, 8, 0};

    private static JsonObject pose(JsonObject out, boolean first) {
        return out.getAsJsonObject("animations")
                .getAsJsonObject(BedrockItemDisplay.animationId("ns_a", first))
                .getAsJsonObject("bones").getAsJsonObject("kalo");
    }

    private static double at(JsonObject pose, String key, int index) {
        return pose.getAsJsonArray(key).get(index).getAsDouble();
    }

    @Test
    void modelWithoutDisplayIsCentredOnTheHandAnchorWithNeutralRotation() {
        JsonObject first = pose(BedrockItemDisplay.animations("ns_a", new JsonObject(), "kalo", CENTER, null), true);
        assertEquals(16, at(first, "position", 1)); // 24 - 8
        assertEquals(0, at(first, "rotation", 0));
        assertFalse(first.has("scale"));
    }

    @Test
    void authoredDisplayIsConverted() {
        JsonObject model = JsonParser.parseString("""
                {"display":{"firstperson_righthand":
                  {"rotation":[10,20,30],"translation":[1,2,3],"scale":[2,2,2]}}}
                """).getAsJsonObject();
        JsonObject first = pose(BedrockItemDisplay.animations("ns_a", model, "kalo", CENTER, null), true);
        assertEquals(10, at(first, "rotation", 0));
        assertEquals(-20, at(first, "rotation", 1));
        assertEquals(-30, at(first, "rotation", 2));
        assertEquals(-1, at(first, "position", 0));
        assertEquals(18, at(first, "position", 1));
        assertEquals(2.0, first.get("scale").getAsDouble());
        // The other view was not authored, so it stays neutral.
        assertFalse(pose(BedrockItemDisplay.animations("ns_a", model, "kalo", CENTER, null), false).has("scale"));
    }

    @Test
    void yamlOverrideIsAddedPerView() {
        BedrockOptions.HoldPose override = new BedrockOptions.HoldPose(null,
                new BedrockOptions.View(List.of(1.0, 2.0, 3.0), List.of(0.0, 90.0, 0.0), 0.5));
        JsonObject out = BedrockItemDisplay.animations("ns_a", new JsonObject(), "kalo", CENTER, override);
        JsonObject third = pose(out, false);
        assertEquals(1, at(third, "position", 0));
        assertEquals(18, at(third, "position", 1));
        assertEquals(90, at(third, "rotation", 1));
        assertEquals(0.5, third.get("scale").getAsDouble());
        assertEquals(16, at(pose(out, true), "position", 1));
    }
}
