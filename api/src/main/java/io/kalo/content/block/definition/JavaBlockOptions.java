package io.kalo.content.block.definition;

import org.jetbrains.annotations.NotNull;

/**
 * Java-platform escape hatch for blocks, read only by the Java compiler.
 *
 * @param mode how Java stores the block in the world
 * @param carrier the vanilla block whose spare states this block borrows in native mode
 */
public record JavaBlockOptions(@NotNull JavaBlockMode mode, @NotNull BlockCarrier carrier) {

    public JavaBlockOptions {
        if (mode == null) {
            throw new IllegalArgumentException("Java block mode cannot be null");
        }
        if (carrier == null) {
            throw new IllegalArgumentException("Java block carrier cannot be null");
        }
    }

    /** Backward-compatible native options constructor. */
    public JavaBlockOptions(@NotNull BlockCarrier carrier) {
        this(JavaBlockMode.NATIVE, carrier);
    }

    public static @NotNull JavaBlockOptions defaults() {
        return new JavaBlockOptions(JavaBlockMode.NATIVE, BlockCarrier.NOTE_BLOCK);
    }

    public static @NotNull JavaBlockOptions virtual() {
        return new JavaBlockOptions(JavaBlockMode.VIRTUAL, BlockCarrier.NOTE_BLOCK);
    }
}
