package io.kalo.model.bedrock;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Java's display scale is relative to the renderer's half-size item transform. Bedrock
 * attachables have no such base, so a model authored at 16 units used to arrive a full
 * block wide in the hand.
 */
class BedrockHoldAnimationsTest {

    @Test
    void displayScaleIsHalvedForTheHand() {
        JsonObject model = JsonParser.parseString("""
                {"display":{
                  "firstperson_righthand":{"scale":[0.7,0.7,0.7]},
                  "thirdperson_righthand":{"scale":[0.7,0.7,0.7]}}}
                """).getAsJsonObject();

        BedrockHoldAnimations.Result result = BedrockHoldAnimations.build("kalo",
                BedrockHoldAnimations.parse(model, "firstperson_righthand"),
                BedrockHoldAnimations.parse(model, "thirdperson_righthand"),
                "test");

        JsonObject first = result.json().getAsJsonObject("animations")
                .getAsJsonObject(result.ids().get("hold_first_person"))
                .getAsJsonObject("bones").getAsJsonObject("kalo");
        assertEquals(0.35, first.getAsJsonArray("scale").get(0).getAsDouble(), 1e-9);
    }

    @Test
    void perItemScaleAndRotationOverrideTheDisplayTransform() {
        JsonObject model = JsonParser.parseString("""
                {"display":{
                  "firstperson_righthand":{"rotation":[10,20,30],"scale":[0.7,0.7,0.7]},
                  "thirdperson_righthand":{"rotation":[10,20,30],"scale":[0.7,0.7,0.7]}}}
                """).getAsJsonObject();

        BedrockHoldAnimations.Result result = BedrockHoldAnimations.build("kalo",
                BedrockHoldAnimations.parse(model, "firstperson_righthand"),
                BedrockHoldAnimations.parse(model, "thirdperson_righthand"),
                "test", 0.8, java.util.List.of(0.0, 90.0, 0.0));

        JsonObject bone = result.json().getAsJsonObject("animations")
                .getAsJsonObject(result.ids().get("hold_first_person"))
                .getAsJsonObject("bones").getAsJsonObject("kalo");
        // display 0.7 x override base 0.8
        assertEquals(0.56, bone.getAsJsonArray("scale").get(0).getAsDouble(), 1e-9);
        assertEquals(0, bone.getAsJsonArray("rotation").get(0).getAsInt());
        assertEquals(90, bone.getAsJsonArray("rotation").get(1).getAsInt());
    }

    @Test
    void missingDisplayUsesTheHalfScaleFallback() {
        JsonObject model = JsonParser.parseString("{}").getAsJsonObject();

        BedrockHoldAnimations.Result result = BedrockHoldAnimations.build("kalo",
                BedrockHoldAnimations.parse(model, "firstperson_righthand"),
                BedrockHoldAnimations.parse(model, "thirdperson_righthand"),
                "test");

        JsonObject third = result.json().getAsJsonObject("animations")
                .getAsJsonObject(result.ids().get("hold_third_person"))
                .getAsJsonObject("bones").getAsJsonObject("kalo");
        assertEquals(0.5, third.getAsJsonArray("scale").get(0).getAsDouble(), 1e-9);
    }
}
