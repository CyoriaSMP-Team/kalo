package io.kalo.platform.bedrock;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Per-session staging: Bedrock negotiates one pack list per login, so each XUID gets its
 * own archive with a marker naming the source revision it was built from.
 */
class BedrockPackStagerTest {

    @TempDir
    Path temporary;

    private static void writeZip(Path target, Map<String, String> entries) throws IOException {
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(target))) {
            for (Map.Entry<String, String> entry : entries.entrySet()) {
                zip.putNextEntry(new ZipEntry(entry.getKey()));
                zip.write(entry.getValue().getBytes(StandardCharsets.UTF_8));
                zip.closeEntry();
            }
        }
    }

    private static Map<String, String> sourceEntries() {
        Map<String, String> entries = new LinkedHashMap<>();
        entries.put("manifest.json", "{\"format_version\":2}");
        entries.put("textures/items/ruby.png", "not-really-a-png");
        entries.put("entity/chair.entity.json", "{\"format_version\":\"1.10.0\"}");
        return entries;
    }

    private static String entry(Path archive, String name) throws IOException {
        try (ZipFile zip = new ZipFile(archive.toFile())) {
            ZipEntry found = zip.getEntry(name);
            if (found == null) {
                return null;
            }
            try (InputStream input = zip.getInputStream(found)) {
                return new String(input.readAllBytes(), StandardCharsets.UTF_8);
            }
        }
    }

    @Test
    void aSessionArchiveCopiesTheSourceAndNamesItsRevision() throws IOException {
        Path data = temporary.resolve("data");
        Files.createDirectories(data);
        Path source = temporary.resolve("generated.mcpack");
        writeZip(source, sourceEntries());

        Path staged = BedrockPackStager.stage(data, source, "2535412345678901");

        assertEquals(data.resolve("bedrock-packs").resolve("2535412345678901.mcpack"),
                staged);
        assertEquals("{\"format_version\":2}", entry(staged, "manifest.json"));
        assertEquals("not-really-a-png", entry(staged, "textures/items/ruby.png"));
        assertEquals("{\"format_version\":\"1.10.0\"}",
                entry(staged, "entity/chair.entity.json"));

        String marker = entry(staged, BedrockPackStager.SESSION_MARKER);
        String digest = BedrockPackStager.digest(source);
        assertTrue(marker.contains("\"xuid\":\"2535412345678901\""), marker);
        assertTrue(marker.contains("\"source_sha256\":\"" + digest + "\""), marker);
    }

    @Test
    void stagingTheSameSourceTwiceProducesTheSameBytes() throws IOException {
        Path data = temporary.resolve("data");
        Files.createDirectories(data);
        Path source = temporary.resolve("generated.mcpack");
        writeZip(source, sourceEntries());

        Path first = BedrockPackStager.stage(data, source, "42");
        byte[] firstBytes = Files.readAllBytes(first);

        // Remove only the archive, keeping the digest: staging must rebuild it from the
        // same source and produce byte-identical output (timestamp-0, sorted entries).
        Files.delete(first);
        Path second = BedrockPackStager.stage(data, source, "42");

        assertArrayEquals(firstBytes, Files.readAllBytes(second));
        try (ZipFile zip = new ZipFile(second.toFile())) {
            assertEquals(0L, zip.getEntry("manifest.json").getTime());
        }
    }

    @Test
    void anUnchangedSourceIsNotRebuiltEvenIfTheCopyWasTouched() throws IOException {
        Path data = temporary.resolve("data");
        Files.createDirectories(data);
        Path source = temporary.resolve("generated.mcpack");
        writeZip(source, sourceEntries());

        Path staged = BedrockPackStager.stage(data, source, "7");
        Files.writeString(staged, "tampered");

        Path again = BedrockPackStager.stage(data, source, "7");

        assertEquals(staged, again);
        assertEquals("tampered", Files.readString(again),
                "the digest file says this session is current, so staging must not rewrite it");
    }

    @Test
    void aChangedSourceRebuildsTheSessionArchive() throws IOException {
        Path data = temporary.resolve("data");
        Files.createDirectories(data);
        Path source = temporary.resolve("generated.mcpack");
        Map<String, String> entries = sourceEntries();
        writeZip(source, entries);
        Path staged = BedrockPackStager.stage(data, source, "99");
        String oldDigest = BedrockPackStager.digest(source);
        assertNull(entry(staged, "animations/new.animation.json"));

        entries.put("animations/new.animation.json", "{\"format_version\":\"1.8.0\"}");
        writeZip(source, entries);
        Path rebuilt = BedrockPackStager.stage(data, source, "99");

        String newDigest = BedrockPackStager.digest(source);
        assertNotEquals(oldDigest, newDigest);
        assertEquals("{\"format_version\":\"1.8.0\"}",
                entry(rebuilt, "animations/new.animation.json"));
        assertTrue(entry(rebuilt, BedrockPackStager.SESSION_MARKER).contains(newDigest));
    }

    @Test
    void anUnsafeXuidCannotEscapeThePackDirectory() {
        assertEquals("2535412345678901", BedrockPackStager.key("2535412345678901"));
        assertEquals("abc_-1", BedrockPackStager.key("abc_-1"));
        assertEquals("unknown", BedrockPackStager.key("..."));

        String unsafe = BedrockPackStager.key("12/34 ../x");
        assertTrue(unsafe.startsWith("1234x-"), unsafe);
        assertFalse(unsafe.contains("/"));
        assertFalse(unsafe.contains("."));
        assertNotEquals(BedrockPackStager.key("1234x"), unsafe,
                "two raw XUIDs must not share one staged file");
    }

    @Test
    void perPlayerOverlaysReplacePackFilesAndTriggerARebuild() throws IOException {
        Path data = temporary.resolve("data");
        Files.createDirectories(data);
        Path source = temporary.resolve("generated.mcpack");
        Map<String, String> entries = sourceEntries();
        entries.put("textures/entity/testpack_body_wave_emote_t0.png", "global-skin");
        writeZip(source, entries);

        Path first = BedrockPackStager.stage(data, source, "skin-1",
                Map.of("textures/entity/testpack_body_wave_emote_t0.png",
                        "player-one".getBytes(StandardCharsets.UTF_8)));
        assertEquals("player-one",
                entry(first, "textures/entity/testpack_body_wave_emote_t0.png"));

        Path second = BedrockPackStager.stage(data, source, "skin-1",
                Map.of("textures/entity/testpack_body_wave_emote_t0.png",
                        "player-two".getBytes(StandardCharsets.UTF_8)));
        assertEquals("player-two",
                entry(second, "textures/entity/testpack_body_wave_emote_t0.png"),
                "a changed overlay must rebuild even when the source is unchanged");
    }

    @Test
    void oneSessionCannotReceiveAnotherSessionsFilteredContent() throws IOException {
        Path data = temporary.resolve("data");
        Files.createDirectories(data);
        Path source = temporary.resolve("generated.mcpack");
        Map<String, String> entries = sourceEntries();
        entries.put("models/entity/testpack_ruby.geo.json", "ruby-geometry");
        entries.put("textures/items/testpack_ruby.png", "ruby-texture");
        entries.put("models/entity/other_chair.geo.json", "chair-geometry");
        entries.put("textures/blocks/other_chair.png", "chair-texture");
        entries.put(SessionContentManifest.PATH, """
                {"format_version":1,"entries":{
                  "models/entity/testpack_ruby.geo.json":"testpack:ruby",
                  "textures/items/testpack_ruby.png":"testpack:ruby",
                  "models/entity/other_chair.geo.json":"other:chair",
                  "textures/blocks/other_chair.png":"other:chair"
                }}
                """);
        writeZip(source, entries);
        SessionContentPolicy policy = SessionContentPolicy.of(Map.of(
                "ruby-session", Set.of("testpack"),
                "chair-session", Set.of("other")));

        Path ruby = BedrockPackStager.stage(data, source, "ruby-session", Map.of(), policy);
        Path chair = BedrockPackStager.stage(data, source, "chair-session", Map.of(), policy);

        assertEquals("ruby-geometry", entry(ruby, "models/entity/testpack_ruby.geo.json"));
        assertEquals("ruby-texture", entry(ruby, "textures/items/testpack_ruby.png"));
        assertNull(entry(ruby, "models/entity/other_chair.geo.json"));
        assertNull(entry(ruby, "textures/blocks/other_chair.png"));

        assertEquals("chair-geometry", entry(chair, "models/entity/other_chair.geo.json"));
        assertEquals("chair-texture", entry(chair, "textures/blocks/other_chair.png"));
        assertNull(entry(chair, "models/entity/testpack_ruby.geo.json"));
        assertNull(entry(chair, "textures/items/testpack_ruby.png"));

        assertEquals("{\"format_version\":2}", entry(ruby, "manifest.json"),
                "shared files must survive filtering");
        assertEquals("not-really-a-png", entry(ruby, "textures/items/ruby.png"));

        JsonObject rubyManifest = JsonParser.parseString(
                entry(ruby, SessionContentManifest.PATH)).getAsJsonObject()
                .getAsJsonObject("entries");
        assertTrue(rubyManifest.has("models/entity/testpack_ruby.geo.json"));
        assertFalse(rubyManifest.has("models/entity/other_chair.geo.json"));
    }

    @Test
    void anOverlayForFilteredContentIsNotAdded() throws IOException {
        Path data = temporary.resolve("data");
        Files.createDirectories(data);
        Path source = temporary.resolve("generated.mcpack");
        Map<String, String> entries = sourceEntries();
        entries.put("textures/entity/testpack_body_emote_t0.png", "global-skin");
        entries.put(SessionContentManifest.PATH, """
                {"format_version":1,"entries":{
                  "textures/entity/testpack_body_emote_t0.png":"testpack:body_emote"
                }}
                """);
        writeZip(source, entries);

        Path staged = BedrockPackStager.stage(data, source, "other-session",
                Map.of("textures/entity/testpack_body_emote_t0.png",
                        "player-skin".getBytes(StandardCharsets.UTF_8)),
                SessionContentPolicy.of(Map.of("other-session", Set.of("other"))));

        assertNull(entry(staged, "textures/entity/testpack_body_emote_t0.png"),
                "a filtered content's skin overlay must not leak into the session");
    }

    @Test
    void permissionEntitlementsFilterEachSessionIndependently() throws IOException {
        Path data = temporary.resolve("data");
        Files.createDirectories(data);
        Path source = temporary.resolve("generated.mcpack");
        Map<String, String> entries = sourceEntries();
        entries.put("models/entity/testpack_ruby.geo.json", "ruby-geometry");
        entries.put("models/entity/testpack_plain.geo.json", "plain-geometry");
        entries.put(SessionContentManifest.PATH, """
                {"format_version":1,"entries":{
                  "models/entity/testpack_ruby.geo.json":"testpack:ruby",
                  "models/entity/testpack_plain.geo.json":"testpack:plain"
                }}
                """);
        writeZip(source, entries);
        SessionContentPolicy policy = SessionContentPolicy.of(
                Map.of(),
                Map.of("testpack:ruby", "kalo.content.ruby"));

        Path allowed = BedrockPackStager.stage(data, source, "allowed-session",
                Map.of(), policy, node -> node.equals("kalo.content.ruby"));
        Path denied = BedrockPackStager.stage(data, source, "denied-session",
                Map.of(), policy, node -> false);

        assertEquals("ruby-geometry",
                entry(allowed, "models/entity/testpack_ruby.geo.json"));
        assertNull(entry(denied, "models/entity/testpack_ruby.geo.json"),
                "permission-denied content must not reach that session");
        assertEquals("plain-geometry",
                entry(denied, "models/entity/testpack_plain.geo.json"));
        assertNotEquals(entry(allowed, BedrockPackStager.SESSION_MARKER),
                entry(denied, BedrockPackStager.SESSION_MARKER),
                "the permission outcome is part of the session digest");

        Path artifacts = Path.of("build", "reports", "entitlements");
        Files.createDirectories(artifacts);
        Files.copy(allowed, artifacts.resolve("allowed-session.mcpack"),
                java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        Files.copy(denied, artifacts.resolve("denied-session.mcpack"),
                java.nio.file.StandardCopyOption.REPLACE_EXISTING);
    }

    @Test
    void anUnlistedSessionStillReceivesAllContent() throws IOException {
        Path data = temporary.resolve("data");
        Files.createDirectories(data);
        Path source = temporary.resolve("generated.mcpack");
        Map<String, String> entries = sourceEntries();
        entries.put("models/entity/testpack_ruby.geo.json", "ruby-geometry");
        entries.put("models/entity/other_chair.geo.json", "chair-geometry");
        entries.put(SessionContentManifest.PATH, """
                {"format_version":1,"entries":{
                  "models/entity/testpack_ruby.geo.json":"testpack:ruby",
                  "models/entity/other_chair.geo.json":"other:chair"
                }}
                """);
        writeZip(source, entries);
        SessionContentPolicy policy = SessionContentPolicy.of(
                Map.of("ruby-session", Set.of("testpack")));

        Path unlisted = BedrockPackStager.stage(data, source, "unknown-session",
                Map.of(), policy);

        assertEquals("ruby-geometry", entry(unlisted, "models/entity/testpack_ruby.geo.json"));
        assertEquals("chair-geometry", entry(unlisted, "models/entity/other_chair.geo.json"));
    }

    @Test
    void changingThePolicyRebuildsTheSameSession() throws IOException {
        Path data = temporary.resolve("data");
        Files.createDirectories(data);
        Path source = temporary.resolve("generated.mcpack");
        Map<String, String> entries = sourceEntries();
        entries.put("models/entity/testpack_ruby.geo.json", "ruby-geometry");
        entries.put("models/entity/other_chair.geo.json", "chair-geometry");
        entries.put(SessionContentManifest.PATH, """
                {"format_version":1,"entries":{
                  "models/entity/testpack_ruby.geo.json":"testpack:ruby",
                  "models/entity/other_chair.geo.json":"other:chair"
                }}
                """);
        writeZip(source, entries);

        Path ruby = BedrockPackStager.stage(data, source, "one", Map.of(),
                SessionContentPolicy.of(Map.of("one", Set.of("testpack"))));
        assertEquals("ruby-geometry", entry(ruby, "models/entity/testpack_ruby.geo.json"));
        assertNull(entry(ruby, "models/entity/other_chair.geo.json"));

        Path chair = BedrockPackStager.stage(data, source, "one", Map.of(),
                SessionContentPolicy.of(Map.of("one", Set.of("other"))));
        assertNull(entry(chair, "models/entity/testpack_ruby.geo.json"));
        assertEquals("chair-geometry", entry(chair, "models/entity/other_chair.geo.json"));
    }

    @Test
    void aSessionDigestCoversTheSourceAndThePerPlayerOverlay() throws IOException {
        Path source = temporary.resolve("generated.mcpack");
        writeZip(source, sourceEntries());
        Map<String, byte[]> overlay = Map.of(
                "textures/entity/testpack_body_wave_emote_t0.png", new byte[]{1, 2, 3});

        String base = BedrockPackStager.sessionDigest(source, Map.of());
        String skinned = BedrockPackStager.sessionDigest(source, overlay);

        assertNotEquals(base, skinned);
        assertEquals(base, BedrockPackStager.sessionDigest(source, Map.of()));
        assertEquals(BedrockPackStager.digest(source) + " none", base);
    }

    @Test
    void aServedDigestDecidesWhetherASessionIsStale() throws IOException {
        Path data = temporary.resolve("data");
        Files.createDirectories(data);
        Path source = temporary.resolve("generated.mcpack");
        writeZip(source, sourceEntries());
        String digest = BedrockPackStager.sessionDigest(source, Map.of());
        BedrockPackStager.forget("stale-test");

        assertNull(BedrockPackStager.servedDigest("stale-test"));
        assertTrue(BedrockPackStager.isStale(data, source, "stale-test"));

        BedrockPackStager.markServed("stale-test", digest);
        assertEquals(digest, BedrockPackStager.servedDigest("stale-test"));
        assertFalse(BedrockPackStager.isStale(data, source, "stale-test"));
        assertTrue(BedrockPackStager.isStale(data, source, "stale-test",
                Map.of("textures/entity/testpack_body_wave_emote_t0.png",
                        new byte[]{1, 2, 3})),
                "a changed skin makes the served pack stale");

        BedrockPackStager.forget("stale-test");
        assertNull(BedrockPackStager.servedDigest("stale-test"));
    }
}
