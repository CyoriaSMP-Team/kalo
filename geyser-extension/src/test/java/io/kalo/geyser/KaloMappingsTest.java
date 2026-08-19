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
        assertEquals("native", entry.javaMode());
    }

    @Test
    void aMappingFileWithNoBlocksIsNotAnError() throws IOException {
        // Item-only packs are normal, and so are packs that have not been generated yet.
        assertTrue(parse("{\"format_version\":2,\"items\":{}}").isEmpty());
    }

    @Test
    void carrierStateMayBeAbsent() throws IOException {
        // The plugin writes the state only once it has allocated one.
        KaloMappings mappings = parse("""
                {"kalo:blocks":[{"java_key":"a:b","bedrock_identifier":"a:b"}]}
                """);

        assertNull(mappings.blocks().get(0).javaCarrierState());
        assertEquals("native", mappings.blocks().get(0).javaMode());
    }

    @Test
    void virtualModeIsPreservedWithoutAJavaCarrierState() throws IOException {
        KaloMappings mappings = parse("""
                {"kalo:blocks":[{"java_key":"a:b","bedrock_identifier":"a:b","java_mode":"virtual"}]}
                """);

        assertNull(mappings.blocks().get(0).javaCarrierState());
        assertEquals("virtual", mappings.blocks().get(0).javaMode());
    }

    @Test
    void anEntryMissingItsIdentifiersIsRejectedLoudly() {
        // Silently skipping would leave a block invisible on Bedrock with no explanation.
        assertThrows(IOException.class, () -> parse("""
                {"kalo:blocks":[{"java_key":"a:b"}]}
                """));
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
