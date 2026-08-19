package io.kalo.content.block;

import io.kalo.content.block.definition.BlockCarrier;
import io.kalo.content.block.definition.JavaBlockMode;
import net.kyori.adventure.key.Key;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BlockTypeTest {

    @Test
    void parsesVirtualModeWithoutNeedingACarrier() {
        YamlConfiguration config = yaml("""
                model:
                  cube_all: block/ruby
                java:
                  mode: virtual
                """);

        var definition = BlockType.parseDefinition(Key.key("testpack", "ruby"), config);

        assertEquals(JavaBlockMode.VIRTUAL, definition.java().mode());
        assertEquals(BlockCarrier.NOTE_BLOCK, definition.java().carrier(),
                "the carrier remains a compatibility fallback and is not allocated");
    }

    @Test
    void parsesNativeModeAndItsExplicitCarrier() {
        YamlConfiguration config = yaml("""
                model:
                  cube_all: block/flat
                java:
                  mode: native
                  carrier: TRIPWIRE
                """);

        var definition = BlockType.parseDefinition(Key.key("testpack", "flat"), config);

        assertEquals(JavaBlockMode.NATIVE, definition.java().mode());
        assertEquals(BlockCarrier.TRIPWIRE, definition.java().carrier());
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
