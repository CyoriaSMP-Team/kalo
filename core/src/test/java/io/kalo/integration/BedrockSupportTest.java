package io.kalo.integration;

import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BedrockSupportTest {

    @Test
    void theAbsentRuntimeAnswersNoWithoutNamingGeyser() {
        BedrockSupport none = BedrockSupport.NONE;
        assertFalse(none.available());
        assertFalse(none.isBedrock(UUID.randomUUID()));

        // Default hooks must be harmless on a Java-only server.
        UUID id = UUID.randomUUID();
        none.rigPlaced(id, "testpack:chair");
        none.animationStarted(id, "testpack:chair", "swing");
        none.rigRemoved(id);
        none.blockPlaced(id, 1, 2, 3, "testpack:fan");
        none.blockRemoved(id, 1, 2, 3);
        none.blockAnimationStarted(id, 1, 2, 3, "testpack:fan", "Open");
        none.mobSpawned(id, "testpack:golem", id, 1, 2, 3, 0);
        none.mobMoved(id, 1, 2, 3, 0);
        none.mobRemoved(id);
        none.mobAnimationStarted(id, "Open");
        none.armorAnimationStarted(id, "testpack:ruby_helmet", "Open");
        none.clientRigSpawned(id, "kalo:vehicle_testpack_speeder_vehicle", id, 1, 2, 3, 0);
        none.clientRigSpawned(id, "kalo:vehicle_testpack_speeder_vehicle", id, 1, 2, 3, 0, 2f);
        none.clientRigSpawned(id, "kalo:vfx_test", id, 1, 2, 3, 0, id, 2f);
        none.clientRigMoved(id, 1, 2, 3, 0);
        none.clientRigMoved(id, 1, 2, 3, 0, 2f);
        none.clientRigRemoved(id);
        none.forget(id);
    }

    @Test
    void theGuardExposesANonNullSeamEvenWithoutGeyser() {
        assertNotNull(GeyserIntegration.support());
        assertFalse(GeyserIntegration.support().available());
    }

    @Test
    void theScaledRigHooksFallBackToTheUnscaledHooks() {
        boolean[] spawned = {false};
        boolean[] moved = {false};
        BedrockSupport support = new BedrockSupport() {
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
                                         float yaw) {
                spawned[0] = true;
            }

            @Override
            public void clientRigMoved(@NotNull UUID entityId, double x, double y, double z,
                                       float yaw) {
                moved[0] = true;
            }
        };

        UUID id = UUID.randomUUID();
        support.clientRigSpawned(id, "kalo:vfx_test", id, 1, 2, 3, 0, 2.5f);
        support.clientRigMoved(id, 1, 2, 3, 0, 2.5f);

        assertTrue(spawned[0], "scaled spawn must reach the unscaled spawn hook");
        assertTrue(moved[0], "scaled move must reach the unscaled move hook");
    }

    @Test
    void thePositionAwarePlaceHookFallsBackToTheSimpleHook() {
        boolean[] called = {false};
        BedrockSupport support = new BedrockSupport() {
            @Override
            public boolean available() {
                return true;
            }

            @Override
            public boolean isBedrock(@NotNull UUID playerId) {
                return false;
            }

            @Override
            public void rigPlaced(@NotNull UUID hitboxId, @NotNull String modelId) {
                called[0] = true;
            }
        };
        support.rigPlaced(UUID.randomUUID(), "testpack:chair", 1, 2, 3, 0, 1);
        assertTrue(called[0]);
    }
}
