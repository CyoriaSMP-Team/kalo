package io.kalo.content.block.definition;

import org.jetbrains.annotations.NotNull;

/**
 * Java-platform escape hatch for blocks, read only by the Java compiler.
 *
 * @param carrier the vanilla block whose spare states this block borrows
 */
public record JavaBlockOptions(@NotNull BlockCarrier carrier) {

    public static @NotNull JavaBlockOptions defaults() {
        return new JavaBlockOptions(BlockCarrier.NOTE_BLOCK);
    }
}
