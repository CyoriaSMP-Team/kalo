package io.kalo.platform.java;

import io.kalo.content.vehicle.VehicleDefinition;
import org.bukkit.Location;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class VehicleSeatsTest {

    private static Location carrier(float yaw) {
        // A null world is fine: rotation math never touches it.
        Location location = new Location(null, 10, 64, 20);
        location.setYaw(yaw);
        return location;
    }

    @Test
    void aSeatOffsetIsRotatedWithTheCarrier() {
        VehicleDefinition.Seat seat = new VehicleDefinition.Seat(0.7, 0.2, 1.0);

        Location south = VehicleSeats.seat(carrier(0f), seat);
        // Facing south (+Z): the wearer's right is west (-X).
        assertEquals(9.3, south.getX(), 1e-9);
        assertEquals(64.2, south.getY(), 1e-9);
        assertEquals(21.0, south.getZ(), 1e-9);

        Location west = VehicleSeats.seat(carrier(90f), seat);
        // Facing west (-X): right is north (-Z), and forward is -X.
        assertEquals(9.0, west.getX(), 1e-9);
        assertEquals(19.3, west.getZ(), 1e-9);
    }

    @Test
    void seatYawFollowsTheCarrier() {
        Location seat = VehicleSeats.seat(carrier(45f),
                new VehicleDefinition.Seat(0, 0, 0));
        assertEquals(45f, seat.getYaw(), 1e-4);
    }
}
