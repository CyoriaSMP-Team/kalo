package io.kalo.platform.java;

import io.kalo.content.vehicle.VehicleDefinition;
import org.jetbrains.annotations.NotNull;

/** Pure movement decisions shared by the vehicle runtime and its tests. */
final class VehicleMotion {

    private VehicleMotion() {
    }

    record Motion(double x, double y, double z) {
    }

    static @NotNull Motion step(@NotNull VehicleDefinition.Physics physics, float yaw,
                                double throttle, boolean inWater, boolean jump,
                                boolean blocked) {
        double speed = blocked ? 0 : throttle;
        double x = -Math.sin(Math.toRadians(yaw)) * speed;
        double z = Math.cos(Math.toRadians(yaw)) * speed;
        double y = switch (physics.medium()) {
            case WATER -> inWater ? physics.buoyancy() : -physics.gravity();
            case AIR -> jump ? physics.climbSpeed() : -physics.gravity();
            case LAND -> 0;
        };
        return new Motion(x, y, z);
    }
}
