package io.kalo.registry;

import net.kyori.adventure.key.Key;
import org.junit.jupiter.api.Test;

import java.util.Collection;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ScalableRegistryTest {

    @Test
    void runtimeIdsAreDeterministicByKeyNotRegistrationOrder() {
        DirectScalableRegistry<String> first = new DirectScalableRegistry<>();
        first.register(Key.key("test", "z"), "z");
        first.register(Key.key("test", "a"), "a");
        first.lock();

        DirectScalableRegistry<String> second = new DirectScalableRegistry<>();
        second.register(Key.key("test", "a"), "a");
        second.register(Key.key("test", "z"), "z");
        second.lock();

        assertEquals(first.runtimeId(Key.key("test", "a")), second.runtimeId(Key.key("test", "a")));
        assertEquals(first.runtimeId(Key.key("test", "z")), second.runtimeId(Key.key("test", "z")));
        assertEquals(Optional.of("a"), first.getByRuntimeId(first.runtimeId(Key.key("test", "a"))));
    }

    @Test
    void entriesReuseOneImmutableSnapshotUntilAMutation() {
        DirectScalableRegistry<String> registry = new DirectScalableRegistry<>();
        registry.register(Key.key("test", "one"), "one");

        Collection<?> first = registry.entries();
        Collection<?> second = registry.entries();
        assertSame(first, second);

        long revision = registry.revision();
        registry.register(Key.key("test", "two"), "two");
        Collection<?> third = registry.entries();
        assertNotEquals(revision, registry.revision());
        assertEquals(2, third.size());
    }

    @Test
    void oneHundredThousandEntriesStayAddressableWithoutACap() {
        DirectScalableRegistry<Integer> registry = new DirectScalableRegistry<>();
        int count = 100_000;
        for (int i = 0; i < count; i++) {
            registry.register(Key.key("bench", "item_" + i), i);
        }
        registry.lock();

        assertEquals(count, registry.entries().size());
        for (int i = 0; i < count; i += 997) {
            int id = registry.runtimeId(Key.key("bench", "item_" + i));
            assertNotEquals(RuntimeIdRegistry.NO_RUNTIME_ID, id);
            assertEquals(i, registry.getByRuntimeId(id).orElseThrow());
        }
        assertThrows(IllegalStateException.class,
                () -> registry.register(Key.key("bench", "after_lock"), -1));
    }
}
