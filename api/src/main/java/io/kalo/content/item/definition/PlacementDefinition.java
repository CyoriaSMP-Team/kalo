package io.kalo.content.item.definition;

import net.kyori.adventure.key.Key;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Optional "put the item's model down in the world" behaviour.
 *
 * <p>Kept platform-neutral: it describes size, seat height, light and animation names,
 * not display entities or client entities. The Java runtime uses a display entity; the
 * Bedrock compiler uses a client entity.</p>
 */
public record PlacementDefinition(
        double scale,
        double width,
        double height,
        @Nullable Double seat,
        int light,
        @Nullable String idleAnimation,
        @Nullable String interactAnimation,
        @Nullable Storage storage,
        @NotNull List<State> states
) {

    public PlacementDefinition(double scale, double width, double height,
                               @Nullable Double seat, int light,
                               @Nullable String idleAnimation,
                               @Nullable String interactAnimation) {
        this(scale, width, height, seat, light, idleAnimation, interactAnimation,
                null, List.of());
    }

    public PlacementDefinition {
        if (!Double.isFinite(scale) || scale <= 0 || scale > 16) {
            throw new IllegalArgumentException("place.scale must be within 0.01..16");
        }
        if (!Double.isFinite(width) || width <= 0 || width > 16
                || !Double.isFinite(height) || height <= 0 || height > 16) {
            throw new IllegalArgumentException("place width/height must be within 0.01..16");
        }
        if (seat != null && (!Double.isFinite(seat) || seat < 0 || seat > 16)) {
            throw new IllegalArgumentException("place.seat must be within 0..16");
        }
        if (light < 0 || light > 15) {
            throw new IllegalArgumentException("place.light must be within 0..15");
        }
        if (idleAnimation != null && idleAnimation.isBlank()) {
            idleAnimation = null;
        }
        if (interactAnimation != null && interactAnimation.isBlank()) {
            interactAnimation = null;
        }
        states = List.copyOf(states);
        for (State state : states) {
            if (state.scale() != null
                    && (!Double.isFinite(state.scale()) || state.scale() <= 0
                    || state.scale() > 16)) {
                throw new IllegalArgumentException("place.states[].scale must be within 0.01..16");
            }
            if (state.light() != null && (state.light() < 0 || state.light() > 15)) {
                throw new IllegalArgumentException("place.states[].light must be within 0..15");
            }
        }
    }

    public static @NotNull PlacementDefinition defaults() {
        return new PlacementDefinition(1.0, 1.0, 1.0, null, 0, null, null, null, List.of());
    }

    /** A container attached to the placed piece. */
    public record Storage(int rows, @Nullable String title) {
        public Storage {
            if (rows < 1 || rows > 6) {
                throw new IllegalArgumentException("place.storage.rows must be within 1..6");
            }
            if (title != null && title.length() > 64) {
                throw new IllegalArgumentException("place.storage.title is too long");
            }
        }
    }

    /**
     * One extra look reached by right-clicking. Null fields inherit the base placement.
     * A model key names another Kalo item whose Blockbench model is shown in this state;
     * the Bedrock compiler emits a separate client entity for it.
     */
    public record State(@Nullable Double scale, @Nullable Integer light,
                        @Nullable Boolean solid, @Nullable Key model,
                        @Nullable String sound) {
        public State {
            if (scale != null && (!Double.isFinite(scale) || scale <= 0 || scale > 16)) {
                throw new IllegalArgumentException("state scale must be within 0.01..16");
            }
            if (light != null && (light < 0 || light > 15)) {
                throw new IllegalArgumentException("state light must be within 0..15");
            }
            if (sound != null && sound.isBlank()) {
                sound = null;
            }
        }
    }
}
