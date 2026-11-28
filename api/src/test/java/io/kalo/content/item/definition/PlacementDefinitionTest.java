package io.kalo.content.item.definition;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PlacementDefinitionTest {

    @Test
    void defaultsDescribeAOneBlockStaticPiece() {
        PlacementDefinition defaults = PlacementDefinition.defaults();
        assertEquals(1.0, defaults.scale(), 1e-9);
        assertEquals(1.0, defaults.width(), 1e-9);
        assertEquals(1.0, defaults.height(), 1e-9);
        assertNull(defaults.seat());
        assertEquals(0, defaults.light());
        assertNull(defaults.idleAnimation());
        assertNull(defaults.interactAnimation());
    }

    @Test
    void invalidScaleLightAndDimensionsAreRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> new PlacementDefinition(0, 1, 1, null, 0, null, null));
        assertThrows(IllegalArgumentException.class,
                () -> new PlacementDefinition(1, 0, 1, null, 0, null, null));
        assertThrows(IllegalArgumentException.class,
                () -> new PlacementDefinition(1, 1, 1, -1.0, 0, null, null));
        assertThrows(IllegalArgumentException.class,
                () -> new PlacementDefinition(1, 1, 1, null, 16, null, null));
    }
}
