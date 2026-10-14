package io.kalo.converter;

import net.kyori.adventure.key.Key;
import org.bukkit.Material;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class ExternalItemBindingsTest {
    @TempDir Path temporary;

    @Test
    void persistsRealObservedMaterialWithoutDuplicatingItsModel() throws IOException {
        Path path = temporary.resolve("external-items.yml");
        var first = new ExternalItemBindings.Binding(Key.key("demo:coin"), Material.PAPER, 64);
        var second = new ExternalItemBindings.Binding(Key.key("demo:coin"), Material.STICK, 1);
        assertTrue(ExternalItemBindings.load(path).isEmpty());
        ExternalItemBindings.remember(path, first);
        assertEquals(first, ExternalItemBindings.load(path).getFirst());
        ExternalItemBindings.remember(path, second);
        assertEquals(1, ExternalItemBindings.load(path).size());
        assertEquals(second, ExternalItemBindings.load(path).getFirst());
    }

    @Test
    void rejectsCorruptBindingsBeforeOverwritingThem() throws IOException {
        Path path = temporary.resolve("external-items.yml");
        String corrupted = "items:\n  - item-model: demo:coin\n    material: NOT_AN_ITEM\n    max-stack: 64\n";
        Files.writeString(path, corrupted);
        assertThrows(IOException.class, () -> ExternalItemBindings.remember(path,
                new ExternalItemBindings.Binding(Key.key("demo:new"), Material.PAPER, 64)));
        assertEquals(corrupted, Files.readString(path));
    }

    @Test
    void rejectsInvalidMaterialAndStackSize() {
        assertThrows(IllegalArgumentException.class, () ->
                new ExternalItemBindings.Binding(Key.key("demo:coin"), Material.AIR, 64));
        assertThrows(IllegalArgumentException.class, () ->
                new ExternalItemBindings.Binding(Key.key("demo:coin"), Material.PAPER, 100));
    }
}
