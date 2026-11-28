package io.kalo.items;

import io.kalo.converter.ExternalItemBindings;
import net.kyori.adventure.key.Key;
import org.bukkit.Material;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExternalItemIndexTest {

    private static final Key MODEL = Key.key("plny_christmas_2025_set", "armor_boots");

    @Test
    void indexesInitialBindings() {
        ExternalItemIndex index = new ExternalItemIndex(List.of(
                new ExternalItemBindings.Binding(MODEL, Material.NETHERITE_BOOTS, 1)));

        assertTrue(index.contains(MODEL));
        assertEquals(Material.NETHERITE_BOOTS, index.get(MODEL).orElseThrow().material());
        assertEquals(1, index.size());
    }

    @Test
    void unknownKeyIsEmpty() {
        ExternalItemIndex index = new ExternalItemIndex(List.of());
        assertTrue(index.get(Key.key("kalo", "missing")).isEmpty());
        assertFalse(index.contains(Key.key("kalo", "missing")));
    }

    @Test
    void repeatedAddReplacesInsteadOfDuplicating() {
        ExternalItemIndex index = new ExternalItemIndex(List.of());
        index.add(new ExternalItemBindings.Binding(MODEL, Material.NETHERITE_BOOTS, 1));
        index.add(new ExternalItemBindings.Binding(MODEL, Material.LEATHER_BOOTS, 1));

        assertEquals(1, index.size());
        assertEquals(Material.LEATHER_BOOTS, index.get(MODEL).orElseThrow().material());
    }
}
