package io.kalo.content.liquid;

import io.kalo.content.liquid.definition.LiquidDefinition;
import net.kyori.adventure.key.Key;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LiquidConfigTest {

    @Test
    void aLiquidParsesItsRulesAndColor() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("base", "water");
        config.set("color", "#3FBF4A");
        config.set("effect", "POISON");
        config.set("amplifier", 1);
        config.set("damage", 1.0);
        config.set("fireproof", false);

        LiquidDefinition definition = LiquidType.parse(
                Key.key("testpack", "acid"), config);

        assertEquals("acid", definition.name());
        assertEquals(LiquidDefinition.Base.WATER, definition.base());
        assertEquals(0x3FBF4A, definition.color());
        assertEquals("POISON", definition.effect());
        assertEquals(1, definition.amplifier());
        assertEquals(1.0, definition.damage(), 1e-9);
        assertEquals(false, definition.fireproof());
        assertEquals(Key.key("testpack", "liquid/acid"), definition.biomeKey());
    }

    @Test
    void baseColorDamageAndAmplifierAreValidated() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("base", "lava");
        config.set("amplifier", 999);
        config.set("damage", -4);
        LiquidDefinition definition = LiquidType.parse(
                Key.key("testpack", "magma"), config);
        assertEquals(LiquidDefinition.Base.LAVA, definition.base());
        assertEquals(255, definition.amplifier());
        assertEquals(0.0, definition.damage());
        assertNull(definition.color());

        assertEquals(0x55FFFF, LiquidDefinition.parseColor("LIGHT_BLUE"));
        assertEquals(0x3FBF4A, LiquidDefinition.parseColor("0x3FBF4A"));
        assertEquals(0x3FBF4A, LiquidDefinition.parseColor("3FBF4A"));
        assertThrows(IllegalArgumentException.class,
                () -> LiquidDefinition.parseColor("not-a-color"));

        YamlConfiguration invalid = new YamlConfiguration();
        invalid.set("base", "milk");
        assertThrows(IllegalArgumentException.class,
                () -> LiquidType.parse(Key.key("testpack", "milk"), invalid));
    }

    @Test
    void bucketItemsMapToTheirLiquidAndTheMapCanBeRebuilt() {
        LiquidBucketRegistry.clear();
        LiquidBucketRegistry.register(Key.key("testpack", "acid_bucket"),
                Key.key("testpack", "acid"));

        assertEquals(Key.key("testpack", "acid"),
                LiquidBucketRegistry.liquid(Key.key("testpack", "acid_bucket")));
        assertEquals(Key.key("testpack", "acid_bucket"),
                LiquidBucketRegistry.item(Key.key("testpack", "acid")));
        assertNull(LiquidBucketRegistry.liquid(Key.key("testpack", "other")));
        assertTrue(LiquidBucketRegistry.all().containsKey(
                Key.key("testpack", "acid_bucket")));
        LiquidBucketRegistry.clear();
        assertTrue(LiquidBucketRegistry.all().isEmpty());
    }
}
