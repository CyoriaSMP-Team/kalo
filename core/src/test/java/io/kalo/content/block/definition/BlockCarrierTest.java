package io.kalo.content.block.definition;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The carrier is the layer that decides what a placed block looks like forever, so its
 * maths is pinned here rather than trusted.
 */
class BlockCarrierTest {

    @Test
    void everyStateHasADistinctVariantKey() {
        // A collision would mean two custom blocks drawn as the same thing, with no error
        // anywhere — the resource pack would simply have one fewer entry than expected.
        for (BlockCarrier carrier : BlockCarrier.values()) {
            Set<String> keys = new HashSet<>();
            for (int i = 0; i < carrier.stateCount(); i++) {
                assertTrue(keys.add(carrier.variantKey(i)),
                        carrier + " produced a duplicate variant at " + i);
            }
            assertEquals(carrier.stateCount(), keys.size());
        }
    }

    @Test
    void noteBlockOffersTheExpectedStates() {
        // 16 usable instruments x 25 notes x powered.
        assertEquals(800, BlockCarrier.NOTE_BLOCK.stateCount());
        assertEquals(799, BlockCarrier.NOTE_BLOCK.usableStateCount());
    }

    @Test
    void tripwireAddsCapacityForFlatBlocks() {
        // attached is pinned false — vanilla draws attached tripwire differently.
        assertEquals(64, BlockCarrier.TRIPWIRE.stateCount());
    }

    @Test
    void decodingCoversEveryPropertyOfTheCarrier() {
        for (BlockCarrier carrier : BlockCarrier.values()) {
            var values = carrier.decode(1);
            assertEquals(carrier.properties().size(), values.size(), carrier.toString());
            carrier.properties().forEach(property ->
                    assertTrue(values.containsKey(property.name()),
                            carrier + " lost " + property.name()));
        }
    }

    @Test
    void decodedValuesAreOnesTheCarrierDeclares() {
        for (BlockCarrier carrier : BlockCarrier.values()) {
            for (int i = 0; i < Math.min(carrier.stateCount(), 100); i++) {
                final int state = i;
                var values = carrier.decode(state);
                carrier.properties().forEach(property ->
                        assertTrue(property.values().contains(values.get(property.name())),
                                carrier + " state " + state + " gave " + property.name() + "="
                                        + values.get(property.name())));
            }
        }
    }

    @Test
    void anOutOfRangeStateIsRejectedRatherThanWrappingAround() {
        // Wrapping would quietly alias one block onto another's appearance.
        assertThrows(IllegalArgumentException.class,
                () -> BlockCarrier.NOTE_BLOCK.decode(BlockCarrier.NOTE_BLOCK.stateCount()));
        assertThrows(IllegalArgumentException.class, () -> BlockCarrier.NOTE_BLOCK.decode(-1));
    }

    @Test
    void variantKeysArePropertySorted() {
        // Vanilla writes them alphabetically; matching it keeps the generated blockstates
        // file diffable against the client's own.
        String key = BlockCarrier.NOTE_BLOCK.variantKey(0);
        assertEquals("instrument=harp,note=0,powered=false", key);
    }

    @Test
    void pathsAndModelsDeriveFromTheVanillaBlock() {
        assertEquals("assets/minecraft/blockstates/note_block.json",
                BlockCarrier.NOTE_BLOCK.blockStatesPath());
        assertEquals("minecraft:block/note_block", BlockCarrier.NOTE_BLOCK.vanillaModel());
        assertEquals("assets/minecraft/blockstates/tripwire.json",
                BlockCarrier.TRIPWIRE.blockStatesPath());
    }

    @Test
    void carriersResolveByNameForConfigAndPersistence() {
        assertEquals(BlockCarrier.NOTE_BLOCK, BlockCarrier.fromId("note_block"));
        assertEquals(BlockCarrier.TRIPWIRE, BlockCarrier.fromId("TRIPWIRE"));
        assertThrows(IllegalArgumentException.class, () -> BlockCarrier.fromId("nope"));
    }
}
