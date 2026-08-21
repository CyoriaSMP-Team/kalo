package io.kalo.registry;

import net.kyori.adventure.key.Key;
import org.junit.jupiter.api.Test;

import java.util.Iterator;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class MappedRegistryTest {

    @Test
    void iteratorCannotMutateAReadOnlyRegistry() {
        Key key = Key.key("test", "entry");
        MappedRegistry<String> registry = new MappedRegistry<>(Map.of(key, "value"));

        Iterator<String> iterator = registry.iterator();
        assertEquals("value", iterator.next());
        assertThrows(UnsupportedOperationException.class, iterator::remove);
        assertTrue(registry.get(key).isPresent());
    }
}
