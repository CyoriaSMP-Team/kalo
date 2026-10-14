package io.kalo.content.feature.builtin;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * Validated, finite frame sequence for a custom-model-data animation.
 * Does not depend on a running server; this is the pure configuration layer.
 */
public record AnimationFrames(@NotNull List<Integer> frames, int delayTicks) {
    public AnimationFrames {
        frames = List.copyOf(frames);
        if (frames.isEmpty() || frames.size() > 64) {
            throw new IllegalArgumentException("Animation needs between 1 and 64 frames");
        }
        if (frames.stream().anyMatch(i -> i == null || i < 0)) {
            throw new IllegalArgumentException("Custom model data frames must be nonnegative");
        }
        if (delayTicks < 1 || delayTicks > 200) {
            throw new IllegalArgumentException("Frame delay must be 1..200 ticks");
        }
    }

    public static @NotNull AnimationFrames parse(@NotNull String raw, @NotNull String delay) {
        String input = raw.trim();
        if (input.startsWith("[") && input.endsWith("]")) {
            input = input.substring(1, input.length() - 1);
        }
        List<Integer> frames = new ArrayList<>();
        for (String element : input.split(",", -1)) {
            if (element.isBlank()) {
                throw new IllegalArgumentException("Empty animation frame");
            }
            frames.add(Integer.parseInt(element.trim()));
        }
        return new AnimationFrames(frames, Integer.parseInt(delay.trim()));
    }
}
