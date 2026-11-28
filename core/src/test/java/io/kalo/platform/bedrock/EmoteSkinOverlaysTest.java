package io.kalo.platform.bedrock;

import io.kalo.TestKalo;
import io.kalo.content.emote.EmoteDefinition;
import io.kalo.content.item.definition.BedrockOptions;
import io.kalo.content.item.definition.ModelDefinition;
import io.kalo.manager.RegistryManager;
import io.kalo.platform.java.PlayerSkinCache;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EmoteSkinOverlaysTest {

    @TempDir
    Path temporary;

    @AfterEach
    void uninstall() {
        TestKalo.uninstall();
    }

    private static byte[] png() {
        byte[] bytes = new byte[32];
        bytes[0] = (byte) 0x89;
        bytes[1] = 'P';
        bytes[2] = 'N';
        bytes[3] = 'G';
        return bytes;
    }

    private static EmoteDefinition definition(String name, EmoteDefinition.RigMode rig) {
        return new EmoteDefinition(Key.key("testpack", name),
                Component.text(name),
                new ModelDefinition.Blockbench(
                        Key.key("testpack", "models/emote/" + name + ".bbmodel"), Map.of()),
                0, true, 1.0, 0.0, null,
                BedrockOptions.defaults(), rig, Key.key("testpack", "entity/emote_skin"));
    }

    private static void register(EmoteDefinition definition) {
        RegistryManager.GlobalRegistries.registries().emote().register(definition.key(),
                entry -> entry.key(definition.key()).definition(definition));
    }

    @Test
    void aCachedSkinOverlaysEveryPlayerBodyEmoteTexture() throws Exception {
        TestKalo.install();
        EmoteDefinition body = definition("body_wave", EmoteDefinition.RigMode.PLAYER);
        EmoteDefinition prop = definition("wave", EmoteDefinition.RigMode.PROP);
        register(body);
        register(prop);
        UUID player = UUID.randomUUID();
        Path file = PlayerSkinCache.file(temporary, player);
        Files.createDirectories(file.getParent());
        Files.write(file, png());

        Map<String, byte[]> overlays = EmoteSkinOverlays.forPlayer(temporary, player);

        assertEquals(1, overlays.size());
        assertArrayEquals(png(), overlays.get(
                "textures/entity/testpack_body_wave_emote_t0.png"));
        assertTrue(!overlays.containsKey("textures/entity/testpack_wave_emote_t0.png"),
                "prop emotes do not use a player skin");
    }

    @Test
    void withoutACachedSkinNoOverlayIsProduced() {
        TestKalo.install();
        register(definition("body_wave", EmoteDefinition.RigMode.PLAYER));

        assertTrue(EmoteSkinOverlays.forPlayer(temporary, UUID.randomUUID()).isEmpty());
    }
}
