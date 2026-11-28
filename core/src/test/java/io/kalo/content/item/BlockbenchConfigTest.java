package io.kalo.content.item;

import io.kalo.content.block.BlockConfigSchema;
import io.kalo.content.block.BlockType;
import io.kalo.content.block.definition.BlockModelDefinition;
import io.kalo.content.item.definition.ItemDefinition;
import io.kalo.content.item.definition.ModelDefinition;
import net.kyori.adventure.key.Key;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BlockbenchConfigTest {

    @Test
    void anItemModelCanPointAtABlockbenchSourceWithTextureOverrides() {
        YamlConfiguration yaml = yaml("""
                model:
                  blockbench: models/item/ruby_sword.bbmodel
                  textures:
                    "0": item/ruby
                    blade: item/ruby_blade
                """);

        assertTrue(new ItemConfigSchema().validate(yaml).isSuccess());
        ItemDefinition definition = ItemType.parseDefinition(
                Key.key("testpack", "ruby_sword"), yaml);

        ModelDefinition.Blockbench model = assertInstanceOf(
                ModelDefinition.Blockbench.class, definition.model());
        assertEquals(Key.key("testpack", "models/item/ruby_sword.bbmodel"), model.source());
        assertEquals(Key.key("testpack", "item/ruby"), model.textures().get("0"));
        assertEquals(Key.key("testpack", "item/ruby_blade"), model.textures().get("blade"));
    }

    @Test
    void aBlockbenchModelMustNotShareTheModelSectionWithAnotherSource() {
        YamlConfiguration item = yaml("""
                model:
                  sprite: item/ruby
                  blockbench: models/item/ruby.bbmodel
                """);
        assertFalse(new ItemConfigSchema().validate(item).isSuccess());

        YamlConfiguration block = yaml("""
                model:
                  cube_all: block/ruby
                  blockbench: models/block/ruby.bbmodel
                """);
        assertFalse(new BlockConfigSchema().validate(block).isSuccess());
    }

    @Test
    void aBlockbenchBlockSourceIsParsedLikeAnyOtherBlockModel() {
        YamlConfiguration yaml = yaml("""
                model:
                  blockbench: models/block/chair.bbmodel
                  textures:
                    "0": block/chair
                """);

        assertTrue(new BlockConfigSchema().validate(yaml).isSuccess());
        var definition = BlockType.parseDefinition(Key.key("testpack", "chair"), yaml);
        BlockModelDefinition.Blockbench model = assertInstanceOf(
                BlockModelDefinition.Blockbench.class, definition.model());
        assertEquals(Key.key("testpack", "models/block/chair.bbmodel"), model.source());
        assertEquals(Key.key("testpack", "block/chair"), model.textures().get("0"));
    }

    @Test
    void aNonBlockbenchExtensionIsRejectedAtParseTime() {
        YamlConfiguration yaml = yaml("""
                model:
                  blockbench: models/item/ruby.json
                """);

        assertTrue(new ItemConfigSchema().validate(yaml).isSuccess(),
                "the schema accepts the key; the parser rejects the wrong file type");
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                () -> ItemType.parseDefinition(Key.key("testpack", "ruby"), yaml));
    }

    @Test
    void aPlaceSectionIsParsedForABlockbenchItem() {
        YamlConfiguration yaml = yaml("""
                model:
                  blockbench: models/item/chair.bbmodel
                place:
                  scale: 1.5
                  width: 2
                  height: 3
                  seat: 0.5
                  light: 12
                  idle: spin
                  interact: swing
                """);

        assertTrue(new ItemConfigSchema().validate(yaml).isSuccess());
        ItemDefinition definition = ItemType.parseDefinition(
                Key.key("testpack", "chair"), yaml);
        var placement = definition.placement();
        assertTrue(placement != null);
        assertEquals(1.5, placement.scale(), 1e-9);
        assertEquals(2.0, placement.width(), 1e-9);
        assertEquals(3.0, placement.height(), 1e-9);
        assertEquals(0.5, placement.seat(), 1e-9);
        assertEquals(12, placement.light());
        assertEquals("spin", placement.idleAnimation());
        assertEquals("swing", placement.interactAnimation());
    }

    @Test
    void placeRequiresABlockbenchModel() {
        YamlConfiguration yaml = yaml("""
                model:
                  sprite: item/ruby
                place:
                  scale: 1
                """);

        assertTrue(new ItemConfigSchema().validate(yaml).isSuccess());
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                () -> ItemType.parseDefinition(Key.key("testpack", "ruby"), yaml));
    }

    @Test
    void storageAndStatesAreParsed() {
        YamlConfiguration yaml = yaml("""
                model:
                  blockbench: models/item/lamp.bbmodel
                place:
                  storage:
                    rows: 3
                    title: Cabinet
                  states:
                    - scale: 2.0
                      light: 15
                      solid: true
                      model: lamp_on
                      sound: minecraft:block.lever.click
                """);

        assertTrue(new ItemConfigSchema().validate(yaml).isSuccess());
        var placement = ItemType.parseDefinition(Key.key("testpack", "lamp"), yaml)
                .placement();
        assertTrue(placement != null);
        assertEquals(3, placement.storage().rows());
        assertEquals("Cabinet", placement.storage().title());
        assertEquals(1, placement.states().size());
        var state = placement.states().getFirst();
        assertEquals(2.0, state.scale(), 1e-9);
        assertEquals(15, state.light());
        assertEquals(Boolean.TRUE, state.solid());
        assertEquals(Key.key("testpack", "lamp_on"), state.model());
        assertEquals("minecraft:block.lever.click", state.sound());
    }

    private static YamlConfiguration yaml(String content) {
        YamlConfiguration config = new YamlConfiguration();
        try {
            config.loadFromString(content);
        } catch (Exception error) {
            throw new AssertionError(error);
        }
        return config;
    }
}
