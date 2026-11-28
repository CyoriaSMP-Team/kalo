package io.kalo.content.vfx.definition;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VfxCameraPlanTest {

    @Test
    void aDefaultCameraSitsAtTheEyeAndReportsItsFov() {
        VfxCameraFrame frame = VfxCameraPlan.frame(camera(0, 20, 70, 0, 0, 0),
                new VfxSimulation.CameraBasis(1, 64, 2, 0, 0), 5, 1);

        assertEquals(1, frame.x(), 1e-9);
        assertEquals(64, frame.y(), 1e-9);
        assertEquals(2, frame.z(), 1e-9);
        assertEquals(0, frame.yaw(), 1e-9);
        assertEquals(0, frame.pitch(), 1e-9);
        assertEquals(70, frame.fov(), 1e-9);
    }

    @Test
    void yawPitchAndFovMoveTheCameraPose() {
        VfxCameraFrame narrow = VfxCameraPlan.frame(camera(0, 20, 55, 15, -10, 0),
                new VfxSimulation.CameraBasis(0, 64, 0, 0, 0), 5, 1);

        assertEquals(15, narrow.yaw(), 1e-9);
        assertEquals(-10, narrow.pitch(), 1e-9);
        assertEquals(55, narrow.fov(), 1e-9);
        assertEquals((1 - 55.0 / 70.0) * 2, narrow.z(), 1e-9,
                "a narrower FOV dollies the camera towards the effect");

        VfxCameraFrame wide = VfxCameraPlan.frame(camera(0, 20, 110, 0, 0, 0),
                new VfxSimulation.CameraBasis(0, 64, 0, 0, 0), 5, 1);
        assertEquals((1 - 110.0 / 70.0) * 2, wide.z(), 1e-9,
                "a wider FOV pulls the camera back");
        assertTrue(wide.z() < 0);
    }

    @Test
    void cameraSpaceOffsetsAreRotatedWithTheCaster() {
        VfxRecipe.Layer layer = layer(0, 20, new VfxRecipe.Vec3(1, 2, 0));
        VfxCameraFrame south = VfxCameraPlan.frame(
                new VfxRecipe.Camera(layer, 2, 0, 70, 0, 0),
                new VfxSimulation.CameraBasis(0, 64, 0, 0, 0), 5, 1);
        assertEquals(-1, south.x(), 1e-9);
        assertEquals(66, south.y(), 1e-9);
        assertEquals(0, south.z(), 1e-9);

        VfxCameraFrame west = VfxCameraPlan.frame(
                new VfxRecipe.Camera(layer, 2, 0, 70, 0, 0),
                new VfxSimulation.CameraBasis(0, 64, 0, 90, 0), 5, 1);
        assertEquals(0, west.x(), 1e-9);
        assertEquals(66, west.y(), 1e-9);
        assertEquals(-1, west.z(), 1e-9);
    }

    @Test
    void shakeIsDeterministicButChangesWithTheTick() {
        VfxRecipe.Camera camera = camera(0, 20, 70, 0, 0, 0.5);
        VfxSimulation.CameraBasis view = new VfxSimulation.CameraBasis(0, 64, 0, 0, 0);

        VfxCameraFrame first = VfxCameraPlan.frame(camera, view, 5, 1);
        VfxCameraFrame same = VfxCameraPlan.frame(camera, view, 5, 1);
        VfxCameraFrame later = VfxCameraPlan.frame(camera, view, 6, 1);

        assertEquals(first, same, "the same tick and seed must produce the same pose");
        assertNotEquals(first, later);
        assertTrue(Math.abs(first.yaw()) > 0.01 || Math.abs(first.pitch()) > 0.01,
                "shake must affect the view");
    }

    @Test
    void theFirstActiveCameraLayerWinsAndEndsAreExclusive() {
        VfxRecipe recipe = new VfxRecipe(20, null, List.of(), List.of(
                camera(5, 10, 70, 0, 0, 0),
                camera(10, 15, 55, 0, 0, 0)),
                List.of(), List.of(), List.of());
        VfxSimulation.CameraBasis view = new VfxSimulation.CameraBasis(0, 64, 0, 0, 0);

        assertNull(VfxCameraPlan.activeFrame(recipe, view, 4, 1));
        assertEquals(70, VfxCameraPlan.activeFrame(recipe, view, 5, 1).fov(), 1e-9);
        assertEquals(70, VfxCameraPlan.activeFrame(recipe, view, 9, 1).fov(), 1e-9);
        assertEquals(55, VfxCameraPlan.activeFrame(recipe, view, 10, 1).fov(), 1e-9);
        assertNull(VfxCameraPlan.activeFrame(recipe, view, 15, 1));
    }

    private static VfxRecipe.Camera camera(int start, int end, double fov,
                                            double yaw, double pitch, double shake) {
        return new VfxRecipe.Camera(layer(start, end, VfxRecipe.Vec3.ZERO),
                2.0, shake, fov, yaw, pitch);
    }

    private static VfxRecipe.Layer layer(int start, int end, VfxRecipe.Vec3 at) {
        return new VfxRecipe.Layer("glow", start, end, 1, at,
                VfxRecipe.Shape.SPHERE, 0, 0, 0, 0.3, 10, 0, 0xFFFFFF);
    }
}
