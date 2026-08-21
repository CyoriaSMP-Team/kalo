package io.kalo.content.glyph;

import io.kalo.content.PackContext;
import io.kalo.content.glyph.definition.GlyphDefinition;
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

class GlyphTypeTest {

    private static ConfigurationSection glyph(String character) {
        YamlConfiguration yaml = new YamlConfiguration();
        try {
            yaml.loadFromString("""
                    coin:
                      texture: font/coin
                      character: %s
                    """.formatted(character));
        } catch (Exception e) {
            throw new AssertionError(e);
        }
        return java.util.Objects.requireNonNull(yaml.getConfigurationSection("coin"));
    }

    @Test
    void aLiteralMustContainExactlyOneCodepoint() {
        assertThrows(IllegalArgumentException.class, () -> GlyphType.parseCharacter("ab"));
        assertEquals(0x1F48E, GlyphType.parseCharacter("💎"));
    }

    @Test
    void invalidUnicodeScalarValuesAreRejectedBeforePackCompilation() {
        assertThrows(IllegalArgumentException.class, () -> new GlyphDefinition(
                Key.key("testpack", "too_high"), Key.key("testpack", "font/x"),
                Character.MAX_CODE_POINT + 1, 7, 8, Key.key("minecraft", "default")));
        assertThrows(IllegalArgumentException.class, () -> new GlyphDefinition(
                Key.key("testpack", "surrogate"), Key.key("testpack", "font/x"),
                Character.MIN_SURROGATE, 7, 8, Key.key("minecraft", "default")));
    }

    @Test
    void duplicateKeysKeepTheFirstDefinition() {
        GlyphType type = new GlyphType();
        PackContext pack = new PackContext("testpack", new File("."));
        RegistriesImpl registries = new RegistriesImpl();

        assertTrue(type.load(pack, registries, glyph("U+E000")));
        assertFalse(type.load(pack, registries, glyph("U+E001")));
        assertEquals(1, type.size());
        assertEquals(0xE000, type.glyphs().get(Key.key("testpack", "coin")).character());
    }
}
