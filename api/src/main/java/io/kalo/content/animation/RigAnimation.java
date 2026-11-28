package io.kalo.content.animation;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Optional animation wiring for a rigged model.
 *
 * @param idle     loop played while idle; null keeps every authored loop
 * @param interact one-shot clip triggered by the content's interaction
 */
public record RigAnimation(@Nullable String idle, @Nullable String interact) {

    public RigAnimation {
        if (idle != null && idle.isBlank()) {
            idle = null;
        }
        if (interact != null && interact.isBlank()) {
            interact = null;
        }
    }

    public static @NotNull RigAnimation none() {
        return new RigAnimation(null, null);
    }
}
