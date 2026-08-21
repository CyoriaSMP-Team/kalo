package io.kalo.platform.bedrock;

import io.kalo.content.block.definition.BlockCarrier;
import io.kalo.platform.java.BlockStateAllocator;
import org.jetbrains.annotations.NotNull;

/**
 * Names the Java block state behind a Kalo block in the form Geyser expects.
 *
 * <p>The integer Kalo persists is an index into one carrier's state product, and it means
 * nothing without the carrier it was allocated from. Geyser needs the complete Java state
 * identifier instead, both for the native override API and as the key of a
 * {@code custom_mappings} block entry.</p>
 *
 * <p>The state maths belongs to {@link BlockCarrier} and is not repeated here. An earlier
 * version of this class carried its own note-block-only copy, which silently produced a
 * {@code minecraft:note_block} identifier for blocks that had spilled onto the tripwire or
 * scaffolding carrier — every such block would have overridden the wrong Java state on
 * Bedrock.</p>
 */
public final class GeyserBlockState {

    private GeyserBlockState() {
    }

    /**
     * @param assignment the carrier and state index the allocator persisted
     * @return e.g. {@code minecraft:note_block[instrument=harp,note=0,powered=true]}
     */
    public static @NotNull String javaIdentifier(@NotNull BlockStateAllocator.Assignment assignment) {
        return javaIdentifier(assignment.carrier(), assignment.state());
    }

    /**
     * @throws IllegalArgumentException when the index is reserved or outside the carrier
     */
    public static @NotNull String javaIdentifier(@NotNull BlockCarrier carrier, int state) {
        if (state <= 0) {
            // State zero is the one every carrier keeps so an untouched vanilla block still
            // renders normally. Handing it to Bedrock would override real note blocks.
            throw new IllegalArgumentException(
                    "state 0 of " + carrier + " is reserved for vanilla, got " + state);
        }
        return carrier.vanillaBlock() + "[" + carrier.variantKey(state) + "]";
    }
}
