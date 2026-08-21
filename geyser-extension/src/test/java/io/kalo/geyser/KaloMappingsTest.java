package io.kalo.geyser;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The extension and the Paper plugin run in different processes and share only this file
 * format, so these tests pin the format rather than any shared class.
 */
class KaloMappingsTest {

    private static KaloMappings parse(String json) throws IOException {
        JsonObject root = JsonParser.parseString(json).getAsJsonObject();
        return KaloMappings.parse(root);
    }

    @Test
    void readsBlockEntriesWrittenByThePlugin() throws IOException {
        KaloMappings mappings = parse("""
                {
                  "format_version": 2,
                  "items": {},
                  "kalo:blocks": [
                    {"java_key":"testpack:ruby_block","bedrock_identifier":"testpack:ruby_block","java_carrier_state":1}
                  ]
                }
                """);

        assertEquals(1, mappings.blocks().size());
        KaloMappings.BlockEntry entry = mappings.blocks().get(0);
        assertEquals("testpack:ruby_block", entry.javaKey());
        assertEquals("testpack:ruby_block", entry.bedrockId());
        assertEquals(1, entry.javaCarrierState());
        assertEquals("minecraft:note_block[instrument=harp,note=0,powered=true]",
                entry.javaIdentifier());
    }

    @Test
    void aMappingFileWithNoBlocksIsNotAnError() throws IOException {
        // Item-only packs are normal, and so are packs that have not been generated yet.
        assertTrue(parse("{\"format_version\":2,\"items\":{}}").isEmpty());
    }

    @Test
    void readsGeyserV2ItemDefinitionsForStandaloneRegistration() throws IOException {
        KaloMappings mappings = parse("""
                {
                  "format_version": 2,
                  "items": {
                    "minecraft:iron_sword": [{
                      "type": "definition",
                      "model": "testpack:ruby_sword",
                      "bedrock_identifier": "testpack:ruby_sword",
                      "display_name": "Ruby Sword",
                      "bedrock_options": {"icon": "testpack_ruby_sword"},
                      "components": {
                        "minecraft:max_stack_size": 1,
                        "minecraft:max_damage": 250,
                        "minecraft:enchantment_glint_override": true
                      }
                    }]
                  }
                }
                """);

        assertEquals(1, mappings.items().size());
        KaloMappings.ItemEntry item = mappings.items().get(0);
        assertEquals("minecraft:iron_sword", item.javaIdentifier());
        assertEquals("testpack:ruby_sword", item.bedrockId());
        assertEquals("testpack:ruby_sword", item.model());
        assertEquals("testpack_ruby_sword", item.icon());
        assertEquals("Ruby Sword", item.displayName());
        assertEquals(1, item.maxStackSize());
        assertEquals(250, item.maxDamage());
        assertTrue(item.enchantmentGlint());
    }

    @Test
    void readsLegacyKaloItemFieldsWithoutLosingTheIconOrDurability() throws IOException {
        KaloMappings mappings = parse("""
                {"format_version":2,"items":{"minecraft:paper":[{
                  "model":"a:b","bedrock_identifier":"a:b","icon":"a_b",
                  "components":{"minecraft:max_stack_size":1,"minecraft:durability":12}
                }]}}
                """);

        assertEquals("a_b", mappings.items().get(0).icon());
        assertEquals(12, mappings.items().get(0).maxDamage());
    }

    @Test
    void rejectsItemDefinitionsGeyserWouldReject() {
        assertThrows(IOException.class, () -> parse("""
                {"format_version":2,"items":{"minecraft:paper":[{
                  "model":"a:b","bedrock_identifier":"a:b",
                  "components":{"minecraft:max_stack_size":64,"minecraft:max_damage":12}
                }]}}
                """));
        assertThrows(IOException.class, () -> parse("""
                {"format_version":2,"items":{"minecraft:paper":{}}}
                """));
    }

    @Test
    void carrierStateMayBeAbsent() throws IOException {
        // The plugin writes the state only once it has allocated one.
        KaloMappings mappings = parse("""
                {"kalo:blocks":[{"java_key":"a:b","bedrock_identifier":"a:b"}]}
                """);

        assertNull(mappings.blocks().get(0).javaCarrierState());
        assertNull(mappings.blocks().get(0).javaIdentifier());
    }

    @Test
    void readsTheExactJavaStateAndVisualMetadataWrittenByThePlugin() throws IOException {
        KaloMappings mappings = parse("""
                {
                  "format_version": 2,
                  "kalo:blocks": [{
                    "java_key": "testpack:chair",
                    "bedrock_identifier": "testpack:chair",
                    "java_carrier_state": 51,
                    "java_identifier": "minecraft:note_block[instrument=basedrum,note=0,powered=true]",
                    "geometry": "geometry.kalo.testpack_chair",
                    "display_name": "Ruby Chair",
                    "hardness": 2.5,
                    "material_instances": {
                      "all": "testpack_chair_all",
                      "legs": "testpack_chair_legs"
                    }
                  }]
                }
                """);

        KaloMappings.BlockEntry entry = mappings.blocks().get(0);
        assertEquals("minecraft:note_block[instrument=basedrum,note=0,powered=true]",
                entry.javaIdentifier());
        assertEquals("geometry.kalo.testpack_chair", entry.geometry());
        assertEquals("Ruby Chair", entry.displayName());
        assertEquals(2.5f, entry.hardness());
        assertEquals("testpack_chair_all", entry.materialInstances().get("all"));
        assertEquals("testpack_chair_legs", entry.materialInstances().get("legs"));
    }

    @Test
    void legacyStateIndexesAreDecodedAtInstrumentBoundaries() throws IOException {
        assertEquals("minecraft:note_block[instrument=basedrum,note=0,powered=false]",
                KaloMappings.noteBlockIdentifier(50));
        assertEquals("minecraft:note_block[instrument=pling,note=24,powered=true]",
                KaloMappings.noteBlockIdentifier(799));
    }

    @Test
    void anEntryMissingItsIdentifiersIsRejectedLoudly() {
        // Silently skipping would leave a block invisible on Bedrock with no explanation.
        assertThrows(IOException.class, () -> parse("""
                {"kalo:blocks":[{"java_key":"a:b"}]}
                """));
    }

    @Test
    void malformedBlockCollectionsAndCarrierIndexesAreRejectedLoudly() {
        assertThrows(IOException.class, () -> parse("{\"kalo:blocks\":{}}"));
        assertThrows(IOException.class, () -> parse("""
                {"kalo:blocks":[{
                  "java_key":"a:b","bedrock_identifier":"a:b","java_carrier_state":800
                }]}
                """));
        assertThrows(IOException.class, () -> parse("""
                {"kalo:blocks":[{
                  "java_key":"a:b","bedrock_identifier":"a:b",
                  "material_instances":{"*":42}
                }]}
                """));
    }

    @Test
    void duplicateOverridesAreRejectedBeforeGeyserSeesThem() {
        assertThrows(IOException.class, () -> parse("""
                {"kalo:blocks":[
                  {"java_key":"a:first","bedrock_identifier":"a:first","java_carrier_state":1},
                  {"java_key":"a:second","bedrock_identifier":"a:second","java_carrier_state":1}
                ]}
                """));
    }

    @Test
    void unsupportedMappingVersionsAreRejected() {
        assertThrows(IOException.class, () -> parse("{\"format_version\":99,\"kalo:blocks\":[]}"));
    }

    @Test
    void aMissingFileYieldsNothingRatherThanFailing(@TempDir Path dir) throws IOException {
        // Geyser can start before the Paper side has ever generated a mapping file.
        assertTrue(KaloMappings.load(dir.resolve("absent.json")).isEmpty());
    }

    @Test
    void readsFromDisk(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("bedrock-mappings.json");
        Files.writeString(file, """
                {"kalo:blocks":[
                  {"java_key":"testpack:oak_chair","bedrock_identifier":"testpack:oak_chair","java_carrier_state":3}
                ]}
                """, StandardCharsets.UTF_8);

        KaloMappings mappings = KaloMappings.load(file);

        assertEquals("testpack:oak_chair", mappings.blocks().get(0).bedrockId());
        assertEquals(3, mappings.blocks().get(0).javaCarrierState());
    }

    @Test
    void aFileThatIsNotAnObjectIsRejected(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("bedrock-mappings.json");
        Files.writeString(file, "[]", StandardCharsets.UTF_8);

        assertThrows(IOException.class, () -> KaloMappings.load(file));
    }
}
