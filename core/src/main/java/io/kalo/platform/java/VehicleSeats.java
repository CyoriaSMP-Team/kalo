package io.kalo.platform.java;

import io.kalo.content.vehicle.VehicleDefinition;
import org.bukkit.Location;
import org.jetbrains.annotations.NotNull;

/**
 * Rotates a seat's local offset into world space.
 *
 * <p>Seats are authored in the carrier frame: X is the wearer's right, Z is forward. The
 * carrier's yaw decides how that lands in the world.</p>
 */
final class VehicleSeats {

    private VehicleSeats() {
    }

    static @NotNull Location seat(@NotNull Location carrier,
                                  @NotNull VehicleDefinition.Seat seat) {
        double yaw = Math.toRadians(carrier.getYaw());
        double x = -Math.cos(yaw) * seat.x() - Math.sin(yaw) * seat.z();
        double z = -Math.sin(yaw) * seat.x() + Math.cos(yaw) * seat.z();
        Location result = carrier.clone().add(x, seat.y(), z);
        result.setYaw(carrier.getYaw());
        result.setPitch(0f);
        return result;
    }
}
