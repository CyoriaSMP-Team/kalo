package io.kalo.platform.bedrock;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GeyserHomeDiscoveryTest {

    @Test
    void findsAProxyGeyserTwoLevelsUp(@TempDir Path temp) throws IOException {
        Path server = Files.createDirectories(temp.resolve("data/minecraft"));
        Path home = Files.createDirectories(
                temp.resolve("servers/velocity/plugins/Geyser-Velocity"));
        makeGeyserHome(home);

        assertEquals(home.toAbsolutePath().normalize(),
                GeyserHomeDiscovery.locate(server).orElseThrow());
    }

    @Test
    void rejectsADirectoryThatIsNotAGeyserHome(@TempDir Path temp) throws IOException {
        Path server = Files.createDirectories(temp.resolve("data/minecraft"));
        Path notGeyser = Files.createDirectories(
                temp.resolve("servers/velocity/plugins/Geyser-Broken"));
        Files.writeString(notGeyser.resolve("config.yml"), "bedrock: {}");

        assertTrue(GeyserHomeDiscovery.locate(server).isEmpty());
    }

    @Test
    void emptyWhenNothingNearby(@TempDir Path temp) throws IOException {
        Path server = Files.createDirectories(temp.resolve("data/minecraft"));
        assertTrue(GeyserHomeDiscovery.locate(server).isEmpty());
    }

    @Test
    void relativeDataFolderStillYieldsAServerRoot() {
        Path dataFolder = Path.of("plugins", "Kalo");
        Path expected = dataFolder.toAbsolutePath().normalize().getParent().getParent();
        assertEquals(expected, GeyserHomeDiscovery.serverRootOf(dataFolder));
    }

    @Test
    void absoluteDataFolderYieldsTheServerRoot(@TempDir Path temp) throws IOException {
        Path dataFolder = Files.createDirectories(temp.resolve("server/plugins/Kalo"));
        assertEquals(temp.resolve("server"), GeyserHomeDiscovery.serverRootOf(dataFolder));
    }

    @Test
    void nullServerDirectoryIsNotAnError() {
        assertTrue(GeyserHomeDiscovery.locate(null).isEmpty());
    }

    private static void makeGeyserHome(Path home) throws IOException {
        Files.writeString(home.resolve("config.yml"), "bedrock:\n  port: 19132\n");
        Files.createDirectories(home.resolve("packs"));
        Files.createDirectories(home.resolve("custom_mappings"));
    }
}
