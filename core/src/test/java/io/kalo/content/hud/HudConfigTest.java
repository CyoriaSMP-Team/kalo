package io.kalo.content.hud;

import io.kalo.content.hud.definition.HudDefinition;
import net.kyori.adventure.key.Key;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class HudConfigTest {

    private static YamlConfiguration config() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("texture", "hud/mana");
        config.set("character", "U+E100");
        config.set("height", 32);
        config.set("ascent", 24);
        config.set("slot", "boss_bar");
        config.set("offset", -8);
        return config;
    }

    @Test
    void aHudParsesItsTextureCharacterSlotAndMetrics() {
        HudDefinition definition = HudType.parse(Key.key("testpack", "mana"), config());

        assertEquals(Key.key("testpack", "hud/mana"), definition.texture());
        assertEquals(0xE100, definition.character());
        assertEquals(32, definition.height());
        assertEquals(24, definition.ascent());
        assertEquals(HudDefinition.Slot.BOSS_BAR, definition.slot());
        assertEquals(Key.key("minecraft", "default"), definition.font());
        assertEquals(-8, definition.offset());
        assertEquals("\uE100", definition.glyph());
        assertEquals(definition.offsetGlyph() + "\uE100", definition.overlayText());
    }

    @Test
    void offsetIsClampedToTheSupportedRange() {
        YamlConfiguration high = config();
        high.set("offset", 900);
        assertEquals(HudDefinition.MAX_OFFSET,
                HudType.parse(Key.key("testpack", "mana"), high).offset());
        YamlConfiguration low = config();
        low.set("offset", -900);
        assertEquals(-HudDefinition.MAX_OFFSET,
                HudType.parse(Key.key("testpack", "mana"), low).offset());
    }

    @Test
    void aHudWithoutACharacterOrTextureIsRejected() {
        YamlConfiguration noCharacter = config();
        noCharacter.set("character", null);
        assertThrows(IllegalArgumentException.class,
                () -> HudType.parse(Key.key("testpack", "mana"), noCharacter));

        YamlConfiguration noTexture = config();
        noTexture.set("texture", null);
        assertThrows(IllegalArgumentException.class,
                () -> HudType.parse(Key.key("testpack", "mana"), noTexture));
    }
}
