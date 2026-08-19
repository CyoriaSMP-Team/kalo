package io.kalo.content.block;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BlockConfigSchemaTest {

    @Test
    void acceptsVirtualMode() {
        var result = new BlockConfigSchema().validate(yaml("""
                model:
                  cube_all: block/ruby
                java:
                  mode: virtual
                """));

        assertTrue(result.isSuccess(), result.getErrors().toString());
    }

    @Test
    void rejectsUnknownModeAndCarrier() {
        var result = new BlockConfigSchema().validate(yaml("""
                model:
                  cube_all: block/ruby
                java:
                  mode: hologram
                  carrier: MISSING
                """));

        assertFalse(result.isSuccess());
        assertTrue(result.getErrors().stream().anyMatch(error -> error.contains("java.mode")));
        assertTrue(result.getErrors().stream().anyMatch(error -> error.contains("java.carrier")));
    }

    private static YamlConfiguration yaml(String content) {
        YamlConfiguration config = new YamlConfiguration();
        try {
            config.loadFromString(content);
        } catch (Exception e) {
            throw new AssertionError(e);
        }
        return config;
    }
}
