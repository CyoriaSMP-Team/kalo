package io.kalo.content.mob;

import io.kalo.content.item.definition.ModelDefinition;
import net.kyori.adventure.key.Key;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.EntityType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MobConfigTest {

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
    void aBlockbenchMobParsesBaseHealthAndModel() {
        YamlConfiguration yaml = yaml("""
                base: zombie
                name: "<red>Ruby Golem"
                health: 40
                width: 0.7
                height: 2.0
                model:
                  blockbench: models/mob/ruby_golem.bbmodel
                  textures:
                    "0": entity/ruby_golem
                animation:
                  idle: walk
                  interact: Open
                """);

        MobDefinition definition = MobType.parseDefinition(
                Key.key("testpack", "ruby_golem"), yaml);

        assertEquals(EntityType.ZOMBIE, definition.base());
        assertEquals(40, definition.health(), 1e-9);
        assertEquals(0.7, definition.width(), 1e-9);
        assertEquals(2.0, definition.height(), 1e-9);
        assertTrue(definition.customModel());
        var blockbench = (ModelDefinition.Blockbench) definition.model();
        assertEquals(Key.key("testpack", "models/mob/ruby_golem.bbmodel"),
                blockbench.source());
        assertEquals(Key.key("testpack", "entity/ruby_golem"),
                blockbench.textures().get("0"));
        assertEquals("walk", definition.animation().idle());
        assertEquals("Open", definition.animation().interact());
    }

    @Test
    void aMobWithoutAModelKeepsTheBaseAppearance() {
        YamlConfiguration yaml = yaml("""
                base: minecraft:cow
                """);

        MobDefinition definition = MobType.parseDefinition(
                Key.key("testpack", "cow"), yaml);

        assertEquals(EntityType.COW, definition.base());
        assertEquals(20, definition.health(), 1e-9);
        assertTrue(!definition.customModel());
    }

    @Test
    void aNonLivingBaseAndANonBlockbenchModelAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> MobType.parseDefinition(
                Key.key("testpack", "boat"), yaml("base: boat")));
        assertThrows(IllegalArgumentException.class, () -> MobType.parseDefinition(
                Key.key("testpack", "mob"), yaml("""
                        base: zombie
                        model:
                          blockbench: models/mob/model.json
                        """)));
    }

    @Test
    void healthAndSizeAreValidated() {
        assertThrows(IllegalArgumentException.class, () -> MobDefinition.builder(
                Key.key("testpack", "mob")).health(0).build());
        assertThrows(IllegalArgumentException.class, () -> MobDefinition.builder(
                Key.key("testpack", "mob")).size(0, 1).build());
    }
}
