package io.kalo.platform.java;

import io.kalo.content.emote.EmoteDefinition;
import org.bukkit.Location;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class EmoteFormationTest {

    private static Location lead(float yaw) {
        Location location = new Location(null, 10, 64, 20);
        location.setYaw(yaw);
        return location;
    }

    @Test
    void aPerformerOffsetFollowsTheLeadsFacing() {
        Location south = EmoteFormation.at(lead(0),
                new EmoteDefinition.Performer("partner", 0.8, 0, 1.0));
        assertEquals(9.2, south.getX(), 1e-9);
        assertEquals(21.0, south.getZ(), 1e-9);

        Location west = EmoteFormation.at(lead(90),
                new EmoteDefinition.Performer("partner", 0.8, 0, 1.0));
        assertEquals(9.0, west.getX(), 1e-9);
        assertEquals(19.2, west.getZ(), 1e-9);
    }
}
