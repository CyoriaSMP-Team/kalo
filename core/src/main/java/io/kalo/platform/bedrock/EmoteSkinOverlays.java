package io.kalo.platform.bedrock;

import io.kalo.content.emote.Emote;
import io.kalo.content.emote.EmoteDefinition;
import io.kalo.manager.RegistryManager;
import io.kalo.platform.java.PlayerSkinCache;
import org.jetbrains.annotations.NotNull;

import java.nio.file.Path;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

/**
 * Builds the texture overlays a Bedrock session needs at login for player-body emotes.
 *
 * <p>An emote-specific path cannot be known at login time, because the player has not
 * chosen an emote yet. A cached player skin is therefore written to every body-emote
 * texture path; prop emotes are untouched. The overlay is durable because it comes from
 * the disk cache, not from an in-memory session map that is dropped on quit.</p>
 */
public final class EmoteSkinOverlays {

    private EmoteSkinOverlays() {
    }

    public static @NotNull Map<String, byte[]> forPlayer(@NotNull Path dataFolder,
                                                         @NotNull UUID playerId) {
        byte[] skin = PlayerSkinCache.resolve(dataFolder, playerId);
        if (skin == null) {
            return Map.of();
        }
        Map<String, byte[]> overlays = new TreeMap<>();
        try {
            for (Emote emote : RegistryManager.GlobalRegistries.registries().emote()) {
                EmoteDefinition definition = emote.definition();
                if (definition.rig() == EmoteDefinition.RigMode.PLAYER
                        && definition.bedrock().enabled()) {
                    overlays.put(PlayerSkinCache.overlayPath(definition.key()), skin);
                }
            }
        } catch (RuntimeException unavailableRegistry) {
            return Map.of();
        }
        return Map.copyOf(overlays);
    }
}
