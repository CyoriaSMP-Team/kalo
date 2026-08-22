package io.kalo.content.furniture;

import io.kalo.content.block.definition.BlockDefinition;
import io.kalo.content.block.definition.BlockModelDefinition;
import io.kalo.content.furniture.definition.FurnitureBehaviour;
import io.kalo.content.furniture.definition.FurnitureDefinition;
import io.kalo.content.furniture.definition.FurnitureDisplay;
import io.kalo.content.item.definition.BedrockOptions;
import io.kalo.content.item.definition.DisplayProperties;
import io.kalo.content.block.definition.JavaBlockOptions;
import net.kyori.adventure.key.Key;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link FurnitureDefinition} record construction and conversion.
 */
class FurnitureDefinitionTest {

    @Test
    void builderRequiresModel() {
        FurnitureDefinition.Builder builder = FurnitureDefinition.builder(Key.key("testpack", "chair"));
        assertThrows(IllegalStateException.class, builder::build,
                "Building without a model should fail");
    }

    @Test
    void builderProducesCorrectDefinition() {
        BlockModelDefinition.CubeAll model = new BlockModelDefinition.CubeAll(Key.key("testpack", "block/chair"));

        FurnitureDefinition definition = FurnitureDefinition.builder(Key.key("testpack", "chair"))
                .display(new DisplayProperties(null, List.of(), false))
                .model(model)
                .behaviour(FurnitureBehaviour.defaults())
                .java(JavaBlockOptions.defaults())
                .bedrock(BedrockOptions.defaults())
                .displayTransform(FurnitureDisplay.defaults())
                .build();

        assertEquals(Key.key("testpack", "chair"), definition.key());
        assertNotNull(definition.display());
        assertNotNull(definition.model());
        assertNotNull(definition.behaviour());
        assertNotNull(definition.java());
        assertNotNull(definition.bedrock());
        assertNotNull(definition.displayTransform());
    }

    @Test
    void toBlockDefinitionPreservesKeyAndModel() {
        BlockModelDefinition.CubeAll model = new BlockModelDefinition.CubeAll(Key.key("testpack", "block/chair"));

        FurnitureDefinition furnitureDef = FurnitureDefinition.builder(Key.key("testpack", "chair"))
                .model(model)
                .behaviour(new FurnitureBehaviour(3.0f, false, true, "strict", null,
                        List.of(new double[]{0, 0, 0}), null, null, false, 15, null))
                .java(JavaBlockOptions.defaults())
                .bedrock(BedrockOptions.defaults())
                .build();

        BlockDefinition blockDef = furnitureDef.toBlockDefinition();

        assertEquals(furnitureDef.key(), blockDef.key());
        assertEquals(furnitureDef.model(), blockDef.model());
        assertEquals(3.0f, blockDef.behaviour().hardness());
        assertFalse(blockDef.behaviour().requiresTool());
    }

    @Test
    void displayTransformDefaultsAreReasonable() {
        FurnitureDisplay display = FurnitureDisplay.defaults();
        assertNull(display.displayTransform());
        assertNull(display.trackingRotation());
        assertNull(display.translation());
        assertNull(display.scale());
        assertNull(display.brightness());
        assertNull(display.shadowRadius());
        assertNull(display.shadowStrength());
        assertNull(display.viewRange());
        assertNull(display.displayWidth());
        assertNull(display.displayHeight());
    }

    @Test
    void displayBrightnessRejectsOutOfRange() {
        assertThrows(IllegalArgumentException.class, () ->
                new FurnitureDisplay.Brightness(-1, 0));
        assertThrows(IllegalArgumentException.class, () ->
                new FurnitureDisplay.Brightness(0, 16));
    }
}
