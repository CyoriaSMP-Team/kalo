package io.kalo.items;

import net.kyori.adventure.key.Key;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IdentityRulesTest {

    private static final Key KRATHONG = Key.key("teddy_loy", "teddy_krathong");
    private static final Key MODEL = Key.key("plny_christmas_2025_set", "armor_boots");
    private static final Set<Key> KNOWN = Set.of(KRATHONG, MODEL);

    @Test
    void markerAloneWins() {
        assertEquals(KRATHONG,
                IdentityRules.classify("teddy_loy:teddy_krathong", null, KNOWN::contains)
                        .orElseThrow());
    }

    @Test
    void modelAloneIsAccepted() {
        assertEquals(MODEL,
                IdentityRules.classify(null, MODEL, KNOWN::contains).orElseThrow());
    }

    @Test
    void markerBeatsModel() {
        assertEquals(KRATHONG,
                IdentityRules.classify("teddy_loy:teddy_krathong", MODEL, KNOWN::contains)
                        .orElseThrow());
    }

    @Test
    void unknownMarkerFallsBackToModel() {
        assertEquals(MODEL,
                IdentityRules.classify("other_pack:gone", MODEL, KNOWN::contains).orElseThrow());
    }

    @Test
    void malformedMarkerFallsBackToModel() {
        assertEquals(MODEL,
                IdentityRules.classify("not a key", MODEL, KNOWN::contains).orElseThrow());
    }

    @Test
    void unknownEverythingIsEmpty() {
        assertTrue(IdentityRules.classify("other_pack:gone",
                Key.key("other_pack", "model"), KNOWN::contains).isEmpty());
    }

    @Test
    void emptyMarkerIsIgnored() {
        assertTrue(IdentityRules.classify("", null, KNOWN::contains).isEmpty());
        assertTrue(IdentityRules.classify(null, null, KNOWN::contains).isEmpty());
    }
}
