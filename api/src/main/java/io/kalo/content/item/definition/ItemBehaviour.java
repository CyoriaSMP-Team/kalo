package io.kalo.content.item.definition;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Platform-neutral item mechanics.
 *
 * <p>Deliberately small. Every field here must have a meaningful answer on both Java and
 * Bedrock; anything only one platform can express belongs in that platform's options
 * record, not in this one.</p>
 *
 * @param maxStackSize  1..99
 * @param maxDurability {@code null} for an item that does not take damage
 */
public record ItemBehaviour(
        int maxStackSize,
        @Nullable Integer maxDurability,
        boolean fireResistant
) {
    public ItemBehaviour {
        if (maxStackSize < 1 || maxStackSize > 99) {
            throw new IllegalArgumentException("maxStackSize must be within 1..99, got " + maxStackSize);
        }
        if (maxDurability != null && maxDurability < 1) {
            throw new IllegalArgumentException("maxDurability must be positive, got " + maxDurability);
        }
        if (maxDurability != null && maxStackSize > 1) {
            throw new IllegalArgumentException("A damageable item cannot stack");
        }
    }

    public static @NotNull ItemBehaviour defaults() {
        return new ItemBehaviour(64, null, false);
    }
}
