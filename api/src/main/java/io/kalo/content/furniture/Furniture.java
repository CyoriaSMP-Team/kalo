package io.kalo.content.furniture;

import io.kalo.content.block.Block;
import io.kalo.content.furniture.definition.FurnitureDefinition;
import org.jetbrains.annotations.NotNull;

/**
 * A registered furniture piece.
 *
 * <p>Named {@code Furniture} to match {@link io.kalo.content.block.Block}; furniture
 * is a block with extra properties: rotation, seating, hitboxes, storage, and
 * jukebox support.</p>
 *
 * <p>Backward compatible: {@link #definition()} still returns the base
 * {@link io.kalo.content.block.definition.BlockDefinition} for code that only needs
 * block-level properties.</p>
 */
public interface Furniture extends Block {

    /**
     * The full furniture definition with rotation, seat, hitbox, and storage properties.
     *
     * <p>This is the preferred way to access furniture-specific properties. For
     * backward compatibility, {@link #definition()} returns the base block definition.</p>
     */
    @NotNull FurnitureDefinition furnitureDefinition();
}
