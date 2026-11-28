package io.kalo.platform.java;

import io.kalo.content.vfx.definition.VfxModelPlacement;
import io.kalo.content.vfx.definition.VfxSimulation;
import io.kalo.integration.BedrockSupport;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class VfxBedrockDeliveryTest {

    @Test
    void theSeamCarriesCastScaleNotTheAbsoluteSpriteSize() {
        RecordingSupport support = new RecordingSupport();
        UUID display = UUID.randomUUID();
        UUID world = UUID.randomUUID();
        UUID viewer = UUID.randomUUID();
        VfxSimulation.Sprite sprite = new VfxSimulation.Sprite(1, 0, 1.5, 2.5, 3.5, 0.4);

        VfxBedrockDelivery.spawn(support, display, "kalo:vfx_test", world, sprite, 30f,
                viewer, 2.0);

        assertEquals(display, support.spawnId);
        assertEquals(viewer, support.viewer);
        assertEquals(1.5, support.x, 1e-9);
        assertEquals(2.0f, support.spawnScale, 1e-6f,
                "geometry is baked at layer size, so the seam carries cast scale");
        assertEquals(0.4, sprite.size(), 1e-9);

        VfxBedrockDelivery.move(support, display, sprite, 45f, 2.0);

        assertEquals(2.0f, support.moveScale, 1e-6f);
        assertEquals(45f, support.moveYaw, 1e-6f);
    }

    @Test
    void modelLayersCarryPitchAndModelScale() {
        RecordingSupport support = new RecordingSupport();
        UUID display = UUID.randomUUID();
        UUID viewer = UUID.randomUUID();
        UUID world = UUID.randomUUID();
        VfxModelPlacement.Placed placed = new VfxModelPlacement.Placed(
                1.0, 2.0, 3.0, 45f, 10f, 5f, 1.5f);

        VfxBedrockDelivery.modelSpawn(support, display, "kalo:vfx_model_test", world,
                placed, viewer);

        assertEquals(display, support.spawnId);
        assertEquals(viewer, support.viewer);
        assertEquals(45f, support.spawnYaw, 1e-6f);
        assertEquals(10f, support.spawnPitch, 1e-6f);
        assertEquals(1.5f, support.spawnScale, 1e-6f);

        VfxBedrockDelivery.modelMove(support, display, placed);

        assertEquals(10f, support.movePitch, 1e-6f);
        assertEquals(1.5f, support.moveScale, 1e-6f);
    }

    private static final class RecordingSupport implements BedrockSupport {
        private UUID spawnId;
        private UUID viewer;
        private double x;
        private float spawnYaw = Float.NaN;
        private float spawnPitch = Float.NaN;
        private float spawnScale = Float.NaN;
        private float moveScale = Float.NaN;
        private float moveYaw = Float.NaN;
        private float movePitch = Float.NaN;

        @Override
        public boolean available() {
            return true;
        }

        @Override
        public boolean isBedrock(@NotNull UUID playerId) {
            return false;
        }

        @Override
        public void clientRigSpawned(@NotNull UUID entityId,
                                     @NotNull String entityIdentifier,
                                     @NotNull UUID worldId, double x, double y, double z,
                                     float yaw, UUID viewerId, float scale) {
            spawn(entityId, viewerId, x, yaw, 0f, scale);
        }

        @Override
        public void clientRigSpawned(@NotNull UUID entityId,
                                     @NotNull String entityIdentifier,
                                     @NotNull UUID worldId, double x, double y, double z,
                                     float yaw, float pitch, UUID viewerId, float scale) {
            spawn(entityId, viewerId, x, yaw, pitch, scale);
        }

        private void spawn(UUID entityId, UUID viewerId, double x, float yaw,
                           float pitch, float scale) {
            this.spawnId = entityId;
            this.viewer = viewerId;
            this.x = x;
            this.spawnYaw = yaw;
            this.spawnPitch = pitch;
            this.spawnScale = scale;
        }

        @Override
        public void clientRigMoved(@NotNull UUID entityId, double x, double y, double z,
                                   float yaw, float scale) {
            move(yaw, 0f, scale);
        }

        @Override
        public void clientRigMoved(@NotNull UUID entityId, double x, double y, double z,
                                   float yaw, float pitch, float scale) {
            move(yaw, pitch, scale);
        }

        private void move(float yaw, float pitch, float scale) {
            this.moveScale = scale;
            this.moveYaw = yaw;
            this.movePitch = pitch;
        }
    }
}
