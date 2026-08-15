package io.kalo.content.block.definition;

import org.jetbrains.annotations.NotNull;

/**
 * Platform-neutral block mechanics.
 *
 * @param hardness     how long the block takes to break; {@code -1} is unbreakable
 * @param requiresTool whether breaking without the correct tool yields no drop
 */
public record BlockBehaviour(
        float hardness,
        boolean requiresTool
) {
    public BlockBehaviour {
        if (hardness < 0 && hardness != -1f) {
            throw new IllegalArgumentException("hardness must be >= 0, or -1 for unbreakable; got " + hardness);
        }
    }

    public boolean unbreakable() {
        return hardness == -1f;
    }

    /** Matches vanilla stone: 1.5 hardness, pickaxe required for a drop. */
    public static @NotNull BlockBehaviour defaults() {
        return new BlockBehaviour(1.5f, true);
    }
}
