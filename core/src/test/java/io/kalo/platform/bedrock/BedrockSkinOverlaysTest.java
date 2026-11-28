package io.kalo.platform.bedrock;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BedrockSkinOverlaysTest {

    @AfterEach
    void clear() {
        BedrockSkinOverlays.clear();
    }

    @Test
    void overlaysAreScopedToAnXuidAndDefensivelyCopied() {
        byte[] skin = {1, 2, 3};
        BedrockSkinOverlays.put("123", "textures/entity/testpack_body_wave_emote_t0.png",
                skin);
        skin[0] = 9;

        byte[] resolved = BedrockSkinOverlays.forXuid("123")
                .get("textures/entity/testpack_body_wave_emote_t0.png");
        assertArrayEquals(new byte[]{1, 2, 3}, resolved);
        assertTrue(BedrockSkinOverlays.forXuid("456").isEmpty());

        resolved[0] = 7;
        assertArrayEquals(new byte[]{1, 2, 3}, BedrockSkinOverlays.forXuid("123")
                .get("textures/entity/testpack_body_wave_emote_t0.png"));
    }

    @Test
    void forgettingASessionDropsOnlyThatXuid() {
        BedrockSkinOverlays.put("123", "a.png", new byte[]{1, 2, 3});
        BedrockSkinOverlays.put("456", "a.png", new byte[]{4, 5, 6});

        BedrockSkinOverlays.forget("123");

        assertTrue(BedrockSkinOverlays.forXuid("123").isEmpty());
        assertArrayEquals(new byte[]{4, 5, 6},
                BedrockSkinOverlays.forXuid("456").get("a.png"));
    }
}
