package io.kalo.content.armor;

import net.kyori.adventure.key.Key;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ArmorModelConfigTest {

    private static YamlConfiguration yaml(String text) {
        YamlConfiguration yaml = new YamlConfiguration();
        try {
            yaml.loadFromString(text);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
        return yaml;
    }

    @Test
    void aBlockbenchWornModelParsesWithTextureOverrides() {
        YamlConfiguration yaml = yaml("""
                blockbench: models/armor/ruby_helmet.bbmodel
                textures:
                  "0": entity/ruby_helmet
                animation:
                  idle: idle
                  interact: Open
                """);

        ArmorDefinition.ArmorModel model = ArmorType.parseArmorModel(
                Key.key("testpack", "ruby_helmet"), yaml);

        assertEquals(Key.key("testpack", "models/armor/ruby_helmet.bbmodel"),
                model.source());
        assertEquals(Key.key("testpack", "entity/ruby_helmet"),
                model.textures().get("0"));
        assertEquals("idle", model.animation().idle());
        assertEquals("Open", model.animation().interact());
    }

    @Test
    void aMissingWornModelSectionIsOptional() {
        assertNull(ArmorType.parseArmorModel(Key.key("testpack", "ruby_helmet"), null));
    }

    @Test
    void aNonBlockbenchWornModelIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> ArmorType.parseArmorModel(
                Key.key("testpack", "ruby_helmet"),
                yaml("blockbench: models/armor/ruby_helmet.json")));
    }
}
