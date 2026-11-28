package io.kalo.platform.java;

import io.kalo.content.vehicle.VehicleDefinition;
import io.kalo.content.vehicle.VehicleMedium;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class VehicleMotionTest {

    private static VehicleDefinition.Physics physics(VehicleMedium medium) {
        return new VehicleDefinition.Physics(medium, 0.08, 0.02, 0.3, true);
    }

    @Test
    void aLandVehicleMovesHorizontallyAndStopsAtBlocks() {
        var free = VehicleMotion.step(physics(VehicleMedium.LAND), 0f, 0.4,
                false, false, false);
        assertEquals(0, free.x(), 1e-9);
        assertEquals(0.4, free.z(), 1e-9);
        assertEquals(0, free.y(), 1e-9);

        var blocked = VehicleMotion.step(physics(VehicleMedium.LAND), 0f, 0.4,
                false, false, true);
        assertEquals(0, blocked.x(), 1e-9);
        assertEquals(0, blocked.z(), 1e-9);
    }

    @Test
    void aWaterVehicleRisesInWaterAndSinksOutside() {
        assertEquals(0.08, VehicleMotion.step(physics(VehicleMedium.WATER), 0f, 0,
                true, false, false).y(), 1e-9);
        assertEquals(-0.02, VehicleMotion.step(physics(VehicleMedium.WATER), 0f, 0,
                false, false, false).y(), 1e-9);
    }

    @Test
    void anAirVehicleClimbsOnJumpAndFallsOtherwise() {
        assertEquals(0.3, VehicleMotion.step(physics(VehicleMedium.AIR), 0f, 0,
                false, true, false).y(), 1e-9);
        assertEquals(-0.02, VehicleMotion.step(physics(VehicleMedium.AIR), 0f, 0,
                false, false, false).y(), 1e-9);
    }
}
