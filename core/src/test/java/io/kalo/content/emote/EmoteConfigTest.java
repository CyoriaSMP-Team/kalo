package io.kalo.content.emote;

import io.kalo.content.item.definition.ModelDefinition;
import net.kyori.adventure.key.Key;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EmoteConfigTest {

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
    void aBlockbenchEmoteParsesDurationAndModel() {
        YamlConfiguration yaml = yaml("""
                name: "<yellow>Wave"
                duration: 2.5
                loop: true
                scale: 1.5
                y-offset: 1.0
                model:
                  blockbench: models/emote/wave.bbmodel
                  textures:
                    "0": entity/wave
                rig: player
                skin: entity/emote_skin
                performers:
                  - name: lead
                    offset: {x: 0, y: 0, z: 0}
                  - name: partner
                    offset: {x: 0.8, y: 0, z: 1.0}
                """);

        EmoteDefinition definition = EmoteType.parseDefinition(
                Key.key("testpack", "wave"), yaml);

        assertEquals(2.5, definition.duration(), 1e-9);
        assertTrue(definition.loop());
        assertEquals(1.5, definition.scale(), 1e-9);
        assertEquals(1.0, definition.yOffset(), 1e-9);
        var blockbench = (ModelDefinition.Blockbench) definition.model();
        assertEquals(Key.key("testpack", "models/emote/wave.bbmodel"),
                blockbench.source());
        assertEquals(Key.key("testpack", "entity/wave"), blockbench.textures().get("0"));
        assertEquals(EmoteDefinition.RigMode.PLAYER, definition.rig());
        assertEquals(Key.key("testpack", "entity/emote_skin"), definition.skin());
        assertEquals(2, definition.performers().size());
        assertEquals("partner", definition.performers().get(1).name());
        assertEquals(0.8, definition.performers().get(1).x(), 1e-9);
        assertEquals(1.0, definition.performers().get(1).z(), 1e-9);
    }

    @Test
    void anEmoteWithoutAModelIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> EmoteType.parseDefinition(
                Key.key("testpack", "wave"), yaml("duration: 2")));
    }

    @Test
    void durationScaleAndOffsetAreValidated() {
        assertThrows(IllegalArgumentException.class, () -> new EmoteDefinition(
                Key.key("testpack", "wave"), net.kyori.adventure.text.Component.text("Wave"),
                new ModelDefinition.Blockbench(Key.key("testpack", "wave.bbmodel"),
                        java.util.Map.of()), -1, true, 1, 0, null,
                io.kalo.content.item.definition.BedrockOptions.defaults()));
    }
}
