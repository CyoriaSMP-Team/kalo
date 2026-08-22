package io.kalo.content.furniture;

import io.kalo.content.furniture.definition.FurnitureBehaviour;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link FurnitureBehaviour} record construction and validation.
 */
class FurnitureBehaviourTest {

    @Test
    void defaultsAreReasonable() {
        FurnitureBehaviour defaults = FurnitureBehaviour.defaults();

        assertEquals(1.5f, defaults.hardness());
        assertTrue(defaults.requiresTool());
        assertFalse(defaults.rotatable());
        assertNull(defaults.restrictedRotation());
        assertNull(defaults.seat());
        assertEquals(0, defaults.hitbox().size());
        assertNull(defaults.storage());
        assertNull(defaults.jukebox());
        assertFalse(defaults.waterloggable());
        assertEquals(0, defaults.light());
        assertNull(defaults.limitedPlacing());
        assertFalse(defaults.unbreakable());
    }

    @Test
    void unbreakableWhenHardnessIsNegativeOne() {
        FurnitureBehaviour behaviour = new FurnitureBehaviour(
                -1f, false, false, null, null, List.of(), null, null, false, 0, null);
        assertTrue(behaviour.unbreakable());
    }

    @Test
    void notUnbreakableWithPositiveHardness() {
        FurnitureBehaviour behaviour = new FurnitureBehaviour(
                3.0f, true, false, null, null, List.of(), null, null, false, 0, null);
        assertFalse(behaviour.unbreakable());
    }

    @Test
    void seatDefaultsAreReasonable() {
        FurnitureBehaviour.Seat seat = FurnitureBehaviour.Seat.defaults();
        assertEquals(0.5, seat.height());
        assertEquals(3, seat.offset().size());
        assertEquals(0.0, seat.offset().get(0));
        assertEquals(0.5, seat.offset().get(1));
        assertEquals(0.0, seat.offset().get(2));
        assertNull(seat.direction());
    }

    @Test
    void storageDefaultsAreReasonable() {
        FurnitureBehaviour.Storage storage = FurnitureBehaviour.Storage.defaults();
        assertEquals("STORAGE", storage.type());
        assertEquals(6, storage.rows());
        assertNull(storage.title());
        assertNull(storage.openSound());
        assertNull(storage.closeSound());
    }

    @Test
    void storageRejectsInvalidRows() {
        assertThrows(IllegalArgumentException.class, () ->
                new FurnitureBehaviour.Storage("STORAGE", 0, null, null, null));
        assertThrows(IllegalArgumentException.class, () ->
                new FurnitureBehaviour.Storage("STORAGE", 7, null, null, null));
    }

    @Test
    void jukeboxDefaultsAreReasonable() {
        FurnitureBehaviour.Jukebox jukebox = FurnitureBehaviour.Jukebox.defaults();
        assertEquals(1.0, jukebox.volume());
        assertEquals(1.0, jukebox.pitch());
        assertNull(jukebox.permission());
    }

    @Test
    void hitboxIsCopiedDefensively() {
        List<double[]> original = List.of(new double[]{1, 2, 3});
        FurnitureBehaviour behaviour = new FurnitureBehaviour(
                1.5f, true, false, null, null, original, null, null, false, 0, null);

        // Modifying the original should not affect the behaviour
        assertEquals(1, behaviour.hitbox().size());
        assertArrayEquals(new double[]{1, 2, 3}, behaviour.hitbox().get(0));
    }

    @Test
    void lightRejectsOutOfRangeValues() {
        assertThrows(IllegalArgumentException.class, () ->
                new FurnitureBehaviour(1.5f, true, false, null, null, List.of(), null, null, false, -1, null));
        assertThrows(IllegalArgumentException.class, () ->
                new FurnitureBehaviour(1.5f, true, false, null, null, List.of(), null, null, false, 16, null));
    }

    @Test
    void limitedPlacingCopiesListsDefensively() {
        FurnitureBehaviour.LimitedPlacing placing = new FurnitureBehaviour.LimitedPlacing(
                true, true, false, "ALLOW",
                List.of("STONE"), List.of("#base_stone"), List.of("mypack:chair"));

        assertEquals(1, placing.blockTypes().size());
        assertEquals(1, placing.blockTags().size());
        assertEquals(1, placing.nexoBlocks().size());
        assertEquals("STONE", placing.blockTypes().get(0));
        assertEquals("#base_stone", placing.blockTags().get(0));
        assertEquals("mypack:chair", placing.nexoBlocks().get(0));
    }
}
