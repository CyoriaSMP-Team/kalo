package io.kalo.content.vehicle;

import io.kalo.content.item.definition.ModelDefinition;
import net.kyori.adventure.key.Key;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.EntityType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class VehicleConfigTest {

    private static YamlConfiguration yaml(String text) {
        YamlConfiguration yaml = new YamlConfiguration();
        try {
            yaml.loadFromString(text);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
        return yaml;
    }

    @Test
    void aVehicleParsesMovementAndModel() {
        YamlConfiguration yaml = yaml("""
                name: "Speeder"
                base: armor_stand
                speed: 0.4
                turn-speed: 9
                scale: 1.5
                y-offset: 0.25
                seats: 2
                model:
                  blockbench: models/vehicle/speeder.bbmodel
                """);

        VehicleDefinition definition = VehicleType.parseDefinition(
                Key.key("testpack", "speeder"), yaml);

        assertEquals(EntityType.ARMOR_STAND, definition.base());
        assertEquals(0.4, definition.speed(), 1e-9);
        assertEquals(9, definition.turnSpeed(), 1e-9);
        assertEquals(1.5, definition.scale(), 1e-9);
        assertEquals(0.25, definition.yOffset(), 1e-9);
        assertEquals(2, definition.seats().size());
        assertEquals(Key.key("testpack", "models/vehicle/speeder.bbmodel"),
                ((ModelDefinition.Blockbench) definition.model()).source());
    }

    @Test
    void anIntegerSeatCountBecomesACentredRow() {
        VehicleDefinition definition = VehicleType.parseDefinition(
                Key.key("testpack", "bus"), yaml("""
                        seats: 3
                        model:
                          blockbench: models/vehicle/bus.bbmodel
                        """));

        assertEquals(3, definition.seats().size());
        assertEquals(-0.7, definition.seats().get(0).x(), 1e-9);
        assertEquals(0.0, definition.seats().get(1).x(), 1e-9);
        assertEquals(0.7, definition.seats().get(2).x(), 1e-9);
        assertEquals(0.2, definition.seats().get(1).y(), 1e-9);
    }

    @Test
    void anExplicitSeatListKeepsEveryPosition() {
        VehicleDefinition definition = VehicleType.parseDefinition(
                Key.key("testpack", "bus"), yaml("""
                        seats:
                          - {x: 0.0, y: 0.3, z: 0.0}
                          - {x: 0.8, y: 0.4, z: -1.0}
                        model:
                          blockbench: models/vehicle/bus.bbmodel
                        """));

        assertEquals(2, definition.seats().size());
        assertEquals(0.0, definition.seats().get(0).x(), 1e-9);
        assertEquals(0.3, definition.seats().get(0).y(), 1e-9);
        assertEquals(0.8, definition.seats().get(1).x(), 1e-9);
        assertEquals(-1.0, definition.seats().get(1).z(), 1e-9);
    }

    @Test
    void mediumBuoyancyFlightAndCollisionAreParsed() {
        VehicleDefinition definition = VehicleType.parseDefinition(
                Key.key("testpack", "boat"), yaml("""
                        medium: water
                        buoyancy: 0.1
                        gravity: 0.03
                        climb-speed: 0.4
                        collision: false
                        model:
                          blockbench: models/vehicle/boat.bbmodel
                        """));

        assertEquals(io.kalo.content.vehicle.VehicleMedium.WATER,
                definition.physics().medium());
        assertEquals(0.1, definition.physics().buoyancy(), 1e-9);
        assertEquals(0.03, definition.physics().gravity(), 1e-9);
        assertEquals(0.4, definition.physics().climbSpeed(), 1e-9);
        assertFalse(definition.physics().collision());
    }

    @Test
    void anUnknownMediumIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> VehicleType.parseDefinition(
                Key.key("testpack", "boat"), yaml("""
                        medium: space
                        model:
                          blockbench: models/vehicle/boat.bbmodel
                        """)));
    }

    @Test
    void aVehicleWithoutAModelIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> VehicleType.parseDefinition(
                Key.key("testpack", "speeder"), yaml("speed: 0.4")));
    }

    @Test
    void aNonLivingBaseIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> VehicleType.parseDefinition(
                Key.key("testpack", "speeder"), yaml("""
                        base: boat
                        model:
                          blockbench: models/vehicle/speeder.bbmodel
                        """)));
    }
}
