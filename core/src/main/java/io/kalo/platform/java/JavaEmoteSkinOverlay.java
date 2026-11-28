package io.kalo.platform.java;

import io.kalo.pack.PackFormats;
import io.kalo.pack.PackMeta;
import io.kalo.pack.ResourcePack;
import io.kalo.pack.ResourcePackImpl;
import io.kalo.pack.Writable;
import net.kyori.adventure.key.Key;
import org.jetbrains.annotations.NotNull;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * A tiny per-player Java pack that replaces one body emote's unique texture.
 *
 * <p>It carries only {@code pack.mcmeta} and the skin PNG, so stacking it on top of the
 * global pack cannot disturb any other content. The pack id is stable per player, which
 * lets a later emote replace the previous overlay instead of accumulating packs.</p>
 */
public final class JavaEmoteSkinOverlay {

    private JavaEmoteSkinOverlay() {
    }

    public static @NotNull ResourcePack pack(@NotNull Key emoteKey,
                                             byte @NotNull [] png) {
        ResourcePack overlay = new ResourcePackImpl(PackMeta.of(PackFormats.resolve(),
                "Kalo emote skin"));
        overlay.file(PlayerSkinCache.javaOverlayPath(emoteKey), Writable.bytes(png));
        return overlay;
    }

    public static @NotNull UUID id(@NotNull UUID playerId) {
        return UUID.nameUUIDFromBytes(("kalo-emote-skin:" + playerId)
                .getBytes(StandardCharsets.UTF_8));
    }
}
