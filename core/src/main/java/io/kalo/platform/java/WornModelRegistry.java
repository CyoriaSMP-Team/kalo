package io.kalo.platform.java;

import io.kalo.content.animation.RigAnimation;
import io.kalo.model.animation.ModelAnimations;
import net.kyori.adventure.key.Key;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Java-side lookup for armor pieces that carry a Blockbench worn model.
 *
 * <p>The compiler writes a Java model per piece and records it here; the worn-armor
 * listener uses the model key to build the display entity's item payload. Rebuilt on every
 * pack generation, so it always matches the pack on disk.</p>
 */
public final class WornModelRegistry {

    private static final Map<Key, Piece> PIECES = new ConcurrentHashMap<>();

    private WornModelRegistry() {
    }

    public record Piece(
            @NotNull Key contentKey,
            @NotNull Key modelKey,
            @NotNull List<ModelAnimations.Clip> animations,
            @NotNull RigAnimation animation
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
