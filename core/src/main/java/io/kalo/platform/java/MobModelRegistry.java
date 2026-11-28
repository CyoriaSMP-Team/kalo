package io.kalo.platform.java;

import io.kalo.model.animation.ModelAnimations;
import net.kyori.adventure.key.Key;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Java model lookup for custom mobs, rebuilt with every pack generation. */
public final class MobModelRegistry {

    private static final Map<Key, Piece> PIECES = new ConcurrentHashMap<>();

    private MobModelRegistry() {
    }

    public record Piece(
            @NotNull Key mobKey,
            @NotNull Key modelKey,
            double height,
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
        PIECES.put(piece.mobKey(), piece);
    }

    public static @Nullable Piece get(@NotNull Key mobKey) {
        return PIECES.get(mobKey);
    }

    public static @NotNull java.util.Collection<Piece> all() {
        return List.copyOf(PIECES.values());
    }
}
