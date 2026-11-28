package io.kalo.content.vfx.definition;

import com.google.gson.JsonParser;
import net.kyori.adventure.key.Key;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VfxLayerTypesTest {

    @Test
    void cameraAndModelLayersParseWithoutBeingDropped() {
        VfxRecipe recipe = VfxRecipe.parse(JsonParser.parseString("""
                {
                  "duration": 40,
                  "layers": [
                    { "type": "motes", "kind": "glow", "burst": 2, "color": "red" },
                    { "type": "camera", "kind": "spark", "start": 5, "end": 20,
                      "burst": 3, "at": [1, 2, 0], "distance": 2.5, "shake": 0.2,
                      "fov": 55, "yaw": 15, "pitch": -10,
                      "shape": "ring", "radius": 0.5, "speed": 0, "size": 0.2,
                      "lifetime": 10, "color": 0x00FF00 },
                    { "type": "model", "model": "testpack:ruby", "start": 0, "end": 10,
                      "at": [0, 1, 2], "scale": 2, "yaw": 30, "pitch": 10,
                      "roll": 5, "spin": 15 }
                  ]
                }
                """).getAsJsonObject());

        assertEquals(1, recipe.layers().size());
        assertEquals(1, recipe.cameras().size());
        assertEquals(1, recipe.models().size());
        assertTrue(recipe.diagnostics().isEmpty(), recipe.diagnostics().toString());

        VfxRecipe.Camera camera = recipe.cameras().get(0);
        assertEquals("spark", camera.layer().kind());
        assertEquals(5, camera.layer().start());
        assertEquals(20, camera.layer().end());
        assertEquals(3, camera.layer().burst());
        assertEquals(VfxRecipe.Shape.RING, camera.layer().shape());
        assertEquals(2.5, camera.distance(), 1e-9);
        assertEquals(0.2, camera.shake(), 1e-9);
        assertEquals(55, camera.fov(), 1e-9);
        assertEquals(15, camera.yaw(), 1e-9);
        assertEquals(-10, camera.pitch(), 1e-9);
        assertEquals(0x00FF00, camera.layer().color());

        VfxRecipe.Model model = recipe.models().get(0);
        assertEquals(Key.key("testpack:ruby"), model.model());
        assertEquals(0, model.start());
        assertEquals(10, model.end());
        assertEquals(1, model.at().y(), 1e-9);
        assertEquals(2, model.scale(), 1e-9);
        assertEquals(30, model.yaw(), 1e-9);
        assertEquals(10, model.pitch(), 1e-9);
        assertEquals(5, model.roll(), 1e-9);
        assertEquals(15, model.spin(), 1e-9);
    }

    @Test
    void unknownLayerTypesAndBadModelsAreDiagnosedAndSkipped() {
        VfxRecipe recipe = VfxRecipe.parse(JsonParser.parseString("""
                {
                  "duration": 20,
                  "layers": [
                    { "type": "lightning", "burst": 4 },
                    { "type": "model", "model": "Not A Key!", "burst": 1 },
                    { "type": "camera", "burst": 1, "distance": 0, "shake": 9,
                      "fov": 999, "yaw": 999, "pitch": -999 }
                  ]
                }
                """).getAsJsonObject());

        assertEquals(0, recipe.layers().size());
        assertEquals(1, recipe.cameras().size());
        assertEquals(0, recipe.models().size());
        assertTrue(recipe.diagnostics().stream()
                .anyMatch(line -> line.contains("type 'lightning'")));
        assertTrue(recipe.diagnostics().stream()
                .anyMatch(line -> line.contains("is not a valid key")));
        assertEquals(0.25, recipe.cameras().get(0).distance(), 1e-9,
                "camera distance is clamped up to the clearance floor");
        assertEquals(1.0, recipe.cameras().get(0).shake(), 1e-9,
                "camera shake is clamped to one block");
        assertEquals(110, recipe.cameras().get(0).fov(), 1e-9);
        assertEquals(180, recipe.cameras().get(0).yaw(), 1e-9);
        assertEquals(-90, recipe.cameras().get(0).pitch(), 1e-9);
    }

    @Test
    void cameraAndModelOnlyRecipesArePlayable() {
        VfxRecipe cameras = VfxRecipe.parse(JsonParser.parseString("""
                { "duration": 10, "layers": [
                  { "type": "camera", "burst": 2, "distance": 1.5 } ] }
                """).getAsJsonObject());
        assertEquals(0, cameras.layers().size());
        assertEquals(1, cameras.cameras().size());

        VfxRecipe models = VfxRecipe.parse(JsonParser.parseString("""
                { "duration": 10, "layers": [
                  { "type": "model", "model": "minecraft:stone" } ] }
                """).getAsJsonObject());
        assertEquals(0, models.layers().size());
        assertEquals(1, models.models().size());

        assertThrows(IllegalArgumentException.class, () -> VfxRecipe.parse(
                JsonParser.parseString("""
                        { "duration": 10, "layers": [
                          { "type": "unknown" } ] }
                        """).getAsJsonObject()));
    }

    @Test
    void cameraSpritesShareTheEffectSpriteBudget() {
        VfxRecipe recipe = VfxRecipe.parse(JsonParser.parseString("""
                { "duration": 20, "layers": [
                  { "type": "motes", "burst": 200 },
                  { "type": "camera", "burst": 200 } ] }
                """).getAsJsonObject());

        assertEquals(200, recipe.layers().get(0).burst());
        assertEquals(40, recipe.cameras().get(0).layer().burst());
        assertTrue(recipe.diagnostics().stream()
                .anyMatch(line -> line.contains("capped at 40 sprites")));
    }

    @Test
    void cameraAndMoteSpriteKeysCannotCollide() {
        VfxDefinition definition = new VfxDefinition(Key.key("testpack", "frost"), "Frost",
                VfxRecipe.parse(JsonParser.parseString("""
                        { "duration": 20, "layers": [
                          { "type": "motes", "burst": 1 },
                          { "type": "camera", "burst": 1 } ] }
                        """).getAsJsonObject()), null);

        assertEquals(Key.key("testpack:vfx/frost/0"), definition.spriteModelFor(0));
        assertEquals(Key.key("testpack:vfx/frost/camera_0"),
                definition.spriteModelFor(-1));
        assertEquals(Key.key("testpack:vfx/frost/camera_0"),
                definition.cameraSpriteModel(0));
        assertTrue(VfxDefinition.isCameraLayer(-1));
        assertFalse(VfxDefinition.isCameraLayer(0));
    }

    @Test
    void modelSpinAndScaleAreClamped() {
        VfxRecipe recipe = VfxRecipe.parse(JsonParser.parseString("""
                { "duration": 20, "layers": [
                  { "type": "model", "model": "testpack:ruby",
                    "scale": 99, "spin": 999, "yaw": 9999 } ] }
                """).getAsJsonObject());
        VfxRecipe.Model model = recipe.models().get(0);
        assertEquals(10, model.scale(), 1e-9);
        assertEquals(180, model.spin(), 1e-9);
        assertEquals(1080, model.yaw(), 1e-9);
    }

    @Test
    void modelPlacementRotatesWithTheCastAndSpinsOverTime() {
        VfxRecipe.Model model = new VfxRecipe.Model(Key.key("testpack:ruby"),
                0, 20, new VfxRecipe.Vec3(0, 1, 2), 1.5, 10, 5, 0, 90);
        VfxModelPlacement.Placed south = VfxModelPlacement.at(
                new VfxRecipe.Vec3(10, 20, 30), 0, model, 0);
        assertEquals(10, south.x(), 1e-9);
        assertEquals(21, south.y(), 1e-9);
        assertEquals(32, south.z(), 1e-9);
        assertEquals(10, south.yaw(), 1e-9);
        assertEquals(5, south.pitch(), 1e-9);
        assertEquals(1.5, south.scale(), 1e-9);

        VfxModelPlacement.Placed later = VfxModelPlacement.at(
                new VfxRecipe.Vec3(10, 20, 30), 0, model, 2);
        assertEquals(190, later.yaw(), 1e-9, "spin advances per recipe tick");

        VfxModelPlacement.Placed west = VfxModelPlacement.at(
                new VfxRecipe.Vec3(10, 20, 30), 90, model, 0);
        assertEquals(8, west.x(), 1e-9);
        assertEquals(30, west.z(), 1e-9);
    }

    @Test
    void cameraBasisRotatesSpritesWithYawAndPitch() {
        VfxRecipe recipe = new VfxRecipe(20, null, List.of(), List.of(
                new VfxRecipe.Camera(new VfxRecipe.Layer("glow", 0, 20, 1,
                        VfxRecipe.Vec3.ZERO, VfxRecipe.Shape.SPHERE, 0, 0, 0,
                        0.3, 20, 0, 0xFFFFFF), 2, 0)),
                List.of(), List.of(), List.of());
        VfxSimulation simulation = new VfxSimulation(recipe, VfxRecipe.Vec3.ZERO, 0, 1);
        int tick = simulation.motes().get(0).spawnTick();

        VfxSimulation.Sprite south = simulation.frame(tick, 0, 0, 20, VfxRecipe.Vec3.ZERO,
                0, 1, new VfxSimulation.CameraBasis(0, 64, 0, 0, 0)).get(0);
        assertEquals(0, south.x(), 1e-9);
        assertEquals(64, south.y(), 1e-9);
        assertEquals(2, south.z(), 1e-9);

        VfxSimulation.Sprite west = simulation.frame(tick, 0, 0, 20, VfxRecipe.Vec3.ZERO,
                0, 1, new VfxSimulation.CameraBasis(0, 64, 0, 90, 0)).get(0);
        assertEquals(-2, west.x(), 1e-9);
        assertEquals(64, west.y(), 1e-9);
        assertEquals(0, west.z(), 1e-9);

        VfxSimulation.Sprite down = simulation.frame(tick, 0, 0, 20, VfxRecipe.Vec3.ZERO,
                0, 1, new VfxSimulation.CameraBasis(0, 64, 0, 0, 90)).get(0);
        assertEquals(0, down.x(), 1e-9);
        assertEquals(62, down.y(), 1e-9);
        assertEquals(0, down.z(), 1e-9);
    }

    @Test
    void cameraSpritesAreSkippedWhenNoBasisIsSupplied() {
        VfxRecipe recipe = new VfxRecipe(20, null, List.of(), List.of(
                new VfxRecipe.Camera(new VfxRecipe.Layer("glow", 0, 20, 1,
                        VfxRecipe.Vec3.ZERO, VfxRecipe.Shape.SPHERE, 0, 0, 0,
                        0.3, 20, 0, 0xFFFFFF), 2, 0)),
                List.of(), List.of(), List.of());
        VfxSimulation simulation = new VfxSimulation(recipe, VfxRecipe.Vec3.ZERO, 0, 1);
        int tick = simulation.motes().get(0).spawnTick();

        assertTrue(simulation.frame(tick).isEmpty());
        assertEquals(1, simulation.frame(tick, 0, 0, 20, VfxRecipe.Vec3.ZERO, 0, 1,
                new VfxSimulation.CameraBasis(0, 64, 0, 0, 0)).size());
    }
}
