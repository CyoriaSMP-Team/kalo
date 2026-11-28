package io.kalo.platform.bedrock;

import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GeyserBlockMappingsTest {

    private static final String STATE = "instrument=harp,note=0,powered=true";

    @Test
    void keysAreBlockIdsAndStatesGoUnderStateOverrides() {
        JsonObject mapping = GeyserBlockMappings.encode(List.of(chest(STATE)));

        JsonObject blocks = mapping.getAsJsonObject("blocks");
        assertEquals(1, blocks.keySet().size());
        assertTrue(blocks.has("minecraft:note_block"));
        assertFalse(blocks.keySet().stream().anyMatch(key -> key.contains("[")));

        JsonObject entry = blocks.getAsJsonObject("minecraft:note_block");
        assertEquals("plny_chest", entry.get("name").getAsString());
        assertTrue(entry.get("only_override_states").getAsBoolean());
        assertTrue(entry.getAsJsonObject("state_overrides").has(STATE));
    }

    @Test
    void statesOfOneJavaBlockShareOneEntry() {
        String second = "instrument=harp,note=1,powered=true";
        JsonObject mapping = GeyserBlockMappings.encode(
                List.of(chest(STATE), chest(second)));

        JsonObject overrides = mapping.getAsJsonObject("blocks")
                .getAsJsonObject("minecraft:note_block")
                .getAsJsonObject("state_overrides");
        assertEquals(2, overrides.keySet().size());
        assertTrue(overrides.has(STATE));
        assertTrue(overrides.has(second));
    }

    @Test
    void statelessBlockHasNoOverrides() {
        BedrockBlockRegistration block = new BedrockBlockRegistration(
                "kalo:ruby_block", "geyser_custom:ruby_block", "minecraft:barrier",
                "geometry.kalo.ruby_block", null, 2.0f, Map.of("*", "ruby_block"));

        JsonObject entry = GeyserBlockMappings.encode(List.of(block))
                .getAsJsonObject("blocks").getAsJsonObject("minecraft:barrier");

        assertFalse(entry.has("state_overrides"));
        assertFalse(entry.has("only_override_states"));
    }

    @Test
    void malformedIdentifierIsIgnoredInsteadOfBreakingTheFile() {
        JsonObject mapping = GeyserBlockMappings.encode(List.of(chest(STATE), chest(null)));
        assertEquals(1, mapping.getAsJsonObject("blocks").keySet().size());
    }

    private static BedrockBlockRegistration chest(String state) {
        return new BedrockBlockRegistration(
                "plny_christmas_2025_set:chest",
                "geyser_custom:plny_chest",
                state == null ? null : "minecraft:note_block[" + state + "]",
                "geometry.kalo.plny_christmas_2025_set_chest",
                "Christmas Chest",
                1.5f,
                Map.of("1", "plny_christmas_2025_set_chest_1", "particle", "plny_chest_particle"));
    }
}
