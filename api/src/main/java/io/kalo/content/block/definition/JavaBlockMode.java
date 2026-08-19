package io.kalo.content.block.definition;

/**
 * How Java represents a custom block to a vanilla client.
 *
 * <p>{@link #NATIVE} borrows a spare vanilla block state. {@link #VIRTUAL} stores an
 * invisible server-side anchor and renders the definition with an item display, so the
 * number of content keys is no longer bounded by the number of vanilla block states.</p>
 */
public enum JavaBlockMode {
    /** A real vanilla block state with a generated blockstates entry. */
    NATIVE,
    /** A persistent invisible anchor plus an {@code ItemDisplay}. */
    VIRTUAL
}
