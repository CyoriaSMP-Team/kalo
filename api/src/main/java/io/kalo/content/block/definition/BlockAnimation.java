package io.kalo.content.block.definition;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Optional animation wiring for an animated block/furniture model.
 *
 * @param idle     loop played while nothing else is happening; null keeps every authored
 *                 loop
 * @param interact one-shot clip played on right-click, then the idle loop resumes
 */
public record BlockAnimation(@Nullable String idle, @Nullable String interact) {

    public BlockAnimation {
        if (idle != null && idle.isBlank()) {
            idle = null;
        }
        if (interact != null && interact.isBlank()) {
            interact = null;
        }
    }

    public static @NotNull BlockAnimation none() {
        return new BlockAnimation(null, null);
    }
}
