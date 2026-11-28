package io.kalo.platform.java;

import io.kalo.content.hud.definition.HudDefinition;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class HudRuntimeTest {

    @Test
    void aHudRendersItsPrivateUseGlyph() {
        HudDefinition definition = new HudDefinition(Key.key("testpack", "mana"),
                Key.key("testpack", "hud/mana"), HudDefinition.Slot.ACTION_BAR,
                0xE100, 32, 24, Key.key("minecraft", "default"));

        assertEquals("\uE100", PlainTextComponentSerializer.plainText()
                .serialize(HudRuntime.overlay(definition)));
    }

    @Test
    void aJavaOffsetIsASpacePrefixWhileBedrockKeepsTheGlyph() {
        HudDefinition definition = new HudDefinition(Key.key("testpack", "mana"),
                Key.key("testpack", "hud/mana"), HudDefinition.Slot.ACTION_BAR,
                0xE100, 32, 24, Key.key("minecraft", "default"), -8);

        assertEquals(definition.offsetGlyph() + "\uE100",
                PlainTextComponentSerializer.plainText()
                        .serialize(HudRuntime.overlay(definition)));
        assertEquals("\uE100", PlainTextComponentSerializer.plainText()
                .serialize(HudRuntime.overlay(definition, true)));
    }
}
