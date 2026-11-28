package io.kalo.platform.bedrock;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SessionContentPolicyTest {

    @Test
    void anUnlistedSessionReceivesEverything() {
        SessionContentPolicy policy = SessionContentPolicy.of(
                Map.of("123", Set.of("testpack")));

        assertTrue(policy.allows("456", "testpack:ruby"));
        assertTrue(policy.allows("456", "other:chair"));
        assertTrue(policy.allows("456", null));
    }

    @Test
    void aListedSessionReceivesOnlyItsNamespacesOrFullKeys() {
        SessionContentPolicy policy = SessionContentPolicy.of(Map.of(
                "123", Set.of("testpack", "other:chair")));

        assertTrue(policy.allows("123", "testpack:ruby"));
        assertTrue(policy.allows("123", "testpack:ruby_sword"));
        assertTrue(policy.allows("123", "other:chair"));
        assertFalse(policy.allows("123", "other:table"));
        assertTrue(policy.allows("123", null), "shared pack files are not content");
    }

    @Test
    void anEmptyListSendsOnlySharedFilesAndStarSendsEverything() {
        SessionContentPolicy empty = SessionContentPolicy.of(Map.of("123", Set.of()));
        assertFalse(empty.allows("123", "testpack:ruby"));
        assertTrue(empty.allows("123", null));

        SessionContentPolicy all = SessionContentPolicy.of(Map.of("123", Set.of("*")));
        assertTrue(all.allows("123", "testpack:ruby"));
        assertTrue(all.allows("123", "anything:else"));
    }

    @Test
    void permissionNodesSeparateSessions() {
        SessionContentPolicy policy = SessionContentPolicy.of(
                Map.of("allowed", Set.of("testpack")),
                Map.of("testpack:ruby", "kalo.content.ruby"));

        assertTrue(policy.allows("allowed", "testpack:ruby",
                node -> node.equals("kalo.content.ruby")));
        assertFalse(policy.allows("denied", "testpack:ruby", node -> false));
        assertTrue(policy.allows("denied", "testpack:plain", node -> false),
                "content without a permission node stays available");
        assertFalse(policy.allows("allowed", "other:ruby",
                        node -> node.equals("kalo.content.ruby")),
                "the session allowlist still applies");

        assertNotEquals(
                policy.fingerprint("allowed", node -> true),
                policy.fingerprint("denied", node -> false));
    }

    @Test
    void permissionNodesAreParsedFromConfig() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("enabled", true);
        config.set("permissions.testpack:ruby", "kalo.content.ruby");
        config.set("permissions.testpack:*", "kalo.content.testpack");

        SessionContentPolicy policy = SessionContentPolicy.fromConfig(config);

        assertTrue(policy.allows("any", "testpack:ruby",
                node -> node.equals("kalo.content.ruby")));
        assertFalse(policy.allows("any", "testpack:ruby", node -> false));
        assertTrue(policy.allows("any", "testpack:plain",
                node -> node.equals("kalo.content.testpack")));
    }

    @Test
    void configBuildsThePerXuidPolicy() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("enabled", true);
        config.set("sessions.123", List.of("testpack"));
        config.set("sessions.456", List.of("other:chair"));

        SessionContentPolicy policy = SessionContentPolicy.fromConfig(config);

        assertTrue(policy.allows("123", "testpack:ruby"));
        assertFalse(policy.allows("123", "other:chair"));
        assertTrue(policy.allows("456", "other:chair"));
        assertFalse(policy.allows("456", "other:table"));
        assertTrue(policy.allows("789", "other:table"));
    }

    @Test
    void aDisabledPolicyAllowsEverything() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("enabled", false);
        config.set("sessions.123", List.of("testpack"));

        SessionContentPolicy policy = SessionContentPolicy.fromConfig(config);

        assertTrue(policy.allows("123", "other:chair"));
    }

    @Test
    void theFingerprintChangesWhenTheSessionAllowlistChanges() {
        SessionContentPolicy one = SessionContentPolicy.of(Map.of("123", Set.of("testpack")));
        SessionContentPolicy two = SessionContentPolicy.of(Map.of("123", Set.of("other")));

        assertNotEquals(one.fingerprint("123"), two.fingerprint("123"));
        assertEquals(one.fingerprint("123"),
                SessionContentPolicy.of(Map.of("123", Set.of("testpack"))).fingerprint("123"));
        assertEquals("all", SessionContentPolicy.all().fingerprint("123"));
    }
}
