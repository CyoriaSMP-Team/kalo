package io.kalo.platform.java;

import io.kalo.model.animation.ModelAnimations;
import net.kyori.adventure.key.Key;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Java model lookup for vehicle and emote display rigs. */
public final class RigModelRegistry {

    public enum Kind {
        VEHICLE,
        EMOTE
    }

    private static final Map<Key, Piece> PIECES = new ConcurrentHashMap<>();

    private RigModelRegistry() {
    }

    public record Piece(
            @NotNull Key contentKey,
            @NotNull Kind kind,
            @NotNull Key modelKey,
            double scale,
            double yOffset,
            @NotNull List<ModelAnimations.Clip> animations
    ) {
        public Piece {
            animations = List.copyOf(animations);
        }
    }

    public static void clear() {
        PIECES.clear();
    }

    public static void put(@NotNull Piece piece) {
        PIECES.put(piece.contentKey(), piece);
    }

    public static @Nullable Piece get(@NotNull Key contentKey) {
        return PIECES.get(contentKey);
    }

    public static @NotNull java.util.Collection<Piece> all() {
        return List.copyOf(PIECES.values());
    }
}
