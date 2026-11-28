package io.kalo.compat;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class CompatIdsTest {
    private static final List<String> KEYS = List.of("zpack:ruby_sword", "apack:ruby_sword", "apack:emerald");

    @Test
    void bareIdMatchesValue() {
        assertEquals("apack:emerald", CompatIds.resolve("emerald", KEYS));
    }

    @Test
    void duplicateValuePicksFirstNamespaceRegardlessOfOrder() {
        assertEquals("apack:ruby_sword", CompatIds.resolve("ruby_sword", KEYS));
        assertEquals("apack:ruby_sword", CompatIds.resolve("ruby_sword", List.of(KEYS.get(2), KEYS.get(1), KEYS.get(0))));
    }

    @Test
    void namespacedIdIsTakenLiterally() {
        assertEquals("zpack:ruby_sword", CompatIds.resolve("zpack:ruby_sword", KEYS));
        assertNull(CompatIds.resolve("other:ruby_sword", KEYS));
    }

    @Test
    void unknownOrEmptyIsNull() {
        assertNull(CompatIds.resolve("nope", KEYS));
        assertNull(CompatIds.resolve("", KEYS));
    }

    @Test
    void kaloKeyMapsToFlatId() {
        assertEquals("ruby_sword", CompatIds.fromKaloKey("apack:ruby_sword"));
    }
}
