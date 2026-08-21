package io.kalo.content.sound;

import io.kalo.content.PackContext;
import io.kalo.content.sound.definition.SoundCategory;
import io.kalo.content.sound.definition.SoundDefinition;
import io.kalo.registry.RegistriesImpl;
import net.kyori.adventure.key.Key;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.File;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SoundTypeTest {

    private static ConfigurationSection sound(String body) {
        YamlConfiguration yaml = new YamlConfiguration();
        try {
            yaml.loadFromString("sound:\n" + body.indent(2));
        } catch (Exception e) {
            throw new AssertionError(e);
        }
        return java.util.Objects.requireNonNull(yaml.getConfigurationSection("sound"));
    }

    @Test
    void malformedListEntriesAreRejectedInsteadOfSkipped() {
        assertThrows(IllegalArgumentException.class,
                () -> SoundType.parse(Key.key("testpack", "sound"), sound("""
                        sounds:
                          - 42
                          - ambient/valid
                        """)));
    }

    @Test
    void numericOptionsMustActuallyBeNumbersAndWeightsMustBeIntegral() {
        assertThrows(IllegalArgumentException.class,
                () -> SoundType.parse(Key.key("testpack", "sound"), sound("""
                        sounds:
                          - file: ambient/x
                            volume: loud
                        """)));
        assertThrows(IllegalArgumentException.class,
                () -> SoundType.parse(Key.key("testpack", "sound"), sound("""
                        sounds:
                          - file: ambient/x
                            weight: 1.5
                        """)));
    }

    @Test
    void nonFiniteValuesCannotReachJsonOutput() {
        assertThrows(IllegalArgumentException.class, () -> new SoundDefinition.SoundFile(
                Key.key("testpack", "x"), Float.NaN, 1, 1));
        assertThrows(IllegalArgumentException.class, () -> new SoundDefinition.SoundFile(
                Key.key("testpack", "x"), 1, Float.POSITIVE_INFINITY, 1));
    }

    @Test
    void duplicateKeysKeepTheFirstDefinition() {
        SoundType type = new SoundType();
        PackContext pack = new PackContext("testpack", new File("."));
        RegistriesImpl registries = new RegistriesImpl();

        assertTrue(type.load(pack, registries, sound("sounds: [ambient/first]\n")));
        assertFalse(type.load(pack, registries, sound("sounds: [ambient/second]\n")));
        assertEquals(1, type.size());
    }
}
