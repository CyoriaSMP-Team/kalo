package io.kalo.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class BoundedCacheTest {

    @Test
    void evictsLeastRecentlyUsed() {
        BoundedCache<String, String> cache = new BoundedCache<>(2);
        cache.put("a", "1");
        cache.put("b", "2");
        cache.get("a");

        cache.put("c", "3");

        assertEquals(2, cache.size());
        assertNotNull(cache.get("a"));
        assertNull(cache.get("b"));
        assertEquals("3", cache.get("c"));
    }

    @Test
    void resizeShrinksImmediately() {
        BoundedCache<String, String> cache = new BoundedCache<>(4);
        for (int i = 0; i < 4; i++) {
            cache.put("k" + i, "v" + i);
        }

        cache.resize(2);

        assertEquals(2, cache.size());
        assertEquals(2, cache.capacity());
        assertNull(cache.get("k0"));
        assertEquals("v3", cache.get("k3"));
    }

    @Test
    void resizeNeverGoesBelowOne() {
        BoundedCache<String, String> cache = new BoundedCache<>(4);
        cache.put("a", "1");

        cache.resize(0);

        assertEquals(1, cache.capacity());
        assertEquals(1, cache.size());
    }

    @Test
    void putOverCapacityKeepsNewest() {
        BoundedCache<String, String> cache = new BoundedCache<>(1);
        cache.put("a", "1");
        cache.put("b", "2");

        assertEquals(1, cache.size());
        assertNull(cache.get("a"));
        assertEquals("2", cache.get("b"));
    }
}
